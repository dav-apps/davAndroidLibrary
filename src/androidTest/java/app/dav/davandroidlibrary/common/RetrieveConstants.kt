package app.dav.davandroidlibrary.common

import app.dav.davandroidlibrary.Constants
import java.io.File

class RetrieveConstants : IRetrieveConstants {
    override fun getDataPath(): String {
        val davFolder = File(Constants.davDataPath)
        if(!davFolder.exists()){
            davFolder.mkdir()
        }
        return davFolder.path + "/"
    }

    override fun getApiKey(): String {
        return Constants.apiKey
    }

    override fun getAppId(): Int {
        return Constants.appId
    }

    override fun getTableIds(): ArrayList<Int> {
        return arrayListOf(
                Constants.testDataTableId,
                Constants.testFileTableId
        )
    }

    override fun getParallelTableIds(): ArrayList<Int> {
        return arrayListOf()
    }
}