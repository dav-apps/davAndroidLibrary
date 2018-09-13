package app.dav.davandroidlibrary

import android.content.Context
import app.dav.davandroidlibrary.data.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import java.util.*
import kotlin.collections.ArrayList

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
                    for(property in tableObject.properties){
                        property.tableObjectId = id
                        property.id = database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property)) ?: 0
                    }
                }
            }
        }

        fun getTableObject(uuid: UUID) : Deferred<TableObject?>{
            return async {
                val tableObjectEntity = database?.tableObjectDao()?.getTableObject(uuid.toString())
                if(tableObjectEntity != null) TableObject.convertTableObjectEntityToTableObject(tableObjectEntity) else null
            }
        }

        fun getAllTableObjects(tableId: Int, deleted: Boolean) : Deferred<ArrayList<TableObject>>{
            return async {
                val db = database
                val tableObjectEntities = if(db != null) db.tableObjectDao().getNonObservableTableObjects() else listOf()
                val tableObjects = ArrayList<TableObject>()

                for (obj in tableObjectEntities){
                    val tableObject = TableObject.convertTableObjectEntityToTableObject(obj)
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

        fun tableObjectExists(uuid: UUID) : Deferred<Boolean>{
            return async {
                database?.tableObjectDao()?.getTableObject(uuid.toString()) != null
            }
        }

        fun createProperty(property: Property){
            launch {
                database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property))
            }
        }

        fun getPropertiesOfTableObject(tableObjectId: Long) : Deferred<ArrayList<Property>>{
            return async {
                val db = database
                val propertyEntries: List<PropertyEntity> = if(db != null) db.propertyDao().getPropertiesOfTableObject(tableObjectId) else listOf<PropertyEntity>()
                val properties = ArrayList<Property>()

                for(propertyEntry in propertyEntries){
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

        fun propertyExists(id: Long) : Deferred<Boolean>{
            return async {
                database?.propertyDao()?.getProperty(id) != null
            }
        }
    }
}