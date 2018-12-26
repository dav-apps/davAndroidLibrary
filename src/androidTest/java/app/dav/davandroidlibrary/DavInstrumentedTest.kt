package app.dav.davandroidlibrary

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import app.dav.davandroidlibrary.data.DavDatabase
import app.dav.davandroidlibrary.models.Property
import app.dav.davandroidlibrary.models.TableObject
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
}