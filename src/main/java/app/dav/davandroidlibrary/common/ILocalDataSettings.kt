package app.dav.davandroidlibrary.common

interface ILocalDataSettings {
    fun setBooleanValue(key: String, value: Boolean)
    fun getBooleanValue(key: String, defaultValue: Boolean) : Boolean
    fun setStringValue(key: String, value: String)
    fun getStringValue(key: String, defaultValue: String) : String
    fun setLongValue(key: String, value: Long)
    fun getLongValue(key: String, defaultValue: Long) : Long
    fun setIntValue(key: String, value: Int)
    fun getIntValue(key: String, defaultValue: Int) : Int
}