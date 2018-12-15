package app.dav.davandroidlibrary

import android.content.Context
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.data.DavDatabase
import app.dav.davandroidlibrary.models.Property
import app.dav.davandroidlibrary.models.PropertyEntity
import app.dav.davandroidlibrary.models.TableObject
import app.dav.davandroidlibrary.models.TableObjectUploadStatus
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


object Dav {
    private const val apiBaseUrlProduction = "https://dav-backend.herokuapp.com/v1/"
    private const val apiBaseUrlDevelopment = "https://e830cd.ngrok.io/v1/"

    const val databaseName = "dav.db"
    var database: DavDatabase? = null
    val dataPath: String
        get() = ProjectInterface.retrieveConstants?.getDataPath() ?: ""
    val apiBaseUrl: String
        get() = if(environment == DavEnvironment.Production) apiBaseUrlProduction else apiBaseUrlDevelopment
    val environment: DavEnvironment
        get() = ProjectInterface.generalMethods?.getEnvironment() ?: DavEnvironment.Development

    // Other constants
    const val getUserUrl = "auth/user"

    // Keys for Sharing Preferences
    const val jwtKey = "dav.jwt"
    const val emailKey = "dav.email"
    const val usernameKey = "dav.username"
    const val totalStorageKey = "dav.totalStorage"
    const val usedStorageKey = "dav.usedStorage"
    const val planKey = "dav.plan"
    const val avatarEtagKey = "dav.avatarEtag"

    fun init(context: Context){
        database = DavDatabase.getInstance(context)
    }

    object Database{
        fun createTableObject(tableObject: TableObject) : Deferred<Long> {
            return GlobalScope.async {
                database?.tableObjectDao()?.insertTableObject(TableObject.convertTableObjectToTableObjectEntity(tableObject)) ?: 0
            }
        }

        fun createTableObjectWithProperties(tableObject: TableObject) : Deferred<Long>{
            return GlobalScope.async {
                val tableObjectEntity = TableObject.convertTableObjectToTableObjectEntity(tableObject)
                val id = database?.tableObjectDao()?.insertTableObject(tableObjectEntity) ?: 0
                if(!id.equals(0)){
                    for(property in tableObject.properties){
                        property.tableObjectId = id
                        property.id = database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property)) ?: 0
                    }
                }
                id
            }
        }

        fun getTableObject(uuid: UUID) : Deferred<TableObject?>{
            return GlobalScope.async {
                val tableObjectEntity = database?.tableObjectDao()?.getTableObject(uuid.toString())
                if(tableObjectEntity != null){
                    val tableObject = TableObject.convertTableObjectEntityToTableObject(tableObjectEntity)
                    tableObject.load()
                    tableObject
                }else null
            }
        }

        fun getAllTableObjects(deleted: Boolean) : Deferred<ArrayList<TableObject>>{
            return GlobalScope.async {
                val db = database
                val tableObjectEntities = if(db != null) db.tableObjectDao().getTableObjects() else listOf()
                val tableObjects = ArrayList<TableObject>()

                for(obj in tableObjectEntities){
                    val tableObject = TableObject.convertTableObjectEntityToTableObject(obj)

                    if(!deleted && tableObject.uploadStatus == TableObjectUploadStatus.Deleted) continue

                    tableObject.load()
                    tableObjects.add(tableObject)
                }

                tableObjects
            }
        }

        fun getAllTableObjects(tableId: Int, deleted: Boolean) : Deferred<ArrayList<TableObject>>{
            return GlobalScope.async {
                val db = database
                val tableObjectEntities = if(db != null) db.tableObjectDao().getTableObjects() else listOf()
                val tableObjects = ArrayList<TableObject>()

                for (obj in tableObjectEntities){
                    val tableObject = TableObject.convertTableObjectEntityToTableObject(obj)

                    if((!deleted && tableObject.uploadStatus == TableObjectUploadStatus.Deleted) ||
                            tableObject.tableId != tableId) continue

                    tableObject.load()
                    tableObjects.add(tableObject)
                }

                tableObjects
            }
        }

        suspend fun updateTableObject(tableObject: TableObject){
            GlobalScope.async { database?.tableObjectDao()?.updateTableObject(TableObject.convertTableObjectToTableObjectEntity(tableObject)) }.await()
        }

        fun tableObjectExists(uuid: UUID) : Deferred<Boolean>{
            return GlobalScope.async {
                database?.tableObjectDao()?.getTableObject(uuid.toString()) != null
            }
        }

        suspend fun deleteTableObject(uuid: UUID){
            val tableObject = getTableObject(uuid).await() ?: return

            if(tableObject.uploadStatus == TableObjectUploadStatus.Deleted){
                deleteTableObjectImmediately(uuid)
            }else{
                tableObject.saveUploadStatus(TableObjectUploadStatus.Deleted)
            }
        }

        suspend fun deleteTableObjectImmediately(uuid: UUID){
            val tableObject = getTableObject(uuid).await() ?: return
            val tableObjectEntity = TableObject.convertTableObjectToTableObjectEntity(tableObject)
            GlobalScope.async { database?.tableObjectDao()?.deleteTableObject(tableObjectEntity) }.await()
        }

        suspend fun createProperty(property: Property){
            GlobalScope.async { database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property)) }.await()
        }

        fun getPropertiesOfTableObject(tableObjectId: Long) : Deferred<ArrayList<Property>>{
            return GlobalScope.async {
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

        suspend fun updateProperty(property: Property){
            GlobalScope.async { database?.propertyDao()?.updateProperty(Property.convertPropertyToPropertyEntity(property)) }.await()
        }

        fun propertyExists(id: Long) : Deferred<Boolean>{
            return GlobalScope.async {
                database?.propertyDao()?.getProperty(id) != null
            }
        }

        fun getTableFolder(tableId: Int) : File{
            val path = dataPath + tableId.toString()
            val file = File(path)
            if(!file.exists()){
                // Create the directory
                file.mkdir()
            }
            return file
        }
    }
}

enum class DavEnvironment(val environment: Int){
    Development(0),
    Test(1),
    Production(2)
}

class HttpResultEntry(override val key: Boolean, override val value: String) : Map.Entry<Boolean, String>