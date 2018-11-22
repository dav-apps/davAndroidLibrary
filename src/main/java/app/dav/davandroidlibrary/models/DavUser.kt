package app.dav.davandroidlibrary.models

import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.common.ProjectInterface
import java.io.File

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
    var avatar: File? = null
    var avatarEtag: String
        get() = getAvatarEtagFromSettings()
        set(value) = setAvatarEtagInSettings(value)
    var isLoggedIn: Boolean = false
    var jwt: String
        get() = getJwtFromSettings()
        set(value) = setJwtInSettings(value)

    constructor(){
        // Get the user information from the local settings
        if(!getJwtFromSettings().isEmpty()){
            // User is logged in. Get the user information
            getUserInformation()


        }else{
            isLoggedIn = false
        }
    }

    private fun downloadUserInformation(){

    }

    private fun getUserInformation(){
        isLoggedIn = true
        // TODO avatar = getAvatar
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
}

enum class DavPlan(val plan: Int){
    Free(0),
    Plus(1)
}