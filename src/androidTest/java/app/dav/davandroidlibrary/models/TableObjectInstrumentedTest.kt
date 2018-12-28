package app.dav.davandroidlibrary.models

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.*
import app.dav.davandroidlibrary.data.DavDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class TableObjectInstrumentedTest {
    val context = InstrumentationRegistry.getTargetContext()
    val database = DavDatabase.getInstance(context)

    init {
        Dav.init(context)

        // Drop the database
        database.tableObjectDao().deleteAllTableObjects()

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
}