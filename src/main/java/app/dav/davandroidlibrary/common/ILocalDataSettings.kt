package app.dav.davandroidlibrary.common

interface ILocalDataSettings {
    fun setBooleanValue(key: String, value: Boolean)
    fun getBooleanValue(key: String) : Boolean?
}