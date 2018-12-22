package app.dav.davandroidlibrary.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.dav.davandroidlibrary.data.DataManager
import kotlinx.coroutines.runBlocking

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params){
    override fun doWork(): Result {
        runBlocking {
            DataManager.sync().await()
            DataManager.syncPush().await()
        }
        return Result.success()
    }
}