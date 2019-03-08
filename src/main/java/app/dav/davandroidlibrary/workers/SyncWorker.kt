package app.dav.davandroidlibrary.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.dav.davandroidlibrary.data.DataManager
import app.dav.davandroidlibrary.data.jwtKey
import app.dav.davandroidlibrary.data.parallelTableIdsKey
import app.dav.davandroidlibrary.data.tableIdsKey
import kotlinx.coroutines.runBlocking
import java.util.*

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params){
    override fun doWork(): Result {
        val jwt = inputData.getString(jwtKey) ?: return Result.failure()
        val tableIds = inputData.getIntArray(tableIdsKey) ?: return Result.failure()
        val parallelTableIds = inputData.getIntArray(parallelTableIdsKey) ?: return Result.failure()

        val tableIdsArrayList = arrayListOf<Int>()
        for(i in tableIds){
            tableIdsArrayList.add(i)
        }

        val parallelTableIdsArrayList = arrayListOf<Int>()
        for(i in parallelTableIds){
            parallelTableIdsArrayList.add(i)
        }

        SyncWorker.jwt = jwt
        SyncWorker.tableIds = tableIdsArrayList
        SyncWorker.parallelTableIds = parallelTableIdsArrayList
        SyncWorker.isSyncing = true

        runBlocking {
            DataManager.sync().await()
        }

        SyncWorker.isSyncing = false
        return Result.success()
    }

    companion object {
        var jwt = ""
        var tableIds: ArrayList<Int>? = null
        var parallelTableIds: ArrayList<Int>? = null
        var isSyncing = false
    }
}