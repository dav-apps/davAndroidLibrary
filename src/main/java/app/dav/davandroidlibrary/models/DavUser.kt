package app.dav.davandroidlibrary.models

import android.util.Log
import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.ProjectInterface
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

private const val avatarFileName = "avatar.png"
private val avatarFilePath = Dav.dataPath + avatarFileName

class DavUser{
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

    constructor(){
        // Get the user information from the local settings
        if(!jwt.isEmpty()){
            // User is logged in. Get the user information
            isLoggedIn = true

            GlobalScope.launch {
                downloadUserInformation()
                // TODO Sync
            }
        }else{
            isLoggedIn = false
        }
    }

    suspend fun login(jwt: String){
        this.jwt = jwt
        isLoggedIn = true
        if(downloadUserInformation())
            // TODO Sync
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
        val getResult = Dav.DataManager.httpGet(jwt, Dav.getUserUrl)

        if(getResult.key){
            val json = Klaxon().parse<DavUserData>(getResult.value) ?: return false
            email = json.email
            username = json.username
            totalStorage = json.total_storage
            usedStorage = json.used_storage
            plan = convertIntToDavPlan(json.plan)
            val newAvatarEtag = json.avatar_etag
            val avatarUrl = json.avatar

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

    private fun getEmailFromSettings() : String{
        val localDataSettings = ProjectInterface.localDataSettings ?: return ""
        return localDataSettings.getStringValue(Dav.emailKey, "")
    }

    private fun getUsernameFromSettings() : String{
        val localDataSettings = ProjectInterface.localDataSettings ?: return ""
        return localDataSettings.getStringValue(Dav.usernameKey, "")
    }

    private fun getTotalStorageFromSettings() : Long{
        val localDataSettings = ProjectInterface.localDataSettings ?: return 0
        return localDataSettings.getLongValue(Dav.totalStorageKey, 0)
    }

    private fun getUsedStorageFromSettings() : Long{
        val localDataSettings = ProjectInterface.localDataSettings ?: return 0
        return localDataSettings.getLongValue(Dav.usedStorageKey, 0)
    }

    private fun getPlanFromSettings() : DavPlan{
        val localDataSettings = ProjectInterface.localDataSettings ?: return DavPlan.Free
        return if(localDataSettings.getIntValue(Dav.planKey, 0) == 1) DavPlan.Plus else DavPlan.Free
    }

    private fun getAvatarEtagFromSettings() : String{
        val localDataSettings = ProjectInterface.localDataSettings ?: return ""
        return localDataSettings.getStringValue(Dav.avatarEtagKey, "")
    }

    private fun getJwtFromSettings() : String{
        val localDataSettings = ProjectInterface.localDataSettings ?: return ""
        return localDataSettings.getStringValue(Dav.jwtKey, "")
    }

    private fun setEmailInSettings(value: String){
        ProjectInterface.localDataSettings?.setStringValue(Dav.emailKey, value)
    }

    private fun setUsernameInSettings(value: String){
        ProjectInterface.localDataSettings?.setStringValue(Dav.usernameKey, value)
    }

    private fun setTotalStorageInSettings(value: Long){
        ProjectInterface.localDataSettings?.setLongValue(Dav.totalStorageKey, value)
    }

    private fun setUsedStorageInSettings(value: Long){
        ProjectInterface.localDataSettings?.setLongValue(Dav.usedStorageKey, value)
    }

    private fun setPlanInSettings(value: DavPlan){
        ProjectInterface.localDataSettings?.setIntValue(Dav.planKey, value.plan)
    }

    private fun setAvatarEtagInSettings(value: String){
        ProjectInterface.localDataSettings?.setStringValue(Dav.avatarEtagKey, value)
    }

    private fun setJwtInSettings(value: String){
        ProjectInterface.localDataSettings?.setStringValue(Dav.jwtKey, value)
    }

    companion object {
        private fun convertIntToDavPlan(plan: Int) : DavPlan{
            return when(plan){
                1 -> DavPlan.Plus
                else -> DavPlan.Free
            }
        }
    }
}

enum class DavPlan(val plan: Int){
    Free(0),
    Plus(1)
}

data class DavUserData(val email: String,
                       val username: String,
                       val total_storage: Long,
                       val used_storage: Long,
                       val plan: Int,
                       val avatar: String,
                       val avatar_etag: String)