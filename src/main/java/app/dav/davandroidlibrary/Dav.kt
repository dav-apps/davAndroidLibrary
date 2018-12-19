package app.dav.davandroidlibrary

import android.content.Context
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.data.DavDatabase
import app.dav.davandroidlibrary.models.Property
import app.dav.davandroidlibrary.models.PropertyEntity
import app.dav.davandroidlibrary.models.TableObject
import app.dav.davandroidlibrary.models.TableObjectUploadStatus
import kotlinx.coroutines.*
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

    private val databaseJob = Job()
    private val databaseScope = CoroutineScope(Dispatchers.Default + databaseJob)

    fun init(context: Context){
        database = DavDatabase.getInstance(context)
    }

    object Database{
        private fun createTableObjectAsync(tableObject: TableObject, scope: CoroutineScope): Deferred<Long> = scope.async {
            database?.tableObjectDao()?.insertTableObject(TableObject.convertTableObjectToTableObjectEntity(tableObject)) ?: 0
        }

        fun createTableObjectAsync(tableObject: TableObject) : Deferred<Long> = createTableObjectAsync(tableObject, databaseScope)

        suspend fun createTableObject(tableObject: TableObject) = coroutineScope {
            createTableObjectAsync(tableObject, this).await()
        }

        private fun createTableObjectWithPropertiesAsync(tableObject: TableObject, scope: CoroutineScope) : Deferred<Long> = scope.async {
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

        fun createTableObjectWithPropertiesAsync(tableObject: TableObject) : Deferred<Long> = createTableObjectWithPropertiesAsync(tableObject, databaseScope)

        suspend fun createTableObjectWithProperties(tableObject: TableObject) : Long = coroutineScope {
            createTableObjectWithPropertiesAsync(tableObject, this).await()
        }

        private fun getTableObjectAsync(uuid: UUID, scope: CoroutineScope) : Deferred<TableObject?> = scope.async {
            val tableObjectEntity = database?.tableObjectDao()?.getTableObject(uuid.toString())
            if(tableObjectEntity != null){
                val tableObject = TableObject.convertTableObjectEntityToTableObject(tableObjectEntity)
                tableObject.load()
                tableObject
            }else null
        }

        fun getTableObjectAsync(uuid: UUID) : Deferred<TableObject?> = getTableObjectAsync(uuid, databaseScope)

        suspend fun getTableObject(uuid: UUID) : TableObject? = coroutineScope {
            getTableObjectAsync(uuid, this).await()
        }

        private fun getAllTableObjectsAsync(deleted: Boolean, scope: CoroutineScope) : Deferred<ArrayList<TableObject>> = scope.async {
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

        fun getAllTableObjectsAsync(deleted: Boolean) : Deferred<ArrayList<TableObject>> = getAllTableObjectsAsync(deleted, databaseScope)

        suspend fun getAllTableObjects(deleted: Boolean) : ArrayList<TableObject> = coroutineScope {
            getAllTableObjectsAsync(deleted, this).await()
        }

        private fun getAllTableObjectsAsync(tableId: Int, deleted: Boolean, scope: CoroutineScope) : Deferred<ArrayList<TableObject>> = scope.async {
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

        fun getAllTableObjectsAsync(tableId: Int, deleted: Boolean) : Deferred<ArrayList<TableObject>> = getAllTableObjectsAsync(tableId, deleted, databaseScope)

        suspend fun getAllTableObjects(tableId: Int, deleted: Boolean) : ArrayList<TableObject> = coroutineScope {
            getAllTableObjectsAsync(tableId, deleted, this).await()
        }

        private fun updateTableObjectAsync(tableObject: TableObject, scope: CoroutineScope) : Deferred<Unit?> = scope.async {
            database?.tableObjectDao()?.updateTableObject(TableObject.convertTableObjectToTableObjectEntity(tableObject))
        }

        fun updateTableObjectAsync(tableObject: TableObject) : Deferred<Unit?> = updateTableObjectAsync(tableObject, databaseScope)

        suspend fun updateTableObject(tableObject: TableObject) : Unit? = coroutineScope {
            updateTableObjectAsync(tableObject, this).await()
        }

        private fun tableObjectExistsAsync(uuid: UUID, scope: CoroutineScope) : Deferred<Boolean> = scope.async {
            database?.tableObjectDao()?.getTableObject(uuid.toString()) != null
        }

        fun tableObjectExistsAsync(uuid: UUID) : Deferred<Boolean> = tableObjectExistsAsync(uuid, databaseScope)

        suspend fun tableObjectExists(uuid: UUID) : Boolean = coroutineScope {
            tableObjectExistsAsync(uuid, this).await()
        }

        private fun deleteTableObjectAsync(uuid: UUID, scope: CoroutineScope) : Deferred<Unit> = scope.async {
            val tableObject = getTableObjectAsync(uuid).await() ?: return@async

            if(tableObject.uploadStatus == TableObjectUploadStatus.Deleted){
                deleteTableObjectImmediately(uuid)
            }else{
                tableObject.saveUploadStatus(TableObjectUploadStatus.Deleted)
            }
        }

        fun deleteTableObjectAsync(uuid: UUID) : Deferred<Unit> = deleteTableObjectAsync(uuid, databaseScope)

        suspend fun deleteTableObject(uuid: UUID) : Unit = coroutineScope {
            deleteTableObjectAsync(uuid, this).await()
        }

        private fun deleteTableObjectImmediatelyAsync(uuid: UUID, scope: CoroutineScope) : Deferred<Unit?> = scope.async {
            val tableObject = getTableObjectAsync(uuid).await() ?: return@async
            val tableObjectEntity = TableObject.convertTableObjectToTableObjectEntity(tableObject)
            database?.tableObjectDao()?.deleteTableObject(tableObjectEntity)
        }

        fun deleteTableObjectImmediatelyAsync(uuid: UUID) : Deferred<Unit?> = deleteTableObjectImmediatelyAsync(uuid, databaseScope)

        suspend fun deleteTableObjectImmediately(uuid: UUID) : Unit? = coroutineScope {
            deleteTableObjectAsync(uuid, this).await()
        }

        private fun createPropertyAsync(property: Property, scope: CoroutineScope) : Deferred<Long?> = scope.async {
            database?.propertyDao()?.insertProperty(Property.convertPropertyToPropertyEntity(property))
        }

        fun createPropertyAsync(property: Property) : Deferred<Long?> = createPropertyAsync(property, databaseScope)

        suspend fun createProperty(property: Property) : Long? = coroutineScope {
            createPropertyAsync(property, this).await()
        }

        private fun getPropertiesOfTableObjectAsync(tableObjectId: Long, scope: CoroutineScope) : Deferred<ArrayList<Property>> = scope.async {
            val db = database
            val propertyEntries: List<PropertyEntity> = if(db != null) db.propertyDao().getPropertiesOfTableObject(tableObjectId) else listOf<PropertyEntity>()
            val properties = ArrayList<Property>()

            for(propertyEntry in propertyEntries){
                val property = Property.convertPropertyEntityToProperty(propertyEntry)
                properties.add(property)
            }

            properties
        }

        fun getPropertiesOfTableObjectAsync(tableObjectId: Long) : Deferred<ArrayList<Property>> = getPropertiesOfTableObjectAsync(tableObjectId, databaseScope)

        suspend fun getPropertiesOfTableObject(tableObjectId: Long) : ArrayList<Property> = coroutineScope {
            getPropertiesOfTableObjectAsync(tableObjectId, this).await()
        }

        private fun updatePropertyAsync(property: Property, scope: CoroutineScope) : Deferred<Unit?> = scope.async {
            database?.propertyDao()?.updateProperty(Property.convertPropertyToPropertyEntity(property))
        }

        fun updatePropertyAsync(property: Property) : Deferred<Unit?> = updatePropertyAsync(property, databaseScope)

        suspend fun updateProperty(property: Property) : Unit? = coroutineScope {
            updatePropertyAsync(property, this).await()
        }

        private fun propertyExistsAsync(id: Long, scope: CoroutineScope) : Deferred<Boolean> = scope.async {
            database?.propertyDao()?.getProperty(id) != null
        }

        fun propertyExistsAsync(id: Long) : Deferred<Boolean> = propertyExistsAsync(id, databaseScope)

        suspend fun propertyExists(id: Long) : Boolean = coroutineScope {
            propertyExistsAsync(id, this).await()
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