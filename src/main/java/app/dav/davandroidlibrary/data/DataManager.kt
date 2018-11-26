package app.dav.davandroidlibrary.data

import app.dav.davandroidlibrary.Dav
import app.dav.davandroidlibrary.HttpResultEntry
import app.dav.davandroidlibrary.common.ProjectInterface
import app.dav.davandroidlibrary.models.DavUser
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class DataManager{
    companion object {
        private var isSyncing = false

        suspend fun httpGet(jwt: String, url: String) : HttpResultEntry {
            val noInternetEntry = HttpResultEntry(false, "No internet connection")
            val isNetworkAvailable = ProjectInterface.generalMethods?.isNetworkAvailable() ?: return noInternetEntry
            if(!isNetworkAvailable) return noInternetEntry

            val client = OkHttpClient()
            val request = Request.Builder()
                    .url(Dav.apiBaseUrl + url)
                    .header("Authorization", jwt)
                    .build()

            try {
                return GlobalScope.async {
                    val response = client.newCall(request).execute()

                    if(response.isSuccessful){
                        HttpResultEntry(true, response.body()?.string() ?: "")
                    }else{
                        HttpResultEntry(false, "There was an error")
                    }
                }.await()
            }catch (e: IOException){
                return HttpResultEntry(false, e.message ?: "There was an error")
            }
        }

        internal fun Sync(){
            if(isSyncing) return

            isSyncing = true
            val jwt = DavUser.getJwtFromSettings()
            if(jwt.isEmpty()) return
            // fileDownloads.clear()
            // fileDownloaders.clear()

            // Get the specified tables

        }
    }
}