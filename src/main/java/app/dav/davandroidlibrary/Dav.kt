package app.dav.davandroidlibrary

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.content.Context
import app.dav.davandroidlibrary.data.DavDatabase
import app.dav.davandroidlibrary.data.TableObject

object Dav {
    const val apiBaseUrl = "https://dav-backend.herokuapp.com/v1/"
    const val DATABASE_NAME = "dav.db"
    var database: DavDatabase? = null

    fun init(context: Context){
        database = DavDatabase.getInstance(context)
    }

    object Database{
        fun createTableObject(tableObject: TableObject){
            database?.tableObjectDao()?.insertTableObject(tableObject)
        }

        fun getAllTableObjects(tableId: Int): LiveData<List<TableObject>> {
            val db = database
            return if(db != null) db.tableObjectDao().getTableObjects() else MutableLiveData<List<TableObject>>()
        }
    }
}