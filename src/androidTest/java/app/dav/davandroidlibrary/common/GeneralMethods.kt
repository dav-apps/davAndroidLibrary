package app.dav.davandroidlibrary.common

import app.dav.davandroidlibrary.DavEnvironment

class GeneralMethods : IGeneralMethods{
    override fun isNetworkAvailable(): Boolean {
        return true
    }

    override fun getEnvironment(): DavEnvironment {
        return DavEnvironment.Test
    }
}