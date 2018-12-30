package app.dav.davandroidlibrary

import app.dav.davandroidlibrary.models.DavPlan
import app.dav.davandroidlibrary.models.Property
import app.dav.davandroidlibrary.models.TableObject
import java.util.*

object Constants {
    const val apiKey = "MhKSDyedSw8WXfLk2hkXzmElsiVStD7C8JU3KNGp"
    const val appId = 3
    const val testDataTableId = 3
    const val testFileTableId = 4
    const val davDataPath = "/data/user/0/app.dav.davandroidlibrary.test/dav/"

    const val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbWFpbCI6ImRhdmNsYXNzbGlicmFyeXRlc3RAZGF2LWFwcHMudGVjaCIsInVzZXJuYW1lIjoiZGF2Q2xhc3NMaWJyYXJ5VGVzdFVzZXIiLCJ1c2VyX2lkIjo1LCJkZXZfaWQiOjIsImV4cCI6Mzc1NDU5NTI5Nzd9.Lyo5-DHTlESBM-bcw1DcNfngCVNRKLFHYnNkhsdYA7U"
    const val testuserEmail = "davclasslibrarytest@dav-apps.tech"
    const val testuserUsername = "davClassLibraryTestUser"
    val testuserPlan = DavPlan.Free

    const val testDataFirstPropertyName = "page1"
    const val testDataSecondPropertyName = "page2"
    const val testDataFirstTableObjectFirstPropertyValue = "Hello World"
    const val testDataFirstTableObjectSecondPropertyValue = "Hallo Welt"
    const val testDataSecondTableObjectFirstPropertyValue = "Table"
    const val testDataSecondTableObjectSecondPropertyValue = "Tabelle"
    val testDataFirstTableObject = TableObject(UUID.fromString("642e6407-f357-4e03-b9c2-82f754931161"), testDataTableId, arrayListOf(
        Property(0, testDataFirstPropertyName, testDataFirstTableObjectFirstPropertyValue),
        Property(0, testDataSecondPropertyName, testDataFirstTableObjectSecondPropertyValue)
    ))
    val testDataSecondTableObject  = TableObject(UUID.fromString("8d29f002-9511-407b-8289-5ebdcb5a5559"), testDataTableId, arrayListOf(
        Property(0, testDataFirstPropertyName, testDataSecondTableObjectFirstPropertyValue),
        Property(0, testDataSecondPropertyName, testDataSecondTableObjectSecondPropertyValue)
    ))
}