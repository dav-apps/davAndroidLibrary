package app.dav.davandroidlibrary.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update
import java.util.*

@Dao
interface TableObjectDao {
    @Insert()
    fun insertTableObject(tableObject: TableObjectEntity) : Long

    @Query("SELECT * FROM TableObject;")
    fun getTableObjects() : List<TableObjectEntity>

    @Query("SELECT * FROM TableObject;")
    fun getNonObservableTableObjects() : List<TableObjectEntity>

    @Query("SELECT * FROM TableObject WHERE id == :id;")
    fun getTableObject(id: Long) : TableObjectEntity

    @Query("SELECT * FROM TableObject WHERE uuid == :uuid;")
    fun getTableObject(uuid: String) : TableObjectEntity

    @Update()
    fun updateTableObject(tableObject: TableObjectEntity)
}