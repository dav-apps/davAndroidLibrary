package app.dav.davandroidlibrary.models

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import app.dav.davandroidlibrary.Constants
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.*
import app.dav.davandroidlibrary.data.DavDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.util.*

@RunWith(AndroidJUnit4::class)
class TableObjectInstrumentedTest {
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

    // setPropertyValue tests
    @Test
    fun setPropertyValueShouldCreateANewPropertyAndSaveItInTheDatabase(){
        // Arrange
        val tableId = 3
        val tableObject = runBlocking { TableObject.create(tableId) }
        val propertyName = "page1"
        val propertyValue = "Hello World"

        // Act
        runBlocking { tableObject.setPropertyValue(propertyName, propertyValue) }

        // Assert
        Assert.assertEquals(1, tableObject.properties.size)
        Assert.assertEquals(propertyName, tableObject.properties[0].name)
        Assert.assertEquals(propertyValue, tableObject.properties[0].value)

        val propertyFromDatabase = database.propertyDao().getProperty(tableObject.properties[0].id)
        Assert.assertEquals(tableObject.id, propertyFromDatabase.tableObjectId)
        Assert.assertEquals(propertyName, propertyFromDatabase.name)
        Assert.assertEquals(propertyValue, propertyFromDatabase.value)

        val tableObject2 = runBlocking { Dav.Database.getTableObject(tableObject.uuid) }
        Assert.assertNotNull(tableObject2)
        Assert.assertEquals(1, tableObject2!!.properties.size)
        Assert.assertEquals(propertyName, tableObject2.properties[0].name)
        Assert.assertEquals(propertyValue, tableObject2.properties[0].value)
    }

    @Test
    fun setPropertyValueShouldUpdateAnExistingPropertyAndSaveItInTheDatabase(){
        // Arrange
        val tableId = 3
        val uuid = UUID.randomUUID()
        val propertyName = "test"
        val oldPropertyValue = "Hello World"
        val newPropertyValue = "Hallo Welt"
        val properties = arrayListOf<Property>(Property(0, propertyName, oldPropertyValue))
        val tableObject = runBlocking { TableObject.create(uuid, tableId, properties) }

        Assert.assertEquals(1, tableObject.properties.size)
        Assert.assertEquals(propertyName, tableObject.properties[0].name)
        Assert.assertEquals(oldPropertyValue, tableObject.properties[0].value)

        // Act
        runBlocking { tableObject.setPropertyValue(propertyName, newPropertyValue) }

        // Assert
        Assert.assertEquals(1, tableObject.properties.size)
        Assert.assertEquals(propertyName, tableObject.properties[0].name)
        Assert.assertEquals(newPropertyValue, tableObject.properties[0].value)

        val tableObject2 = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObject2)
        Assert.assertEquals(1, tableObject2!!.properties.size)
        Assert.assertEquals(propertyName, tableObject2.properties[0].name)
        Assert.assertEquals(newPropertyValue, tableObject2.properties[0].value)
    }
    // End setPropertyValue tests

    // getPropertyValue tests
    @Test
    fun getPropertyValueShouldReturnTheValueOfTheProperty(){
        // Arrange
        val uuid = UUID.randomUUID()
        val tableId = 7
        val propertyName = "test"
        val propertyValue = "files"
        val properties = arrayListOf<Property>(Property(0, propertyName, propertyValue))
        val tableObject = runBlocking { TableObject.create(uuid, tableId, properties) }

        Assert.assertEquals(1, tableObject.properties.size)
        Assert.assertEquals(propertyName, tableObject.properties[0].name)
        Assert.assertEquals(propertyValue, tableObject.properties[0].value)

        // Act
        val tableObjectPropertyValue = tableObject.getPropertyValue(propertyName)

        // Assert
        Assert.assertEquals(propertyValue, tableObjectPropertyValue)
    }

    @Test
    fun getPropertyValueShouldReturnNullIfThePropertyDoesNotExist(){
        // Arrange
        val tableObject = runBlocking { TableObject.create(1) }

        // Act
        val propertyValue = tableObject.getPropertyValue("files")

        // Assert
        Assert.assertNull(propertyValue)
    }
    // End getPropertyValue tests

    // delete tests
    @Test
    fun deleteShouldSetTheUploadStatusOfTheTableObjectToDeletedWhenTheUserIsLoggedIn(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val tableId = 2
        val uuid = UUID.randomUUID()
        val properties = arrayListOf<Property>(
                Property(0, "test1", "blabla"),
                Property(0, "test2", "blablabla"))
        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, properties)
        }

        // Act
        runBlocking { tableObject.delete() }

        // Assert
        val tableObject2 = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObject2)
        Assert.assertEquals(TableObjectUploadStatus.Deleted, tableObject2!!.uploadStatus)
    }

    @Test
    fun deleteShouldDeleteTheFileOfATableObjectAndSetTheUploadStatusToDeletedWhenTheUserIsLoggedIn(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)
        val tableId = 2
        val uuid = UUID.randomUUID()

        // Copy the file into the cache
        val assetManager = InstrumentationRegistry.getContext().assets
        val inputStream = assetManager.open("files/test/icon.ico")
        val file = File.createTempFile("icon", ".ico")
        file.copyInputStreamToFile(inputStream)
        inputStream.close()

        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, file)
        }
        Assert.assertTrue(tableObject.file?.exists() ?: false)

        // Act
        runBlocking { tableObject.delete() }

        // Assert
        Assert.assertFalse(tableObject.file?.exists() ?: true)
        Assert.assertEquals(TableObjectUploadStatus.Deleted, tableObject.uploadStatus)

        val tableObject2 = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObject2)
        Assert.assertEquals(TableObjectUploadStatus.Deleted, tableObject2?.uploadStatus)
    }

    @Test
    fun deleteShouldDeleteTheTableObjectImmediatelyWhenTheUserIsNotLoggedIn(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, "")
        val tableId = 3
        val uuid = UUID.randomUUID()
        val properties = arrayListOf<Property>(
                Property(0, "page1", "Hello World"),
                Property(0, "page2", "Hallo Welt"))
        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, properties)
        }

        val firstPropertyId = tableObject.properties[0].id
        val secondPropertyId = tableObject.properties[1].id

        // Act
        runBlocking { tableObject.delete() }

        // Assert
        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)

        val firstPropertyFromDatabase = database.propertyDao().getProperty(firstPropertyId)
        Assert.assertNull(firstPropertyFromDatabase)

        val secondPropertyFromDatabase = database.propertyDao().getProperty(secondPropertyId)
        Assert.assertNull(secondPropertyFromDatabase)
    }

    @Test
    fun deleteShouldDeleteTheFileOfTheTableObjectAndDeleteTheTableObjectImmediatelyWhenTheUserIsNotLoggedIn(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, "")
        val tableId = 3
        val uuid = UUID.randomUUID()

        // Copy the file into the cache
        val assetManager = InstrumentationRegistry.getContext().assets
        val inputStream = assetManager.open("files/test/icon.ico")
        val file = File.createTempFile("icon", ".ico")
        file.copyInputStreamToFile(inputStream)
        inputStream.close()

        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, file)
        }
        Assert.assertTrue(tableObject.file?.exists() ?: false)

        // Act
        runBlocking { tableObject.delete() }

        // Assert
        Assert.assertFalse(tableObject.file?.exists() ?: false)
        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)
    }
    // End delete tests

    // deleteImmediately tests
    @Test
    fun deleteImmediatelyShouldDeleteTheTableObjectImmediately(){
        // Arrange
        val tableId = 5
        val uuid = UUID.randomUUID()
        val properties = arrayListOf<Property>(
                Property(0, "page1", "Hello World"),
                Property(0, "page2", "Hallo Welt"))
        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, properties)
        }

        val firstPropertyId = tableObject.properties[0].id
        val secondPropertyId = tableObject.properties[1].id

        // Act
        runBlocking { tableObject.deleteImmediately() }

        // Assert
        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)

        val firstPropertyFromDatabase = database.propertyDao().getProperty(firstPropertyId)
        Assert.assertNull(firstPropertyFromDatabase)

        val secondPropertyFromDatabase = database.propertyDao().getProperty(secondPropertyId)
        Assert.assertNull(secondPropertyFromDatabase)
    }

    @Test
    fun deleteImmediatelyShouldDeleteTheTableObjectAndItsFile(){
        // Arrange
        val tableId = 6
        val uuid = UUID.randomUUID()

        // Copy the file into the cache
        val assetManager = InstrumentationRegistry.getContext().assets
        val inputStream = assetManager.open("files/test/icon.ico")
        val file = File.createTempFile("icon", ".ico")
        file.copyInputStreamToFile(inputStream)
        inputStream.close()

        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, file)
        }
        Assert.assertTrue(tableObject.file?.exists() ?: false)

        // Act
        runBlocking { tableObject.deleteImmediately() }

        // Assert
        Assert.assertFalse(tableObject.file?.exists() ?: false)
        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNull(tableObjectFromDatabase)
    }
    // End deleteImmediately tests

    // saveVisibility tests
    @Test
    fun saveVisibilityShouldSetTheVisibilityOfTheTableObjectAndSaveItInTheDatabase(){
        // Arrange
        val tableId = 7
        val tableObject = runBlocking { TableObject.create(tableId) }
        val oldVisibility = tableObject.visibility
        val newVisibility = TableObjectVisibility.Public

        // Act
        runBlocking { tableObject.saveVisibility(newVisibility) }

        // Assert
        Assert.assertEquals(newVisibility, tableObject.visibility)
        Assert.assertNotEquals(oldVisibility, tableObject.visibility)

        val tableObject2 = runBlocking { Dav.Database.getTableObject(tableObject.uuid) }
        Assert.assertNotNull(tableObject2)
        Assert.assertEquals(newVisibility, tableObject2!!.visibility)
    }
    // End saveVisibility tests

    // toTableObjectEntity tests
    @Test
    fun toTableObjectEntityShouldReturnValidTableObjectEntityObject(){
        // Arrange
        val uuid = UUID.randomUUID()
        val tableId = 54
        val properties = arrayListOf<Property>(
                Property(0, "page1", "Hello World"),
                Property(0, "page2", "Hallo Welt")
        )
        val tableObject = runBlocking { TableObject.create(UUID.randomUUID(), 12, properties) }
        runBlocking {
            tableObject.saveVisibility(TableObjectVisibility.Public)
            tableObject.uploadStatus = TableObjectUploadStatus.Updated
        }

        // Act
        val tableObjectEntity = tableObject.toTableObjectEntity()

        // Assert
        Assert.assertEquals(tableObject.id, tableObjectEntity.id)
        Assert.assertEquals(tableObject.tableId,  tableObjectEntity.tableId)
        Assert.assertEquals(tableObject.uuid, UUID.fromString(tableObjectEntity.uuid))
        Assert.assertEquals(tableObject.visibility.visibility, tableObjectEntity.visibility)
        Assert.assertEquals(tableObject.uploadStatus.uploadStatus, tableObjectEntity.uploadStatus)
    }
    // End toTableObjectEntity tests

    // create(tableId: Int) tests
    @Test
    fun createWithTableIdShouldCreateNewTableObject(){
        // Arrange
        val tableId = 5

        // Act
        val tableObject = runBlocking { TableObject.create(tableId) }

        // Assert
        Assert.assertNotEquals(tableId, tableObject.id)

        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(tableObject.id)
        Assert.assertNotNull(tableObjectFromDatabase)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(tableObject.uuid, UUID.fromString(tableObjectFromDatabase.uuid))
    }
    // End create(tableId: Int) tests

    // create(uuid: UUID, tableId: Int) tests
    @Test
    fun createWithUuidAndTableIdShouldCreateNewTableObject(){
        // Arrange
        val tableId = 8
        val uuid = UUID.randomUUID()

        // Act
        val tableObject = runBlocking { TableObject.create(uuid, tableId) }

        // Arrange
        Assert.assertEquals(tableId, tableObject.tableId)
        Assert.assertEquals(uuid, tableObject.uuid)

        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(tableObject.id)
        Assert.assertNotNull(tableObjectFromDatabase)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(uuid, UUID.fromString(tableObjectFromDatabase.uuid))
    }
    // End create(uuid: UUID, tableId: Int) tests

    // create(uuid: UUID, tableId: Int, file: File) tests
    @Test
    fun createWithUuidTableIdAndFileShouldCreateNewTableObject(){
        // Arrange
        val tableId = 9
        val uuid = UUID.randomUUID()
        val fileExt = ".ico"

        // Copy the file into the cache
        val assetManager = InstrumentationRegistry.getContext().assets
        val inputStream = assetManager.open("files/test/icon.ico")
        val file = File.createTempFile("icon", fileExt)
        file.copyInputStreamToFile(inputStream)
        inputStream.close()

        // Act
        val tableObject = runBlocking {
            TableObject.create(uuid, tableId, file)
        }

        // Assert
        Assert.assertEquals(tableId, tableObject.tableId)
        Assert.assertEquals(uuid, tableObject.uuid)
        Assert.assertTrue(tableObject.isFile)
        Assert.assertTrue(tableObject.file?.exists() ?: false)

        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObjectFromDatabase)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase!!.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(uuid, tableObjectFromDatabase.uuid)
        Assert.assertTrue(tableObjectFromDatabase.isFile)
        Assert.assertTrue(tableObjectFromDatabase.file?.exists() ?: false)
        Assert.assertEquals(tableObject.getPropertyValue("ext"), fileExt.replace(".", ""))
    }
    // End create(uuid: UUID, tableId: Int, file: File) tests

    // create(uuid: UUID, tableId: Int, properties: ArrayList<Property>) tests
    @Test
    fun createWithUuidTableIdAndPropertiesShouldCreateNewTableObject(){
        // Arrange
        val tableId = 7
        val uuid = UUID.randomUUID()
        val properties = arrayListOf<Property>(
                Property(0, "page1", "Hello World"),
                Property(0, "page2", "Hallo Welt")
        )

        // Act
        val tableObject = runBlocking { TableObject.create(uuid, tableId, properties) }

        // Assert
        Assert.assertEquals(tableId, tableObject.tableId)
        Assert.assertEquals(uuid, tableObject.uuid)
        Assert.assertEquals(properties, tableObject.properties)

        val tableObjectFromDatabase = runBlocking { Dav.Database.getTableObject(uuid) }
        Assert.assertNotNull(tableObjectFromDatabase)
        Assert.assertEquals(tableObject.id, tableObjectFromDatabase!!.id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(uuid, tableObjectFromDatabase.uuid)
    }
    // End create(uuid: UUID, tableId: Int, properties: ArrayList<Property>) tests

    private fun File.copyInputStreamToFile(inputStream: InputStream) {
        // Return the progress as int between 0 and 100
        inputStream.use { input ->
            this.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }
    }
}