package app.dav.davandroidlibrary

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import android.content.Context
import app.dav.davandroidlibrary.data.*
import kotlinx.coroutines.experimental.launch
import java.util.*

object Dav {
    const val apiBaseUrl = "https://dav-backend.herokuapp.com/v1/"
    const val DATABASE_NAME = "dav.db"
    var database: DavDatabase? = null

    fun init(context: Context){
        database = DavDatabase.getInstance(context)
    }

    object Database{
        fun createTableObject(tableObject: TableObject){
            launch {
                database?.tableObjectDao()?.insertTableObject(TableObject.convertTableObjectToTableObjectEntity(tableObject))
            }
        }

        fun createTableObjectWithProperties(tableObject: TableObject){
            launch {
                val tableObjectEntity = TableObjectEntity(tableObject.tableId, tableObject.uuid.toString(), 0, 0, tableObject.isFile, tableObject.etag)
                val id = database?.tableObjectDao()?.insertTableObject(tableObjectEntity) ?: 0
                if(!id.equals(0)){
                    for(property in tableObject.getProperties()){
                        property.tableObjectId = id
                        property.id = database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property)) ?: 0
                    }
                }
            }
        }

        fun getTableObject(uuid: UUID) : TableObject?{
            val tableObjectEntity = database?.tableObjectDao()?.getTableObject(uuid.toString())?.value
            return if(tableObjectEntity != null) TableObject.convertTableObjectEntityToTableObject(tableObjectEntity) else null
        }

        fun getAllTableObjects(tableId: Int, deleted: Boolean) : LiveData<ArrayList<TableObject>>{
            val db = database
            val tableObjectEntities = if(db != null) db.tableObjectDao().getTableObjects() else MutableLiveData<List<TableObjectEntity>>()

            return Transformations.map(tableObjectEntities) {
                val tableObjects = ArrayList<TableObject>()

                for (obj in it){
                    if (!deleted && obj.uploadStatus == TableObjectUploadStatus.Deleted.uploadStatus) continue;

                    val tableObject = TableObject.convertTableObjectEntityToTableObject(obj)

                    // Get the properties of the table object
                    tableObject.loadProperties()
                    tableObjects.add(tableObject)
                }

                tableObjects
            }
        }

        fun updateTableObject(tableObject: TableObject){
            launch {
                database?.tableObjectDao()?.updateTableObject(TableObject.convertTableObjectToTableObjectEntity(tableObject))
            }
        }

        fun tableObjectExists(uuid: UUID) : Boolean{
            return database?.tableObjectDao()?.getTableObject(uuid.toString())?.value != null
        }

        fun createProperty(property: Property){
            launch {
                database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property))
            }
        }

        fun getPropertiesOfTableObject(tableObjectId: Long) : LiveData<ArrayList<Property>>{
            val db = database
            val propertyEntries = if(db != null) db.propertyDao().getPropertiesOfTableObject(tableObjectId) else MutableLiveData<List<PropertyEntity>>()

            return Transformations.map(propertyEntries) {
                val properties = ArrayList<Property>()

                for(propertyEntry in it){
                    val property = Property.convertPropertyEntityToProperty(propertyEntry)
                    properties.add(property)
                }

                properties
            }
        }

        fun updateProperty(property: Property){
            launch {
                database?.propertyDao()?.updateProperty(Property.convertPropertyToPropertyEntity(property))
            }
        }

        fun propertyExists(id: Long) : Boolean{
            return database?.propertyDao()?.getProperty(id)?.value != null
        }
    }
}