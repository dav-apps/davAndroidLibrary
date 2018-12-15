package app.dav.davandroidlibrary.data

import android.arch.lifecycle.MutableLiveData
import android.os.Handler
import android.os.Looper
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.HttpResultEntry
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.models.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

internal const val extPropertyName = "ext"
private const val downloadFilesSimultaneously = 2

class DataManager{
    companion object {
        private var isSyncing = false
        private var syncAgain = false
        // The list with the files that will be downloaded asap
        internal val fileDownloadQueue = ArrayList<TableObject>()
        // Contains the currently downloading files and the progress
        internal val fileDownloadProgress = HashMap<UUID, MutableLiveData<Int>>()
        private val downloadHandler = Handler(Looper.getMainLooper())
        private val downloadRunnable = Runnable { GlobalScope.launch { downloadFilesTimerElapsed() } }

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

        internal fun getTableFolder(tableId: Int) : File{
            val folder = File("${Dav.dataPath}$tableId")
            folder.mkdir()
            return folder
        }

        internal suspend fun sync() : Deferred<Unit>{
            return GlobalScope.async {
                if(isSyncing) return@async

                isSyncing = true
                val jwt = DavUser.getJwtFromSettings()
                if(jwt.isEmpty()) return@async
                fileDownloadQueue.clear()
                fileDownloadProgress.clear()

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
                                            fileDownloadQueue.add(currentTableObject)
                                        }
                                    }
                                }else{
                                    // GET the table object
                                    val tableObject = downloadTableObject(currentTableObject.uuid) ?: continue
                                    tableObject.uploadStatus = TableObjectUploadStatus.UpToDate

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
                                        fileDownloadQueue.add(tableObject)
                                    }else{
                                        // Save the table object
                                        tableObject.saveWithProperties()
                                        ProjectInterface.triggerAction?.updateTableObject(tableObject, false)
                                        tableChanged = true
                                    }
                                }
                            }else{
                                // GET the table object
                                val tableObject = downloadTableObject(obj.uuid) ?: continue
                                tableObject.uploadStatus = TableObjectUploadStatus.UpToDate

                                if(tableObject.isFile){
                                    // Remove all properties except ext
                                    val removingProperties = ArrayList<Property>()
                                    for(p in tableObject.properties)
                                        if(p.name != extPropertyName) removingProperties.add(p)

                                    for(p in removingProperties)
                                        tableObject.properties.remove(p)

                                    tableObject.saveWithProperties()

                                    // Download the file
                                    fileDownloadQueue.add(tableObject)

                                    ProjectInterface.triggerAction?.updateTableObject(tableObject, false)
                                    tableChanged = true
                                }else{
                                    // Save the table object
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
                syncPush().await()
                downloadFiles()
            }
        }

        internal suspend fun syncPush() : Deferred<Unit> {
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
                    syncPush().await()
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
            // Check if there is enough storage space
            val freeSpace = File(Dav.dataPath).freeSpace
            if(freeSpace < 2000000000) return

            // Trigger the runnable
            downloadHandler.postDelayed(downloadRunnable, 5000)
        }

        private suspend fun downloadFilesTimerElapsed(){
            // Check the network connection
            if(ProjectInterface.generalMethods?.isNetworkAvailable() != true) return
            
            // Check if there are files to download
            if(fileDownloadProgress.count() < downloadFilesSimultaneously && fileDownloadQueue.count() > 0 &&
                    fileDownloadQueue.first().downloadStatus == TableObjectDownloadStatus.NotDownloaded){
                fileDownloadQueue.first().downloadFile(null).await()
            }

            if(fileDownloadQueue.count() > 0)
                downloadHandler.postDelayed(downloadRunnable, 5000)
        }
    }
}