package app.dav.davandroidlibrary.models

import android.util.Log
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.data.DataManager
import kotlinx.coroutines.experimental.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InputStream

private const val avatarFileName = "avatar.png"
private val avatarFilePath = Dav.dataPath + avatarFileName

class DavUser() {
    var email: String
        get() = getEmailFromSettings()
        set(value) = setEmailInSettings(value)
    var username: String
        get() = getUsernameFromSettings()
        set(value) = setUsernameInSettings(value)
    var totalStorage: Long
        get() = getTotalStorageFromSettings()
        set(value) = setTotalStorageInSettings(value)
    var usedStorage: Long
        get() = getUsedStorageFromSettings()
        set(value) = setUsedStorageInSettings(value)
    var plan: DavPlan
        get() = getPlanFromSettings()
        set(value) = setPlanInSettings(value)
    var avatar: File = File(avatarFilePath)
    var avatarEtag: String
        get() = getAvatarEtagFromSettings()
        set(value) = setAvatarEtagInSettings(value)
    var isLoggedIn: Boolean = false
    var jwt: String
        get() = getJwtFromSettings()
        set(value) = setJwtInSettings(value)

    init {
        if(!jwt.isEmpty()){
            // User is logged in. Get the user information
            isLoggedIn = true

            GlobalScope.launch(Dispatchers.IO) {
                downloadUserInformation()
                DataManager.Sync().await()
            }
        }else{
            isLoggedIn = false
        }
    }

    suspend fun login(jwt: String){
        this.jwt = jwt
        isLoggedIn = true
        if(downloadUserInformation())
            GlobalScope.launch(Dispatchers.IO) { DataManager.Sync().await() }
        else
            logout()
    }

    fun logout(){
        // Clear all values
        isLoggedIn = false
        jwt = ""
        email = ""
        username = ""
        totalStorage = 0
        usedStorage = 0
        plan = DavPlan.Free
        avatarEtag = ""

        // Delete the avatar
        deleteAvatar()
    }

    private fun deleteAvatar(){
        val avatarFile = File(avatarFilePath)
        if(avatarFile.exists())
            avatarFile.delete()
    }

    private suspend fun downloadUserInformation() : Boolean{
        if(!isLoggedIn) return false
        val getResult = DataManager.httpGet(jwt, Dav.getUserUrl).await()

        if(getResult.key){
            val userData = DavUserData(getResult.value)
            email = userData.email
            username = userData.username
            totalStorage = userData.total_storage
            usedStorage = userData.used_storage
            plan = convertIntToDavPlan(userData.plan)
            val newAvatarEtag = userData.avatar_etag
            val avatarUrl = userData.avatar

            val avatarFile = File(avatarFilePath)

            if(avatarEtag != newAvatarEtag || !avatarFile.exists()){
                // Download the avatar
                GlobalScope.launch { downloadAvatar(avatarUrl) }
            }

            if(avatarFile.exists()){
                avatar = avatarFile
                avatarEtag = newAvatarEtag
            }

            return true
        }else{
            Log.e("DavUser", getResult.value)
            return false
        }
    }

    private suspend fun downloadAvatar(avatarUrl: String){
        try{
            GlobalScope.async {
                val client = OkHttpClient()
                val request = Request.Builder()
                        .url(avatarUrl)
                        .build()

                // Get the image
                val response = client.newCall(request).execute()
                val byteStream: InputStream = response.body()?.byteStream() ?: return@async
                val file = File(avatarFilePath)
                file.copyInputStreamToFile(byteStream)
            }.await()
        }catch (e: Exception){
            Log.d("DavUser", "There was an error when downloading the avatar: ${e.message}")
        }
    }

    private fun File.copyInputStreamToFile(inputStream: InputStream) {
        inputStream.use { input ->
            this.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }
    }

    companion object {
        private fun convertIntToDavPlan(plan: Int) : DavPlan{
            return when(plan){
                1 -> DavPlan.Plus
                else -> DavPlan.Free
            }
        }

        internal fun getEmailFromSettings() : String{
            val localDataSettings = ProjectInterface.localDataSettings ?: return ""
            return localDataSettings.getStringValue(Dav.emailKey, "")
        }

        internal fun getUsernameFromSettings() : String{
            val localDataSettings = ProjectInterface.localDataSettings ?: return ""
            return localDataSettings.getStringValue(Dav.usernameKey, "")
        }

        internal fun getTotalStorageFromSettings() : Long{
            val localDataSettings = ProjectInterface.localDataSettings ?: return 0
            return localDataSettings.getLongValue(Dav.totalStorageKey, 0)
        }

        internal fun getUsedStorageFromSettings() : Long{
            val localDataSettings = ProjectInterface.localDataSettings ?: return 0
            return localDataSettings.getLongValue(Dav.usedStorageKey, 0)
        }

        internal fun getPlanFromSettings() : DavPlan{
            val localDataSettings = ProjectInterface.localDataSettings ?: return DavPlan.Free
            return if(localDataSettings.getIntValue(Dav.planKey, 0) == 1) DavPlan.Plus else DavPlan.Free
        }

        internal fun getAvatarEtagFromSettings() : String{
            val localDataSettings = ProjectInterface.localDataSettings ?: return ""
            return localDataSettings.getStringValue(Dav.avatarEtagKey, "")
        }

        internal fun getJwtFromSettings() : String{
            val localDataSettings = ProjectInterface.localDataSettings ?: return ""
            return localDataSettings.getStringValue(Dav.jwtKey, "")
        }

        internal fun setEmailInSettings(value: String){
            ProjectInterface.localDataSettings?.setStringValue(Dav.emailKey, value)
        }

        internal fun setUsernameInSettings(value: String){
            ProjectInterface.localDataSettings?.setStringValue(Dav.usernameKey, value)
        }

        internal fun setTotalStorageInSettings(value: Long){
            ProjectInterface.localDataSettings?.setLongValue(Dav.totalStorageKey, value)
        }

        internal fun setUsedStorageInSettings(value: Long){
            ProjectInterface.localDataSettings?.setLongValue(Dav.usedStorageKey, value)
        }

        internal fun setPlanInSettings(value: DavPlan){
            ProjectInterface.localDataSettings?.setIntValue(Dav.planKey, value.plan)
        }

        internal fun setAvatarEtagInSettings(value: String){
            ProjectInterface.localDataSettings?.setStringValue(Dav.avatarEtagKey, value)
        }

        internal fun setJwtInSettings(value: String){
            ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, value)
        }
    }
}

enum class DavPlan(val plan: Int){
    Free(0),
    Plus(1)
}

internal class DavUserData(json: String) : JSONObject(json){
    val email: String = this.optString("email")
    val username: String = this.optString("username")
    val total_storage: Long = this.optLong("total_storage")
    val used_storage: Long = this.optLong("used_storage")
    val plan: Int = this.optInt("plan")
    val avatar: String = this.optString("avatar")
    val avatar_etag: String = this.optString("avatar_etag")
}