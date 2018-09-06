package app.dav.davandroidlibrary.data

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import app.dav.davandroidlibrary.Dav
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

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
    var etag: String = ""
    private val propertiesData = MutableLiveData<ArrayList<Property>>()
    val properties: LiveData<ArrayList<Property>>
        get() = propertiesData

    constructor(){
        propertiesData.value = ArrayList<Property>()
    }

    constructor(tableId: Int){
        propertiesData.value = ArrayList<Property>()
        this.tableId = tableId

        save()
    }

    constructor(uuid: UUID, tableId: Int){
        propertiesData.value = ArrayList<Property>()
        this.uuid = uuid
        this.tableId = tableId

        save()
    }

    constructor(uuid: UUID, tableId: Int, file: File){
        propertiesData.value = ArrayList<Property>()
        this.uuid = uuid
        this.tableId = tableId

        save()
    }

    constructor(uuid: UUID, tableId: Int, properties: ArrayList<Property>){
        propertiesData.value = ArrayList<Property>()
        this.uuid = uuid
        this.tableId = tableId
        for (p in properties) addProperty(p)

        saveWithProperties()
    }

    private fun save(){
        // Check if the table object already exists
        if(!Dav.Database.tableObjectExists(uuid)){
            uploadStatus = TableObjectUploadStatus.New
            Dav.Database.createTableObject(this)
        }else{
            Dav.Database.updateTableObject(this)
        }
    }

    private fun saveWithProperties(){
        // Check if the table object already exists
        if(!Dav.Database.tableObjectExists(uuid)){
            Dav.Database.createTableObjectWithProperties(this)
        }else{
            val tableObject = Dav.Database.getTableObject(uuid)

            if(tableObject != null){
                id = tableObject.id

                Dav.Database.updateTableObject(this)
                for(p in getProperties()) tableObject.setPropertyValue(p.name, p.value)
            }
        }

        // TODO SyncPush()
    }

    fun loadProperties(){
        Dav.Database.getPropertiesOfTableObject(id).observeForever {
            this.propertiesData.value = it
        }
    }

    fun getProperties() : ArrayList<Property>{
        return propertiesData.value ?: ArrayList<Property>()
    }

    private fun addProperty(property: Property){
        propertiesData.value?.add(property)
    }

    fun getPropertyValue(name: String) : String?{
        val property = getProperties().find { it.name == name }
        return if(property != null) property.value else null
    }

    fun setPropertyValue(name: String, value: String){
        val property = getProperties().find { it.name == name }

        if(property != null){
            if(property.value == value) return

            // Update the property
            property.setPropertyValue(value)
        }else{
            // Create a new property
            addProperty(Property(id, name, value))
        }

        if(uploadStatus == TableObjectUploadStatus.UpToDate && !isFile)
            uploadStatus = TableObjectUploadStatus.Updated

        save()
        // TODO SyncPush()
    }

    companion object {
        fun convertIntToVisibility(visibility: Int) : TableObjectVisibility{
            return when(visibility){
                2 -> TableObjectVisibility.Public
                1 -> TableObjectVisibility.Protected
                else -> TableObjectVisibility.Private
            }
        }

        fun convertIntToUploadStatus(uploadStatus: Int) : TableObjectUploadStatus{
            return when(uploadStatus){
                4 -> TableObjectUploadStatus.NoUpload
                3 -> TableObjectUploadStatus.Deleted
                2 -> TableObjectUploadStatus.Updated
                1 -> TableObjectUploadStatus.New
                else -> TableObjectUploadStatus.UpToDate
            }
        }

        fun convertTableObjectEntityToTableObject(obj: TableObjectEntity) : TableObject{
            val tableObject = TableObject()
            tableObject.id = obj.id ?: 0
            tableObject.tableId = obj.tableId
            tableObject.uuid = UUID.fromString(obj.uuid)
            tableObject.visibility = TableObject.convertIntToVisibility(obj.visibility)
            tableObject.uploadStatus = TableObject.convertIntToUploadStatus(obj.uploadStatus)
            tableObject.isFile = obj.isFile
            tableObject.etag = obj.etag
            return tableObject
        }

        fun convertTableObjectToTableObjectEntity(tableObject: TableObject) : TableObjectEntity{
            val tableObjectEntity = TableObjectEntity(tableObject.tableId,
                    tableObject.uuid.toString(),
                    tableObject.visibility.visibility,
                    tableObject.uploadStatus.uploadStatus,
                    tableObject.isFile,
                    tableObject.etag)
            tableObjectEntity.id = tableObject.id
            return tableObjectEntity
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