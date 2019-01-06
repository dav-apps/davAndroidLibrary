package app.dav.davandroidlibrary.data

import android.arch.lifecycle.MutableLiveData
import android.os.Handler
import android.os.Looper
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.HttpResultEntry
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.models.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

internal const val extPropertyName = "ext"
private const val downloadFilesSimultaneously = 2

private val syncJob = Job()
private val syncScope = CoroutineScope(Dispatchers.Default + syncJob)

class DataManager{
    companion object {
        internal var isSyncing = false
        private var syncAgain = false
        // The list with the files that will be downloaded asap
        internal val fileDownloadQueue = ArrayList<TableObject>()
        // Contains the currently downloading files and the progress
        internal val fileDownloadProgress = HashMap<UUID, MutableLiveData<Int>>()
        private val downloadHandler = Handler(Looper.getMainLooper())
        private val downloadRunnable = Runnable { GlobalScope.launch { downloadFilesTimerElapsed() } }

        internal fun httpGet(jwt: String, url: String) : Deferred<HttpResultEntry>  = GlobalScope.async {
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

        internal fun getTableFolder(tableId: Int) : File{
            val folder = File("${Dav.dataPath}$tableId")
            folder.mkdir()
            return folder
        }

        internal suspend fun sync() : Deferred<Unit> = syncScope.async {
            if(isSyncing) return@async

            isSyncing = true
            val jwt = DavUser.getJwtFromSettings()
            if(jwt.isEmpty()) return@async
            fileDownloadQueue.clear()
            fileDownloadProgress.clear()

            // Holds the table ids, e.g. 1, 2, 3, 4
            val tableIds = ProjectInterface.retrieveConstants?.getTableIds() ?: return@async
            // Holds the parallel table ids, e.g. 2, 3
            val parallelTableIds = ProjectInterface.retrieveConstants?.getParallelTableIds() ?: return@async
            // Holds the order of the table ids, sorted by the pages and the parallel pages, e.g. 1, 2, 3, 2, 3, 4
            var sortedTableIds = arrayListOf<Int>()
            // Holds the pages of the table; in the format <tableId, pages>
            val tablePages = HashMap<Int, Int>()
            // Holds the last downloaded page; in the format <tableId, pages>
            val currentTablePages = HashMap<Int, Int>()
            // Holds the latest table result; in the format <tableId, tableData>
            val tableResults = HashMap<Int, TableData>()
            // Holds the uuids of the table objects that were removed on the server but not locally; in the format <tableId, ArrayList<UUID>>
            val removedTableObjectUuids = HashMap<Int, ArrayList<UUID>>()
            // Is true if all http calls of the specified table are successful; in the format <tableId, Boolean>
            val tableGetResultsOkay = HashMap<Int, Boolean>()

            // Populate removedTableObjectUuids
            for(tableId in tableIds){
                removedTableObjectUuids[tableId] = ArrayList()

                for(tableObject in Dav.Database.getAllTableObjectsAsync(tableId, true).await())
                    removedTableObjectUuids[tableId]!!.add(tableObject.uuid)
            }

            // Get the first page of each table and generate the sorted tableIds list
            for(tableId in tableIds){
                // Get the first page of the table
                val tableGetResult = httpGet(jwt, "apps/table/$tableId?page=1").await()

                tableGetResultsOkay[tableId] = tableGetResult.key
                if(!tableGetResult.key)
                    continue

                // Save the result
                tableResults[tableId] = TableData(tableGetResult.value)
                tablePages[tableId] = tableResults[tableId]!!.pages
                currentTablePages[tableId] = 1
            }

            sortedTableIds = sortTableIds(tableIds, parallelTableIds, tablePages)

            // Process the table results
            for(tableId in sortedTableIds){
                val tableObjects = tableResults[tableId]?.table_objects ?: continue
                var tableChanged = false

                if(tableGetResultsOkay[tableId] != true) continue

                // Get the objects of the table
                for(obj in tableObjects){
                    removedTableObjectUuids[tableId]?.remove(obj.uuid)

                    // Is obj in the database?
                    val currentTableObject = Dav.Database.getTableObjectAsync(obj.uuid).await()
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

                if(tableChanged)
                    ProjectInterface.triggerAction?.updateAllOfTable(tableId)

                // Check if there is a next page
                currentTablePages[tableId] = (currentTablePages[tableId] ?: 0) + 1
                if(currentTablePages[tableId] ?: 0 > tablePages[tableId] ?: 0){
                    continue
                }

                // Get the data of the next page
                val tableGetResult = httpGet(jwt, "apps/table/$tableId?page=${currentTablePages[tableId]}").await()
                if(!tableGetResult.key){
                    tableGetResultsOkay[tableId] = false
                    continue
                }

                tableResults[tableId] = TableData(tableGetResult.value)
            }

            // RemovedTableObjects now includes all objects that were deleted on the server but not locally
            // Delete those objects locally
            for(tableId in tableIds){
                if(tableGetResultsOkay[tableId] != true) continue
                val removedTableObjects = removedTableObjectUuids[tableId] ?: continue
                var tableChanged = false

                for(objUuid in removedTableObjects){
                    val obj = Dav.Database.getTableObjectAsync(objUuid).await() ?: continue

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

        internal suspend fun syncPush() : Deferred<Unit> = GlobalScope.async {
            if(isSyncing){
                syncAgain = true
                return@async
            }

            val jwt = DavUser.getJwtFromSettings()
            if(jwt.isEmpty()) return@async

            isSyncing = true
            val tableObjects = Dav.Database.getAllTableObjectsAsync(true).await().filter {
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

        internal fun sortTableIds(tableIds: ArrayList<Int>, parallelTableIds: ArrayList<Int>, tableIdPages: HashMap<Int, Int>) : ArrayList<Int>{
            val preparedTableIds = arrayListOf<Int>()

            // Remove all table ids in parallelTableIds that do not exist in tableIds
            val removeParallelTableIds = arrayListOf<Int>()
            for(i in 0 until parallelTableIds.size - 1){
                val value = parallelTableIds[i]
                if(!tableIds.contains(value)){
                    removeParallelTableIds.add(value)
                }
            }
            parallelTableIds.removeAll(removeParallelTableIds)

            // Prepare pagesOfParallelTable
            val pagesOfParallelTable = HashMap<Int, Int>()
            tableIdPages.forEach {
                if(parallelTableIds.contains(it.key))
                    pagesOfParallelTable[it.key] = it.value
            }

            // Count the pages
            var pagesSum = 0
            tableIdPages.forEach{
                pagesSum += it.value

                if(parallelTableIds.contains(it.key)){
                    pagesOfParallelTable[it.key] = it.value - 1
                }
            }

            var index = 0
            var currentTableIdIndex = 0
            var parallelTableIdsInserted = false

            while(index < pagesSum){
                val currentTableId = tableIds[currentTableIdIndex]
                val currentTablePages = tableIdPages[currentTableId]

                if(parallelTableIds.contains(currentTableId)){
                    // Add the table id once if it belongs to parallel table ids
                    preparedTableIds.add(currentTableId)
                    index++
                }else{
                    // Add it for all pages
                    for(j in 1 until (currentTablePages ?: 1)){
                        preparedTableIds.add(currentTableId)
                        index++
                    }
                }

                // Check if all parallel table ids are in prepared table ids
                if(preparedTableIds.containsAll(parallelTableIds) && !parallelTableIdsInserted){
                    parallelTableIdsInserted = true
                    var pagesOfParallelTableSum = 0

                    // Update pagesOfParallelTableSum
                    pagesOfParallelTable.forEach{
                        pagesOfParallelTableSum += it.value
                    }

                    // Add the parallel table ids in the right order
                    while(pagesOfParallelTableSum > 0){
                        for(parallelTableId in parallelTableIds){
                            if((pagesOfParallelTable[parallelTableId] ?: 0) > 0){
                                preparedTableIds.add(parallelTableId)
                                pagesOfParallelTableSum--

                                if(pagesOfParallelTable[parallelTableId] != null){
                                    pagesOfParallelTable[parallelTableId] = (pagesOfParallelTable[parallelTableId] ?: 0) - 1
                                }

                                index++
                            }
                        }
                    }
                }

                currentTableIdIndex++
            }

            return preparedTableIds
        }
    }
}