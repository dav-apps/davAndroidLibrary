package app.dav.davandroidlibrary.data

import android.util.Log
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.HttpResultEntry
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.models.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

internal const val extPropertyName = "ext"
private const val downloadFilesSimultaneously = 2

class DataManager{
    companion object {
        private var isSyncing = false
        private var syncAgain = false
        private val fileDownloads = ArrayList<TableObject>()
        private val fileDownloaders = HashMap<UUID, OkHttpClient>()

        fun httpGet(jwt: String, url: String) : Deferred<HttpResultEntry> {
            return GlobalScope.async {
                val noInternetEntry = HttpResultEntry(false, "No internet connection")
                val isNetworkAvailable = ProjectInterface.generalMethods?.isNetworkAvailable() ?: return@async noInternetEntry
                if(!isNetworkAvailable) return@async noInternetEntry

                val client = OkHttpClient()
                val request = Request.Builder()
                        .url(Dav.apiBaseUrl + url)
                        .header("Authorization", jwt)
                        .build()

                try {
                    val response = client.newCall(request).execute()

                    if(response.isSuccessful){
                        HttpResultEntry(true, response.body()?.string() ?: "")
                    }else{
                        HttpResultEntry(false, "There was an error")
                    }
                }catch (e: IOException){
                    HttpResultEntry(false, e.message ?: "There was an error")
                }
            }
        }

        internal suspend fun Sync() : Deferred<Unit>{
            return GlobalScope.async {
                if(isSyncing) return@async

                isSyncing = true
                val jwt = DavUser.getJwtFromSettings()
                if(jwt.isEmpty()) return@async
                fileDownloads.clear()
                fileDownloaders.clear()

                // Get the specified tables
                val tableIds = ProjectInterface.retrieveConstants?.getTableIds() ?: return@async
                for(tableId in tableIds){
                    var tableGetResult: HttpResultEntry
                    var table: TableData
                    var pages = 1
                    var tableGetResultsOkay = true
                    var tableChanged = false

                    val removedTableObjectUuids: ArrayList<UUID> = ArrayList()
                    for(tableObject in Dav.Database.getAllTableObjects(tableId, true).await())
                        removedTableObjectUuids.add(tableObject.uuid)

                    for(i in 1 until (pages + 1)){
                        // Get the next page of the table
                        tableGetResult = httpGet(jwt, "apps/table/$tableId?page=$i").await()
                        if(!tableGetResult.key){
                            tableGetResultsOkay = false
                            continue
                        }

                        table = TableData(tableGetResult.value)
                        pages = table.pages
                        val tableObjects = table.table_objects ?: continue

                        // Get the objects of the table
                        for(obj in tableObjects){
                            removedTableObjectUuids.remove(obj.uuid)

                            // Is obj in the database?
                            val currentTableObject = Dav.Database.getTableObject(obj.uuid).await()
                            if(currentTableObject != null){
                                // Is the etag correct?
                                if(obj.etag == currentTableObject.etag){
                                    // Is it a file?
                                    if(currentTableObject.isFile){
                                        // Was the file downloaded?
                                        if(!currentTableObject.fileDownloaded()){
                                            // Download the file
                                            fileDownloads.add(currentTableObject)
                                        }
                                    }
                                }else{
                                    // GET the table object
                                    val tableObject = downloadTableObject(currentTableObject.uuid) ?: continue

                                    // Is it a file?
                                    if(tableObject.isFile){
                                        // Remove all properties except ext
                                        val removingProperties = ArrayList<Property>()
                                        for(p in tableObject.properties)
                                            if(p.name != extPropertyName) removingProperties.add(p)

                                        for(p in removingProperties)
                                            tableObject.properties.remove(p)

                                        // Save the ext property
                                        tableObject.saveWithProperties()

                                        // Download the file
                                        fileDownloads.add(tableObject)
                                    }else{
                                        // Save the table object
                                        tableObject.uploadStatus = TableObjectUploadStatus.UpToDate
                                        tableObject.saveWithProperties()
                                        ProjectInterface.triggerAction?.updateTableObject(tableObject, false)
                                        tableChanged = true
                                    }
                                }
                            }else{
                                // GET the table object
                                val tableObject = downloadTableObject(obj.uuid) ?: continue

                                if(tableObject.isFile){
                                    val etag = tableObject.etag

                                    // Remove all properties except ext
                                    val removingProperties = ArrayList<Property>()
                                    for(p in tableObject.properties)
                                        if(p.name != extPropertyName) removingProperties.add(p)

                                    for(p in removingProperties)
                                        tableObject.properties.remove(p)

                                    // Save the table object without properties and etag (the etag will be saved later when the file was downloaded)
                                    tableObject.etag = ""
                                    tableObject.saveWithProperties()
                                    tableObject.saveUploadStatus(TableObjectUploadStatus.UpToDate)

                                    // Download the file
                                    tableObject.etag = etag
                                    fileDownloads.add(tableObject)

                                    ProjectInterface.triggerAction?.updateTableObject(tableObject, false)
                                    tableChanged = true
                                }else{
                                    // Save the table object
                                    tableObject.uploadStatus = TableObjectUploadStatus.UpToDate
                                    tableObject.saveWithProperties()
                                    ProjectInterface.triggerAction?.updateTableObject(tableObject, false)
                                    tableChanged = true
                                }
                            }
                        }
                    }

                    if(!tableGetResultsOkay) continue

                    // RemovedTableObjects now includes all objects that were deleted on the server but not locally
                    // Delete those objects locally
                    for(objUuid in removedTableObjectUuids){
                        val obj = Dav.Database.getTableObject(objUuid).await() ?: continue

                        if(obj.uploadStatus == TableObjectUploadStatus.New && obj.isFile){
                            if(obj.fileDownloaded())
                                continue
                        }else if(obj.uploadStatus == TableObjectUploadStatus.New ||
                                obj.uploadStatus == TableObjectUploadStatus.NoUpload ||
                                obj.uploadStatus == TableObjectUploadStatus.Deleted){
                            continue
                        }

                        obj.deleteImmediately()
                        ProjectInterface.triggerAction?.deleteTableObject(obj)
                        tableChanged = true
                    }

                    if(tableChanged)
                        ProjectInterface.triggerAction?.updateAllOfTable(tableId)
                }
                isSyncing = false

                // Push changes
                SyncPush().await()
                downloadFiles()
            }
        }

        internal suspend fun SyncPush() : Deferred<Unit> {
            return GlobalScope.async {
                if(isSyncing){
                    syncAgain = true
                    return@async
                }

                val jwt = DavUser.getJwtFromSettings()
                if(jwt.isEmpty()) return@async

                isSyncing = true
                val tableObjects = Dav.Database.getAllTableObjects(true).await().filter {
                    it.uploadStatus != TableObjectUploadStatus.NoUpload &&
                            it.uploadStatus != TableObjectUploadStatus.UpToDate
                }.sortedBy { it.id }

                for(tableObject in tableObjects){
                    if(tableObject.uploadStatus == TableObjectUploadStatus.New){
                        // Check if the table object is a file and if it can be uploaded
                        if(tableObject.isFile && tableObject.fileDownloaded()){
                            val usedStorage = DavUser.getUsedStorageFromSettings()
                            val totalStorage = DavUser.getTotalStorageFromSettings()
                            val fileSize = tableObject.file?.length() ?: continue

                            if(usedStorage + fileSize > totalStorage && totalStorage != 0L)
                                continue
                        }

                        // Create the new object on the server
                        val etag = tableObject.createOnServer() ?: continue
                        if(etag.isEmpty()) continue

                        tableObject.etag = etag
                        tableObject.uploadStatus = TableObjectUploadStatus.UpToDate
                        tableObject.save()
                    }else if(tableObject.uploadStatus == TableObjectUploadStatus.Updated){
                        // Update the object on the server
                        val etag = tableObject.updateOnServer() ?: continue
                        if(etag.isEmpty()) continue

                        tableObject.etag = etag
                        tableObject.uploadStatus = TableObjectUploadStatus.UpToDate
                        tableObject.save()
                    }else if(tableObject.uploadStatus == TableObjectUploadStatus.Deleted){
                        // Delete the table object on the server
                        if(tableObject.deleteOnServer())
                            Dav.Database.deleteTableObject(tableObject.uuid)
                    }
                }

                isSyncing = false

                if(syncAgain){
                    syncAgain = false
                    SyncPush().await()
                }
            }
        }

        private suspend fun downloadTableObject(uuid: UUID) : TableObject?{
            val jwt = DavUser.getJwtFromSettings()
            if(jwt.isEmpty()) return null
            val getResult = httpGet(jwt, "apps/object/$uuid").await()
            if(getResult.key){
                val tableObjectData = TableObjectData(getResult.value)
                tableObjectData.id = 0
                return TableObject.convertTableObjectDataToTableObject(tableObjectData)
            }else{
                handleErrorCodes(getResult.value)
                return null
            }
        }

        private fun handleErrorCodes(errorMessage: String){
            if(errorMessage.contains("1301") || errorMessage.contains("1302") || errorMessage.contains("1303")){
                DavUser.setJwtInSettings("")
            }
        }

        private fun downloadFiles(){
            // Do not download more than downloadFilesSimultaneously files at the same time
            val timer = Timer("fileDownloads", false).schedule(5000) {
                downloadFilesTimerElapsed()
            }
        }

        private fun downloadFilesTimerElapsed(){
            Log.d("DataManager", "downloadFileTimerElapsed")
        }
    }
}