package app.dav.davandroidlibrary

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import app.dav.davandroidlibrary.data.DavDatabase
import app.dav.davandroidlibrary.models.Property
import app.dav.davandroidlibrary.models.TableObject
import app.dav.davandroidlibrary.models.TableObjectEntity
import app.dav.davandroidlibrary.models.TableObjectUploadStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class DavInstrumentedTest{
    val context = InstrumentationRegistry.getTargetContext()
    val database = DavDatabase.getInstance(context)

    init {
        Dav.init(context)

        // Drop the database
        database.tableObjectDao().deleteAllTableObjects()
    }

    // createTableObject tests
    @Test
    fun createTableObjectShouldSaveTheTableObjectInTheDatabaseAndReturnTheId(){
        // Arrange
        val uuid = UUID.randomUUID()
        val tableId = 4
        val tableObject = TableObject(uuid, tableId)

        // Act
        tableObject.id = runBlocking {
            Dav.Database.createTableObject(tableObject)
        }

        // Assert
        Assert.assertNotEquals(0, tableObject.id)

        // Get the table object from the database
        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(tableObject.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase.id)
        Assert.assertEquals(uuid, UUID.fromString(tableObjectFromDatabase.uuid))
        Assert.assertFalse(tableObjectFromDatabase.isFile)
    }
    // End createTableObject tests

    // CreateTableObjectWithProperties tests
    @Test
    fun createTableObjectWithPropertiesShouldSaveTheTableObjectAndItsPropertiesInTheDatabase(){
        // Arrange
        val uuid = UUID.randomUUID()
        val tableId = 4
        val firstPropertyName = "page1"
        val secondPropertyName = "page2"
        val firstPropertyValue = "Hello World"
        val secondPropertyValue = "Hallo Welt"
        val propertiesArray = arrayListOf<Property>(
                Property(0, firstPropertyName, firstPropertyValue),
                Property(0, secondPropertyName, secondPropertyValue))

        val tableObject = TableObject(uuid, tableId, propertiesArray)

        // Act
        tableObject.id = runBlocking {
            Dav.Database.createTableObjectWithProperties(tableObject)
        }

        // Assert
        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(tableObject.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase.id)
        Assert.assertEquals(uuid, UUID.fromString(tableObjectFromDatabase.uuid))

        val firstPropertyFromDatabase = database.propertyDao().getProperty(tableObject.properties[0].id)
        Assert.assertEquals(tableObjectFromDatabase.id, firstPropertyFromDatabase.tableObjectId)
        Assert.assertEquals(firstPropertyName, firstPropertyFromDatabase.name)
        Assert.assertEquals(firstPropertyValue, firstPropertyFromDatabase.value)

        val secondPropertyFromDatabase = database.propertyDao().getProperty(tableObject.properties[1].id)
        Assert.assertEquals(tableObjectFromDatabase.id, secondPropertyFromDatabase.tableObjectId)
        Assert.assertEquals(secondPropertyName, secondPropertyFromDatabase.name)
        Assert.assertEquals(secondPropertyValue, secondPropertyFromDatabase.value)
    }
    // End createTableObjectWithProperties tests

    // GetTableObject tests
    @Test
    fun getTableObjectShouldReturnTheTableObject(){
        // Arrange
        val uuid = UUID.randomUUID()
        val tableId = 4
        val tableObject = TableObject(uuid, tableId)
        tableObject.id = runBlocking {
            Dav.Database.createTableObject(tableObject)
        }

        // Act
        val tableObjectFromDatabase = runBlocking {
             Dav.Database.getTableObject(uuid)
        }

        // Assert
        Assert.assertNotNull(tableObjectFromDatabase)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase?.id)
        Assert.assertEquals(tableObject.tableId, tableObjectFromDatabase?.tableId)
        Assert.assertEquals(tableObject.uuid, tableObjectFromDatabase?.uuid)
        Assert.assertEquals(tableObject.uploadStatus, tableObjectFromDatabase?.uploadStatus)
    }

    @Test
    fun getTableObjectShouldReturnNullWhenTheTableObjectDoesNotExist(){
        // Arrange
        val uuid = UUID.randomUUID()

        // Act
        val tableObject = runBlocking {
            Dav.Database.getTableObject(uuid)
        }

        // Assert
        Assert.assertNull(tableObject)
    }
    // End getTableObject tests

    // getAllTableObjects(deleted: Boolean) tests
    @Test
    fun getAllTableObjectsShouldReturnAllTableObjects(){
        // Arrange
        val firstTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 12)
        }
        val secondTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 12)
        }
        val thirdTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 13)
        }
        runBlocking { thirdTableObject.saveUploadStatus(TableObjectUploadStatus.Deleted) }

        // Act
        val allTableObjects = runBlocking {
            Dav.Database.getAllTableObjects(true)
        }

        // Assert
        Assert.assertEquals(3, allTableObjects.size)

        Assert.assertEquals(firstTableObject.uuid, allTableObjects[0].uuid)
        Assert.assertEquals(firstTableObject.tableId, allTableObjects[0].tableId)

        Assert.assertEquals(secondTableObject.uuid, allTableObjects[1].uuid)
        Assert.assertEquals(secondTableObject.tableId, allTableObjects[1].tableId)

        Assert.assertEquals(thirdTableObject.uuid, allTableObjects[2].uuid)
        Assert.assertEquals(thirdTableObject.tableId, allTableObjects[2].tableId)
    }

    @Test
    fun getAllTableObjectsShouldReturnAllTableObjectsExceptDeletedOnes(){
        // Arrange
        val firstTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 12)
        }
        val secondTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 12)
        }
        val thirdTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 13)
        }
        runBlocking { thirdTableObject.saveUploadStatus(TableObjectUploadStatus.Deleted) }

        // Act
        val allTableObjects = runBlocking {
            Dav.Database.getAllTableObjects(false)
        }

        // Assert
        Assert.assertEquals(2, allTableObjects.size)

        Assert.assertEquals(firstTableObject.uuid, allTableObjects[0].uuid)
        Assert.assertEquals(firstTableObject.tableId, allTableObjects[0].tableId)

        Assert.assertEquals(secondTableObject.uuid, allTableObjects[1].uuid)
        Assert.assertEquals(secondTableObject.tableId, allTableObjects[1].tableId)
    }
    // End getAllTableObjects(deleted: Boolean) tests

    // getAllTableObjects(tableId: Int, deleted: Boolean) tests
    @Test
    fun getAllTableObjectsWithTableIdShouldReturnAllTableObjectsOfTheTable(){
        // Arrange
        val tableId = 12
        val firstTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), tableId)
        }
        val secondTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), tableId)
        }
        val thirdTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 3)
        }
        val fourthTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), tableId)
        }
        runBlocking { fourthTableObject.saveUploadStatus(TableObjectUploadStatus.Deleted) }

        // Act
        val allTableObjects = runBlocking {
            Dav.Database.getAllTableObjects(tableId, true)
        }

        // Assert
        Assert.assertEquals(3, allTableObjects.size)

        Assert.assertEquals(firstTableObject.uuid, allTableObjects[0].uuid)
        Assert.assertEquals(firstTableObject.tableId, allTableObjects[0].tableId)

        Assert.assertEquals(secondTableObject.uuid, allTableObjects[1].uuid)
        Assert.assertEquals(secondTableObject.tableId, allTableObjects[1].tableId)

        Assert.assertEquals(fourthTableObject.uuid, allTableObjects[2].uuid)
        Assert.assertEquals(fourthTableObject.tableId, allTableObjects[2].tableId)
    }

    @Test
    fun getAllTableObjectsWithTableIdShouldReturnAlltableObjectsOfTheTableExceptDeletedOnes(){
        // Arrange
        val tableId = 12
        val firstTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), tableId)
        }
        val secondTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), tableId)
        }
        val thirdTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), 3)
        }
        val fourthTableObject = runBlocking {
            TableObject.create(UUID.randomUUID(), tableId)
        }
        runBlocking { fourthTableObject.saveUploadStatus(TableObjectUploadStatus.Deleted) }

        // Act
        val allTableObjects = runBlocking {
            Dav.Database.getAllTableObjects(tableId, false)
        }

        // Assert
        Assert.assertEquals(2, allTableObjects.size)

        Assert.assertEquals(firstTableObject.uuid, allTableObjects[0].uuid)
        Assert.assertEquals(firstTableObject.tableId, allTableObjects[0].tableId)

        Assert.assertEquals(secondTableObject.uuid, allTableObjects[1].uuid)
        Assert.assertEquals(secondTableObject.tableId, allTableObjects[1].tableId)
    }
    // End getAllTableObjects(tableId: Int, deleted: Boolean) tests

    // updateTableObject tests
    @Test
    fun updateTableObjectShouldUpdateTheTableObjectInTheDatabase(){
        // Arrange
        val uuid = UUID.randomUUID()
        val tableId = 5
        val oldVisibilityInt = 1
        val newVisibilityInt = 2
        val oldUploadStatusInt = 0
        val newUploadStatusInt = 1
        val oldEtag = "oldetag"
        val newEtag = "newetag"

        val oldTableObjectEntity = TableObjectEntity(tableId, uuid.toString(), oldVisibilityInt, oldUploadStatusInt, false, oldEtag)
        oldTableObjectEntity.id = database.tableObjectDao().insertTableObject(oldTableObjectEntity)

        // Create a second table object with the same id and uuid but different values, and replace the old table object with this one
        val newTableObjectEntity = TableObjectEntity(tableId, uuid.toString(), newVisibilityInt, newUploadStatusInt, false, newEtag)
        newTableObjectEntity.id = oldTableObjectEntity.id
        val newTableObject = TableObject.convertTableObjectEntityToTableObject(newTableObjectEntity)

        // Act
        runBlocking { Dav.Database.updateTableObject(newTableObject) }

        // Assert
        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(newTableObject.id)
        Assert.assertEquals(newTableObject.id, tableObjectFromDatabase.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(newVisibilityInt, tableObjectFromDatabase.visibility)
        Assert.assertEquals(newUploadStatusInt, tableObjectFromDatabase.uploadStatus)
        Assert.assertEquals(newEtag, tableObjectFromDatabase.etag)
    }

    @Test
    fun updateTableObjectShouldNotThrowAnExceptionWhenTheTableObjectDoesNotExist(){
        // Arrange
        val tableObjectEntity = TableObjectEntity(-2, UUID.randomUUID().toString(), 0, 0, false, "")
        tableObjectEntity.id = -3
        val tableObject = TableObject.convertTableObjectEntityToTableObject(tableObjectEntity)

        // Act
        runBlocking { Dav.Database.updateTableObject(tableObject) }
    }
    // End updateTableObject tests
}