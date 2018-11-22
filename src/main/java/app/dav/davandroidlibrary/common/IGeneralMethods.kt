package app.dav.davandroidlibrary.common

import app.dav.davandroidlibrary.DavEnvironment

interface IGeneralMethods {
    fun isNetworkAvailable() : Boolean
    fun getEnvironment() : DavEnvironment
}