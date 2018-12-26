package app.dav.davandroidlibrary

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
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

    // createTableObject tests
    @Test
    fun createTableObjectAsyncShouldSaveTheTableObjectInTheDatabaseAndReturnTheId(){
        // Arrange
        Dav.init(context)

        val uuid = UUID.randomUUID()
        val tableId = 4
        val tableObject = TableObject(uuid, tableId)

        // Act
        val id = runBlocking {
            Dav.Database.createTableObjectAsync(tableObject).await()
        }

        // Assert
        Assert.assertNotEquals(0, id)

        // Get the table object from the database
        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(id, tableObjectFromDatabase.id)
        Assert.assertEquals(uuid, UUID.fromString(tableObjectFromDatabase.uuid))
        Assert.assertFalse(tableObjectFromDatabase.isFile)
    }
    // End createTableObject tests

    // CreateTableObjectWithProperties tests
    @Test
    fun CreateTableObjectWithPropertiesShouldSaveTheTableObjectAndItsPropertiesInTheDatabase(){
        // Arrange
        Dav.init(context)

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
        val id = runBlocking {
            Dav.Database.createTableObjectWithPropertiesAsync(tableObject).await()
        }

        // Assert
        val tableObjectFromDatabase = database.tableObjectDao().getTableObject(id)
        Assert.assertEquals(tableId, tableObjectFromDatabase.tableId)
        Assert.assertEquals(id, tableObjectFromDatabase.id)
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
}