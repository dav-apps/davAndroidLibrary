package app.dav.davandroidlibrary.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.dav.davandroidlibrary.Constants
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.HttpResultEntry
import app.dav.davandroidlibrary.common.*
import app.dav.davandroidlibrary.models.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

@RunWith(AndroidJUnit4::class)
class DataManagerInstrumentedTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val database = DavDatabase.getInstance(context)

    init {
        Dav.init(context)

        // Drop the database
        database.tableObjectDao().deleteAllTableObjects()

        // Delete all files
        File(Constants.davDataPath).deleteRecursively()

        ProjectInterface.localDataSettings = LocalDataSettings()
        ProjectInterface.retrieveConstants = RetrieveConstants()
        ProjectInterface.triggerAction = TriggerAction()
        ProjectInterface.generalMethods = GeneralMethods()
    }

    // sync tests
    @Test
    fun syncShouldDownloadAllTableObjects(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.sync().await() }

        // Assert
        val firstTableObject = runBlocking { Dav.Database.getTableObject(Constants.testDataFirstTableObject.uuid) }

        Assert.assertNotNull(firstTableObject)
        Assert.assertEquals(Constants.testDataFirstTableObject.uuid, firstTableObject!!.uuid)
        Assert.assertEquals(Constants.testDataFirstTableObject.visibility, firstTableObject.visibility)
        Assert.assertFalse(firstTableObject.isFile)
        Assert.assertEquals(Constants.testDataFirstTableObject.properties.find { p -> p.name == Constants.testDataFirstPropertyName }?.value, firstTableObject.getPropertyValue(Constants.testDataFirstPropertyName))
        Assert.assertEquals(Constants.testDataFirstTableObject.properties.find { p -> p.name == Constants.testDataSecondPropertyName }?.value, firstTableObject.getPropertyValue(Constants.testDataSecondPropertyName))

        val secondTableObject = runBlocking { Dav.Database.getTableObject(Constants.testDataSecondTableObject.uuid) }
        Assert.assertNotNull(secondTableObject)
        Assert.assertEquals(Constants.testDataSecondTableObject.uuid, secondTableObject!!.uuid)
        Assert.assertEquals(Constants.testDataSecondTableObject.visibility, secondTableObject.visibility)
        Assert.assertFalse(secondTableObject.isFile)
        Assert.assertEquals(Constants.testDataSecondTableObject.properties.find { p -> p.name == Constants.testDataFirstPropertyName }?.value, secondTableObject.getPropertyValue(Constants.testDataFirstPropertyName))
        Assert.assertEquals(Constants.testDataSecondTableObject.properties.find { p -> p.name == Constants.testDataSecondPropertyName }?.value, secondTableObject.getPropertyValue(Constants.testDataSecondPropertyName))
    }

    @Test
    fun syncShouldDeleteTableObjectsThatDoNotExistOnTheServer(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val tableId = Constants.testDataTableId
        val uuid = UUID.randomUUID()
        val firstPropertyName = "text"
        val firstPropertyValue = "Lorem ipsum"
        val secondPropertyName = "test"
        val secondPropertyValue = "true"

        // Create a new table object in the database and set the upload status to UpToDate
        val properties = arrayListOf<Property>(
                Property(0, firstPropertyName, firstPropertyValue),
                Property(0, secondPropertyName, secondPropertyValue)
        )
        val tableObject = runBlocking { TableObject.create(uuid, tableId, properties) }
        runBlocking { tableObject.saveUploadStatus(TableObjectUploadStatus.UpToDate) }

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.sync().await() }

        // Assert
        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)

        val firstPropertyFromDatabase = database.propertyDao().getProperty(tableObject.properties[0].id)
        Assert.assertNull(firstPropertyFromDatabase)

        val secondPropertyFromDatabase = database.propertyDao().getProperty(tableObject.properties[1].id)
        Assert.assertNull(secondPropertyFromDatabase)

        val firstTableObjectFromServer = runBlocking { Dav.Database.getTableObject(Constants.testDataFirstTableObject.uuid) }
        Assert.assertNotNull(firstTableObjectFromServer)

        val secondTableObjectFromServer = runBlocking { Dav.Database.getTableObject(Constants.testDataSecondTableObject.uuid) }
        Assert.assertNotNull(secondTableObjectFromServer)
    }
    // End sync tests

    // syncPush tests
    @Test
    fun syncPushShouldUploadCreatedTableObjects(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val uuid = UUID.randomUUID()
        val tableId = Constants.testDataTableId
        val firstPropertyName = "text"
        val firstPropertyValue = "Lorem ipsum"
        val secondPropertyName = "test"
        val secondPropertyValue = "true"
        val properties = arrayListOf<Property>(
                Property(0, firstPropertyName, firstPropertyValue),
                Property(0, secondPropertyName, secondPropertyValue)
        )

        runBlocking { TableObject.create(uuid, tableId, properties) }
        var tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObjectFromDatabase)

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.syncPush().await() }
        DataManager.isSyncing = true

        // Assert
        // Get the created table object from the server
        val response = runBlocking { httpGet(Constants.jwt, "apps/object/$uuid").await() }
        Assert.assertTrue(response.key)
        val tableObjectFromServer = TableObjectData(response.value)
        tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }

        Assert.assertEquals(tableId, tableObjectFromDatabase!!.tableId)
        Assert.assertEquals(tableId, tableObjectFromServer.table_id)
        Assert.assertEquals(TableObjectUploadStatus.UpToDate, tableObjectFromDatabase.uploadStatus)

        // Etags should be equal
        Assert.assertEquals(tableObjectFromDatabase.etag, tableObjectFromServer.etag)

        // Both table objects should have the same properties
        Assert.assertEquals(firstPropertyValue, tableObjectFromServer.properties?.get(firstPropertyName))
        Assert.assertEquals(secondPropertyValue, tableObjectFromServer.properties?.get(secondPropertyName))

        Assert.assertEquals(firstPropertyName, tableObjectFromDatabase.properties[0].name)
        Assert.assertEquals(firstPropertyValue, tableObjectFromDatabase.properties[0].value)
        Assert.assertEquals(secondPropertyName, tableObjectFromDatabase.properties[1].name)
        Assert.assertEquals(secondPropertyValue, tableObjectFromDatabase.properties[1].value)

        // Delete the table object on the server
        val response2 = runBlocking { httpDelete(Constants.jwt, "apps/object/$uuid").await() }
        Assert.assertTrue(response2.key)
    }

    @Test
    fun syncPushShouldUploadUpdatedTableObjects(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        DataManager.isSyncing = false
        runBlocking { DataManager.sync().await() }
        DataManager.isSyncing = true
        val tableObject = runBlocking { Dav.Database.getTableObject(Constants.testDataFirstTableObject.uuid) }
        val property = tableObject!!.properties[0]
        val propertyName = property.name
        val oldPropertyValue = property.value
        val newPropertyValue = "newTestData"
        runBlocking {
            property.setPropertyValue(newPropertyValue)
            tableObject.saveUploadStatus(TableObjectUploadStatus.Updated)
        }

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.syncPush().await() }
        DataManager.isSyncing = true

        // Assert
        // Get the updated object from the server
        val response = runBlocking { httpGet(Constants.jwt, "apps/object/${tableObject.uuid}").await() }
        Assert.assertTrue(response.key)
        val tableObjectFromServer = TableObjectData(response.value)
        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(tableObject.uuid) }

        Assert.assertEquals(newPropertyValue, tableObjectFromServer.properties?.get(propertyName))
        Assert.assertEquals(newPropertyValue, tableObjectFromDatabase!!.properties[0].value)
        Assert.assertEquals(TableObjectUploadStatus.UpToDate, tableObjectFromDatabase.uploadStatus)

        // Revert changes
        runBlocking {
            tableObjectFromDatabase.setPropertyValue(propertyName, oldPropertyValue)
            DataManager.isSyncing = false
            DataManager.syncPush().await()
        }
        val tableObjectFromDatabase2 = runBlocking { Dav.Database.getTableObject(tableObject.uuid) }
        Assert.assertEquals(oldPropertyValue, tableObjectFromDatabase2!!.getPropertyValue(propertyName))
        Assert.assertEquals(TableObjectUploadStatus.UpToDate, tableObjectFromDatabase2.uploadStatus)
    }

    @Test
    fun syncPushShouldUploadDeletedTableObjects(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val uuid = UUID.randomUUID()
        val tableId = Constants.testDataTableId
        val properties = arrayListOf<Property>(
                Property(0, "page1", "bla"),
                Property(0, "page2", "blablabla")
        )
        // Create a new table object and upload it
        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, properties)
        }
        var tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObjectFromDatabase)

        DataManager.isSyncing = false
        runBlocking { DataManager.syncPush().await() }
        DataManager.isSyncing = true

        // Check if the table object was uploaded
        var response = runBlocking { httpGet(Constants.jwt, "apps/object/${tableObject.uuid}").await() }
        Assert.assertTrue(response.key)

        // Set the upload status of the table object to deleted
        runBlocking { tableObjectFromDatabase?.saveUploadStatus(TableObjectUploadStatus.Deleted) }

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.syncPush().await() }
        DataManager.isSyncing = true

        // Assert
        response = runBlocking { httpGet(Constants.jwt, "apps/object/${tableObject.uuid}").await() }
        Assert.assertFalse(response.key)
        Assert.assertTrue(response.value.contains("2805"))

        tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)
    }

    @Test
    fun syncPushShouldDeleteUpdatedTableObjectsThatDoNotExistOnTheServer(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val uuid = UUID.randomUUID()
        val tableId = Constants.testDataTableId
        val properties = arrayListOf<Property>(
                Property(0, "page1", "bla"),
                Property(0, "page2", "blablabla")
        )
        // Create a new table object
        runBlocking { TableObject.create(uuid, tableId, properties) }
        var tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObjectFromDatabase)

        // Set the uploadStatus of the table object to Updated
        runBlocking { tableObjectFromDatabase!!.saveUploadStatus(TableObjectUploadStatus.Updated) }

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.syncPush().await() }
        DataManager.isSyncing = true

        // Assert
        // The table object should not exist
        tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)
    }

    @Test
    fun syncPushShouldDeleteDeletedTableObjectsThatDoNotExistOnTheServer(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val uuid = UUID.randomUUID()
        val tableId = Constants.testDataTableId
        val properties = arrayListOf<Property>(
                Property(0, "page1", "bla"),
                Property(0, "page2", "blablabla")
        )
        // Create a new table object
        runBlocking { TableObject.create(uuid, tableId, properties) }
        var tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObjectFromDatabase)

        // Set the uploadStatus of the table object to Updated
        runBlocking { tableObjectFromDatabase!!.saveUploadStatus(TableObjectUploadStatus.Deleted) }

        // Act
        DataManager.isSyncing = false
        runBlocking { DataManager.syncPush().await() }
        DataManager.isSyncing = true

        // Assert
        // The table object should not exist
        tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)
    }
    // End syncPush tests

    // sortTableIds tests
    @Test
    fun sortTableIdsShouldReturnTheCorrectArrayWhenThereAreNoParallelTableIds(){
        /*
            Input:
                tableIds:           1, 2, 3, 4
                parallelTableIds:
                pages:              2, 2, 2, 2

            Output:
                [1, 1, 2, 2, 3, 3, 4, 4]
        */
        // Arrange
        val tableIds = arrayListOf<Int>(1, 2, 3, 4)
        val parallelTableIds = arrayListOf<Int>()
        val tableIdPages = HashMap<Int, Int>()
        tableIdPages[1] = 2
        tableIdPages[2] = 2
        tableIdPages[3] = 2
        tableIdPages[4] = 2

        // Act
        val sortedTableIds = DataManager.sortTableIds(tableIds, parallelTableIds, tableIdPages)

        // Assert
        Assert.assertArrayEquals(arrayListOf(1, 1, 2, 2, 3, 3, 4, 4).toArray(), sortedTableIds.toArray())
    }

    @Test
    fun sortTableIdsShouldReturnTheCorrectArrayWhenThereIsOneParallelTableId(){
        /*
            Input:
                tableIds:           1, 2, 3, 4
                parallelTableIds:      2
                pages:              2, 2, 2, 2

            Output:
                [1, 1, 2, 2, 3, 3, 4, 4]
        */
        // Arrange
        val tableIds = arrayListOf<Int>(1, 2, 3, 4)
        val parallelTableIds = arrayListOf<Int>(2)
        val tableIdPages = HashMap<Int, Int>()
        tableIdPages[1] = 2
        tableIdPages[2] = 2
        tableIdPages[3] = 2
        tableIdPages[4] = 2

        // Act
        val sortedTableIds = DataManager.sortTableIds(tableIds, parallelTableIds, tableIdPages)

        // Assert
        Assert.assertArrayEquals(arrayListOf(1, 1, 2, 2, 3, 3, 4, 4).toArray(), sortedTableIds.toArray())
    }

    @Test
    fun sortTableIdsShouldReturnTheCorrectArrayWhenTheParallelTableIdsAreSideBySide(){
        /*
            Input:
                tableIds:           1, 2, 3, 4
                parallelTableIds:      2, 3
                pages:              2, 2, 2, 2

            Output:
                [1, 1, 2, 3, 2, 3, 4, 4]
        */
        // Arrange
        val tableIds = arrayListOf<Int>(1, 2, 3, 4)
        val parallelTableIds = arrayListOf<Int>(2, 3)
        val tableIdPages = HashMap<Int, Int>()
        tableIdPages[1] = 2
        tableIdPages[2] = 2
        tableIdPages[3] = 2
        tableIdPages[4] = 2

        // Act
        val sortedTableIds = DataManager.sortTableIds(tableIds, parallelTableIds, tableIdPages)

        // Assert
        Assert.assertArrayEquals(arrayListOf(1, 1, 2, 3, 2, 3, 4, 4).toArray(), sortedTableIds.toArray())
    }

    @Test
    fun sortTableIdsShouldReturnTheCorrectArrayWhenTheParallelTableIdsAreNotSideBySide(){
        /*
            Input:
                tableIds:           1, 2, 3, 4
                parallelTableIds:   1,       4
                pages:              2, 2, 2, 2

            Output:
                [1, 2, 2, 3, 3, 4, 1, 4]
        */
        // Arrange
        val tableIds = arrayListOf<Int>(1, 2, 3, 4)
        val parallelTableIds = arrayListOf<Int>(1, 4)
        val tableIdPages = HashMap<Int, Int>()
        tableIdPages[1] = 2
        tableIdPages[2] = 2
        tableIdPages[3] = 2
        tableIdPages[4] = 2

        // Act
        val sortedTableIds = DataManager.sortTableIds(tableIds, parallelTableIds, tableIdPages)

        // Assert
        Assert.assertArrayEquals(arrayListOf(1, 2, 2, 3, 3, 4, 1, 4).toArray(), sortedTableIds.toArray())
    }

    @Test
    fun sortTableIdsShouldReturnTheCorrectArrayWhenThereAreDifferentPagesAndTheParallelTableIdsAreNotSideBySide(){
        /*
            Input:
                tableIds:           1, 2, 3, 4
                parallelTableIds:   1,       4
                pages:              3, 1, 2, 4

            Output:
                [1, 2, 3, 3, 4, 1, 4, 1, 4, 4]
        */
        // Arrange
        val tableIds = arrayListOf<Int>(1, 2, 3, 4)
        val parallelTableIds = arrayListOf<Int>(1, 4)
        val tableIdPages = HashMap<Int, Int>()
        tableIdPages[1] = 3
        tableIdPages[2] = 1
        tableIdPages[3] = 2
        tableIdPages[4] = 4

        // Act
        val sortedTableIds = DataManager.sortTableIds(tableIds, parallelTableIds, tableIdPages)

        // Assert
        Assert.assertArrayEquals(arrayListOf(1, 2, 3, 3, 4, 1, 4, 1, 4, 4).toArray(), sortedTableIds.toArray())
    }

    @Test
    fun sortTableIdsShouldReturnTheCorrectArrayWhenThereAreDifferentPagesAndTheParallelTableIdsAreSideBySide(){
        /*
            Input:
                tableIds:           1, 2, 3, 4
                parallelTableIds:   1, 2
                pages:              2, 4, 3, 2

            Output:
                [1, 2, 1, 2, 2, 2, 3, 3, 3, 4, 4]
        */
        // Arrange
        val tableIds = arrayListOf<Int>(1, 2, 3, 4)
        val parallelTableIds = arrayListOf<Int>(1, 2)
        val tableIdPages = HashMap<Int, Int>()
        tableIdPages[1] = 2
        tableIdPages[2] = 4
        tableIdPages[3] = 3
        tableIdPages[4] = 2

        // Act
        val sortedTableIds = DataManager.sortTableIds(tableIds, parallelTableIds, tableIdPages)

        // Assert
        Assert.assertArrayEquals(arrayListOf(1, 2, 1, 2, 2, 2, 3, 3, 3, 4, 4).toArray(), sortedTableIds.toArray())
    }
    // End sortTableIds tests

    // Helper functions
    fun httpGet(jwt: String, url: String) = GlobalScope.async {
        val client = OkHttpClient()
        val request = Request.Builder()
                .url(Dav.apiBaseUrl + url)
                .header("Authorization", jwt)
                .build()

        try {
            val response = client.newCall(request).execute()
            HttpResultEntry(response.isSuccessful, response.body()?.string() ?: "")
        }catch (e: IOException){
            HttpResultEntry(false, e.message ?: "There was an error")
        }
    }

    fun httpDelete(jwt: String, url: String) = GlobalScope.async {
        val client = OkHttpClient()
        val request = Request.Builder()
                .url(Dav.apiBaseUrl + url)
                .header("Authorization", jwt)
                .delete()
                .build()

        try {
            val response = client.newCall(request).execute()
            HttpResultEntry(response.isSuccessful, response.body()?.string() ?: "")
        }catch (e: IOException){
            HttpResultEntry(false, e.message ?: "There was an error")
        }
    }
}