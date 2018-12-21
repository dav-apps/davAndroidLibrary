package app.dav.davandroidlibrary.models

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import android.util.Log
import android.webkit.MimeTypeMap
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.data.DataManager
import app.dav.davandroidlibrary.data.extPropertyName
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Entity(tableName = "TableObject")
data class TableObjectEntity(
        var tableId: Int,
        var uuid: String,
        var visibility: Int,
        var uploadStatus: Int,
        var isFile: Boolean,
        var etag: String
){
    @PrimaryKey var id: Long? = null
}

class TableObject{
    var id: Long = 0
    var tableId: Int = 0
    var uuid: UUID = UUID.randomUUID()
    var visibility: TableObjectVisibility = TableObjectVisibility.Private
    var uploadStatus: TableObjectUploadStatus = TableObjectUploadStatus.New
    var isFile: Boolean = false
    var file: File? = null
    var etag: String = ""
    var properties = ArrayList<Property>()
    val downloadStatus: TableObjectDownloadStatus
        get() = findDownloadStatus()

    constructor()

    constructor(tableId: Int){
        this.tableId = tableId
    }

    constructor(uuid: UUID, tableId: Int){
        this.uuid = uuid
        this.tableId = tableId
    }

    constructor(uuid: UUID, tableId: Int, properties: ArrayList<Property>){
        this.uuid = uuid
        this.tableId = tableId
        for (p in properties) this.properties.add(p)
    }

    internal suspend fun save(){
        // Check if the table object already exists
        if(!Dav.Database.tableObjectExistsAsync(uuid).await()){
            id = Dav.Database.createTableObjectAsync(this).await()
        }else{
            Dav.Database.updateTableObject(this)
        }
    }

    internal suspend fun saveWithProperties(){
        // Check if the table object already exists
        if(!Dav.Database.tableObjectExistsAsync(uuid).await()){
            id = Dav.Database.createTableObjectWithPropertiesAsync(this).await()
        }else{
            val tableObject = Dav.Database.getTableObjectAsync(uuid).await()

            if(tableObject != null){
                id = tableObject.id

                Dav.Database.updateTableObject(this)
                for(p in properties) tableObject.setPropertyValue(p.name, p.value)
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            DataManager.syncPush().await()
        }
    }

    suspend fun load(){
        loadProperties()
        loadFile()
    }

    private suspend fun loadProperties(){
        properties.clear()
        for (property in Dav.Database.getPropertiesOfTableObjectAsync(id).await()){
            properties.add(property)
        }
    }

    private fun loadFile(){
        if(!isFile) return

        val filePath: String = Dav.dataPath + tableId + "/" + uuid
        val file = File(filePath)

        if(file.exists()){
            this.file = file
        }
    }

    suspend fun setPropertyValue(name: String, value: String){
        val property = properties.find { it.name == name }

        if(property != null){
            if(property.value == value) return

            // Update the property
            property.setPropertyValue(value)
        }else{
            // Create a new property
            properties.add(Property.create(id, name, value))
        }

        if(uploadStatus == TableObjectUploadStatus.UpToDate && !isFile)
            uploadStatus = TableObjectUploadStatus.Updated

        save()
        GlobalScope.launch(Dispatchers.IO) { DataManager.syncPush().await() }
    }

    fun getPropertyValue(name: String) : String?{
        val property = properties.find { it.name == name }
        return if(property != null) property.value else null
    }

    suspend fun delete(){
        val jwt = DavUser.getJwtFromSettings()

        if(jwt.isEmpty()){
            deleteImmediately()
            uploadStatus = TableObjectUploadStatus.Deleted
        }else{
            val file = this.file
            if(file != null){
                if(isFile && file.exists()){
                    file.delete()
                }
            }

            saveUploadStatus(TableObjectUploadStatus.Deleted)
            GlobalScope.launch(Dispatchers.IO) { DataManager.syncPush().await() }
        }
    }

    suspend fun deleteImmediately(){
        if(isFile && file != null){
            file?.delete()
        }

        Dav.Database.deleteTableObjectImmediately(uuid)
    }

    internal suspend fun saveVisibility(visibility: TableObjectVisibility){
        if(this.visibility == visibility) return
        this.visibility = visibility
        save()
    }

    private fun findDownloadStatus() : TableObjectDownloadStatus{
        if(!isFile) return TableObjectDownloadStatus.NoFileOrNotLoggedIn

        if(file?.exists() == true)
            return TableObjectDownloadStatus.Downloaded

        val jwt = DavUser.getJwtFromSettings()
        if(jwt.isEmpty()) return TableObjectDownloadStatus.NoFileOrNotLoggedIn

        if(DataManager.fileDownloadProgress.containsKey(uuid)) return TableObjectDownloadStatus.Downloading
        return TableObjectDownloadStatus.NotDownloaded
    }

    suspend fun saveUploadStatus(uploadStatus: TableObjectUploadStatus){
        if(uploadStatus == this.uploadStatus) return

        this.uploadStatus = uploadStatus
        save()
    }

    suspend fun setFile(file: File){
        saveFile(file)
    }

    private suspend fun saveFile(file: File){
        if(uploadStatus == TableObjectUploadStatus.UpToDate) uploadStatus = TableObjectUploadStatus.Updated

        // Save the file in the data folder with the uuid as name (without extension)
        val filename: String = uuid.toString()
        val tableFolder = Dav.Database.getTableFolder(tableId)
        val newFile = File(tableFolder.path + "/" + filename)
        this.file = file.copyTo(newFile, true)

        if(!file.extension.isEmpty()){
            setPropertyValue("ext", file.extension)
        }

        save()
    }

    fun fileDownloaded() : Boolean{
        return file?.exists() ?: false
    }

    fun getDownloadFileProgress() : LiveData<Int>?{
        if(downloadStatus != TableObjectDownloadStatus.Downloading) return null
        return DataManager.fileDownloadProgress[uuid]
    }

    fun downloadFile(reportProgress: ((progress: Int) -> Unit)?) : Deferred<Unit> = GlobalScope.async {
        val jwt = DavUser.getJwtFromSettings()
        if(downloadStatus == TableObjectDownloadStatus.Downloading){
            // The file is already downloading, return the progress
            val progressLiveData = DataManager.fileDownloadProgress[uuid]
            if(progressLiveData == null){
                reportProgress?.invoke(-1)
            }else{
                var i = 0
                progressLiveData.observeForever {
                    if(it == null){
                        reportProgress?.invoke(-1)
                        return@observeForever
                    }else{
                        reportProgress?.invoke(it)
                        i = it
                    }
                }

                // Delay the thread until the download is finished
                while (i in 0..99){
                    delay(500)
                }

                // Try to get the file
                loadFile()
            }
            return@async
        }else if (downloadStatus != TableObjectDownloadStatus.NotDownloaded || jwt.isEmpty()){
            reportProgress?.invoke(-1)
            return@async
        }

        // Add the download progress to the list in DataManager
        DataManager.fileDownloadProgress.put(uuid, MutableLiveData())

        // Start the download
        val url = "${Dav.apiBaseUrl}apps/object/$uuid?file=true"

        // Remove the table object from the queue
        DataManager.fileDownloadQueue.remove(this@TableObject)

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                    .url(url)
                    .header("Authorization", jwt)
                    .build()

            val response = client.newCall(request).execute()
            if(response.isSuccessful){
                val byteStream: InputStream = response.body()?.byteStream() ?: return@async
                val tempFile = File.createTempFile(uuid.toString(), null)

                tempFile.copyInputStreamToFile(byteStream) {
                    // Report the progress to the SendChannel and update the progress in the DataManager list
                    GlobalScope.launch(Dispatchers.Main) {
                        DataManager.fileDownloadProgress[uuid]?.value = it
                    }
                    reportProgress?.invoke(it)
                }

                // Copy the temp file into the appropriate table folder
                val file = File(DataManager.getTableFolder(tableId), uuid.toString())
                tempFile.copyTo(file, true)
                this@TableObject.file = file

                // Notify subscribers that the download was finished
                GlobalScope.async(Dispatchers.Main) {
                    DataManager.fileDownloadProgress[uuid]?.value = 100
                    reportProgress?.invoke(100)

                    DataManager.fileDownloadProgress.remove(uuid)
                }.await()
            }else{
                reportProgress?.invoke(-1)
                Log.d("TableObject", "Error: ${response.body()?.string()}")
            }
        } catch (e: Exception) {
            reportProgress?.invoke(-1)
            Log.d("TableObject", "There was an error when downloading the file: ${e.message}")
        }finally {
            DataManager.fileDownloadProgress.remove(uuid)
        }
    }

    internal suspend fun createOnServer() : String?{
        if(ProjectInterface.generalMethods?.isNetworkAvailable() != true) return null
        val appId = ProjectInterface.retrieveConstants?.getAppId() ?: return null

        val jwt = DavUser.getJwtFromSettings()
        if(jwt.isEmpty()) return null

        var url = "apps/object?uuid=$uuid&app_id=$appId&table_id=$tableId"
        val client = OkHttpClient()
        val requestBuilder = Request.Builder()
                .header("Authorization", jwt)

        if(isFile){
            val f = file ?: return null

            // Set the Content-Type header
            val ext = getPropertyValue("ext") ?: return null
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: return null
            url += "&ext=$ext"

            // Add the file to the request
            val requestBody = RequestBody.create(MediaType.parse(mimeType), f)
            requestBuilder.post(requestBody)
        }else{
            // Set the properties
            val propertiesMap = HashMap<String, String>()
            for(property in properties){
                propertiesMap.put(property.name, property.value)
            }
            val json = Klaxon().toJsonString(propertiesMap)
            val requestBody = RequestBody.create(MediaType.parse("application/json"), json)
            requestBuilder.post(requestBody)
        }

        try{
            return GlobalScope.async {
                val response = client.newCall(requestBuilder
                        .url(Dav.apiBaseUrl + url)
                        .build())
                        .execute()
                val responseBody = response.body()?.string() ?: return@async null

                if(response.isSuccessful){
                    // Convert the result string to TableObjectData
                    val tableObjectData = TableObjectData(responseBody)
                    tableObjectData.etag
                }else{
                    // Check the error
                    if(responseBody.contains("2704")){    // Field already taken: uuid
                        saveUploadStatus(TableObjectUploadStatus.UpToDate)
                    }

                    null
                }
            }.await()
        }catch (e: IOException){
            Log.d("TableObject", "Error in createOnServer: ${e.message}")
            return null
        }
    }

    internal suspend fun updateOnServer() : String?{
        if(ProjectInterface.generalMethods?.isNetworkAvailable() != true) return null

        val jwt = DavUser.getJwtFromSettings()
        if(jwt.isEmpty()) return null

        var url = "apps/object/$uuid"
        val client = OkHttpClient()
        val requestBuilder = Request.Builder()
                .header("Authorization", jwt)

        if(isFile){
            val f = file ?: return null

            // Set the Content-Type header
            val ext = getPropertyValue(extPropertyName) ?: return null
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: return null
            url += "&ext=$ext"

            // Add the file to the request
            val requestBody = RequestBody.create(MediaType.parse(mimeType), f)
            requestBuilder.put(requestBody)
        }else{
            // Set the properties
            val propertiesMap = HashMap<String, String>()
            for(property in properties){
                propertiesMap.put(property.name, property.value)
            }
            val json = Klaxon().toJsonString(propertiesMap)
            val requestBody = RequestBody.create(MediaType.parse("application/json"), json)
            requestBuilder.put(requestBody)
        }

        try{
            return GlobalScope.async {
                val response = client.newCall(requestBuilder
                        .url(Dav.apiBaseUrl + url)
                        .build())
                        .execute()
                val responseBody = response.body()?.string() ?: return@async null

                if(response.isSuccessful){
                    // Convert the result string to TableObjectData
                    val tableObjectData = TableObjectData(responseBody)
                    tableObjectData.etag
                }else{
                    // Check the error
                    if(responseBody.contains("2805")){  // Resource does not exist: TableObject
                        // Delete the table object locally
                        deleteImmediately()
                    }

                    null
                }
            }.await()
        }catch (e: IOException){
            Log.d("TableObject", "Error in updateOnServer: ${e.message}")
            return null
        }
    }

    internal suspend fun deleteOnServer() : Boolean{
        if(ProjectInterface.generalMethods?.isNetworkAvailable() != true) return false

        val jwt = DavUser.getJwtFromSettings()
        if(jwt.isEmpty()) return false

        val url = "apps/object/$uuid"
        val client = OkHttpClient()
        val request = Request.Builder()
                .url(Dav.apiBaseUrl + url)
                .header("Authorization", jwt)
                .delete()
                .build()

        try {
            return GlobalScope.async {
                val response = client.newCall(request).execute()

                if(response.isSuccessful){
                    true
                }else{
                    // Check the error
                    val responseBody = response.body()?.string() ?: return@async false
                    return@async responseBody.contains("2805") || responseBody.contains("1102")
                }
            }.await()
        }catch (e: IOException){
            Log.d("TableObject", "Error in deleteOnServer: ${e.message}")
            return false
        }
    }

    private fun File.copyInputStreamToFile(inputStream: InputStream, reportProgress: (progress: Int) -> Unit) {
        // Return the progress as int between 0 and 100
        inputStream.use { input ->
            this.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }

        reportProgress(50)
    }

    companion object {
        suspend fun create(tableId: Int) : TableObject {
            val tableObject = TableObject(tableId)
            tableObject.save()
            return tableObject
        }

        suspend fun create(uuid: UUID, tableId: Int) : TableObject {
            val tableObject = TableObject(uuid, tableId)
            tableObject.save()
            return tableObject
        }

        suspend fun create(uuid: UUID, tableId: Int, file: File) : TableObject {
            val tableObject = TableObject(uuid, tableId)
            tableObject.isFile = true
            tableObject.save()
            tableObject.saveFile(file)
            return tableObject
        }

        suspend fun create(uuid: UUID, tableId: Int, properties: ArrayList<Property>) : TableObject {
            val tableObject = TableObject(uuid, tableId, properties)
            tableObject.saveWithProperties()
            return tableObject
        }

        fun convertIntToVisibility(visibility: Int) : TableObjectVisibility {
            return when(visibility){
                2 -> TableObjectVisibility.Public
                1 -> TableObjectVisibility.Protected
                else -> TableObjectVisibility.Private
            }
        }

        fun convertIntToUploadStatus(uploadStatus: Int) : TableObjectUploadStatus {
            return when(uploadStatus){
                4 -> TableObjectUploadStatus.NoUpload
                3 -> TableObjectUploadStatus.Deleted
                2 -> TableObjectUploadStatus.Updated
                1 -> TableObjectUploadStatus.New
                else -> TableObjectUploadStatus.UpToDate
            }
        }

        fun convertTableObjectEntityToTableObject(obj: TableObjectEntity) : TableObject {
            val tableObject = TableObject()
            tableObject.id = obj.id ?: 0
            tableObject.tableId = obj.tableId
            tableObject.uuid = UUID.fromString(obj.uuid)
            tableObject.visibility = convertIntToVisibility(obj.visibility)
            tableObject.uploadStatus = convertIntToUploadStatus(obj.uploadStatus)
            tableObject.isFile = obj.isFile
            tableObject.etag = obj.etag
            return tableObject
        }

        fun convertTableObjectToTableObjectEntity(tableObject: TableObject) : TableObjectEntity {
            val tableObjectEntity = TableObjectEntity(tableObject.tableId,
                    tableObject.uuid.toString(),
                    tableObject.visibility.visibility,
                    tableObject.uploadStatus.uploadStatus,
                    tableObject.isFile,
                    tableObject.etag)
            tableObjectEntity.id = if(tableObject.id == 0L) null else tableObject.id
            return tableObjectEntity
        }

        internal fun convertTableObjectDataToTableObject(tableObjectData: TableObjectData) : TableObject{
            val tableObject = TableObject()
            tableObject.id = tableObjectData.id ?: 0
            tableObject.tableId = tableObjectData.table_id ?: 0
            tableObject.visibility = convertIntToVisibility(tableObjectData.visibility ?: 0)
            tableObject.uuid = tableObjectData.uuid
            tableObject.isFile = tableObjectData.file ?: false
            tableObject.etag = tableObjectData.etag ?: ""

            val properties = ArrayList<Property>()

            if(tableObjectData.properties != null){
                for(key in tableObjectData.properties.keys){
                    val property = Property()
                    property.name = key
                    property.value = tableObjectData.properties[key] ?: ""
                    properties.add(property)
                }
            }

            tableObject.properties = properties
            return tableObject
        }
    }
}

enum class TableObjectVisibility(val visibility: Int){
    Private(0),
    Protected(1),
    Public(2)
}

enum class TableObjectUploadStatus(val uploadStatus: Int){
    UpToDate(0),
    New(1),
    Updated(2),
    Deleted(3),
    NoUpload(4)
}

enum class TableObjectDownloadStatus(val downloadStatus: Int){
    NoFileOrNotLoggedIn(0),
    NotDownloaded(1),
    Downloading(2),
    Downloaded(3)
}

internal class TableObjectData(json: String) : JSONObject(json){
    var id: Long? = this.optLong("id")
    val table_id: Int? = this.optInt("table_id")
    val visibility: Int? = this.optInt("visibility")
    val uuid: UUID = UUID.fromString(this.optString("uuid"))
    val file: Boolean? = this.optBoolean("file")
    val properties: HashMap<String, String>? = this.optJSONObject("properties")
            ?.let {
                val map = HashMap<String, String>()
                for(key in it.keys()){
                    map.put(key, it.getString(key))
                }
                map
            }
    val etag: String? = this.optString("etag")
}