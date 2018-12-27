package app.dav.davandroidlibrary.common

class LocalDataSettings : ILocalDataSettings{
    private val booleanHashMap = HashMap<String, Boolean>()
    private val stringHashMap = HashMap<String, String>()
    private val longHashMap = HashMap<String, Long>()
    private val intHashMap = HashMap<String, Int>()

    override fun setBooleanValue(key: String, value: Boolean) {
        booleanHashMap[key] = value
    }

    override fun getBooleanValue(key: String, defaultValue: Boolean): Boolean {
        return booleanHashMap[key] ?: false
    }

    override fun setStringValue(key: String, value: String) {
        stringHashMap[key] = value
    }

    override fun getStringValue(key: String, defaultValue: String): String {
        return stringHashMap[key] ?: ""
    }

    override fun setLongValue(key: String, value: Long) {
        longHashMap[key] = value
    }

    override fun getLongValue(key: String, defaultValue: Long): Long {
        return longHashMap[key] ?: 0
    }

    override fun setIntValue(key: String, value: Int) {
        intHashMap[key] = value
    }

    override fun getIntValue(key: String, defaultValue: Int): Int {
        return intHashMap[key] ?: 0
    }
}