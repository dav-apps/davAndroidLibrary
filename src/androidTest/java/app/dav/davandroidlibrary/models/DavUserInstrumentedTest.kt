package app.dav.davandroidlibrary.models

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import app.dav.davandroidlibrary.Constants
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.*
import app.dav.davandroidlibrary.data.DavDatabase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DavUserInstrumentedTest {
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

    // Constructor tests
    @Test
    fun constructorShouldCreateNewDavUserWithNotLoggedInUserWhenNoJWTIsSaved(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, "")

        // Act
        val user = DavUser()

        // Assert
        Assert.assertFalse(user.isLoggedIn)
    }

    @Test
    fun constructorShouldCreateNewDavUserWithLoggedInUserWhenJWTIsSaved(){
        // Arrange
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, Constants.jwt)

        // Act
        val user = DavUser()

        // Assert
        Assert.assertTrue(user.isLoggedIn)
        Assert.assertEquals(Constants.jwt, user.jwt)
    }
    // End constructor tests
}