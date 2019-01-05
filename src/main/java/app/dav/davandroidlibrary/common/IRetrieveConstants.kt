package app.dav.davandroidlibrary.common

interface IRetrieveConstants {
    fun getDataPath() : String
    fun getApiKey() : String
    fun getAppId() : Int
    fun getTableIds() : ArrayList<Int>
    fun getParallelTableIds() : ArrayList<Int>
}