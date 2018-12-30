package app.dav.davandroidlibrary.data

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import app.dav.davandroidlibrary.Constants
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.*
import app.dav.davandroidlibrary.models.Property
import app.dav.davandroidlibrary.models.TableObject
import app.dav.davandroidlibrary.models.TableObjectUploadStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.*

@RunWith(AndroidJUnit4::class)
class DataManagerInstrumentedTest {
    val context = InstrumentationRegistry.getTargetContext()
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
}