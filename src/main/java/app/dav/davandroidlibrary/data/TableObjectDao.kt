package app.dav.davandroidlibrary.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

@Dao
interface TableObjectDao {
    @Query("SELECT * from TableObject")
    fun getTableObjects(): LiveData<List<TableObject>>

    @Query("SELECT * FROM TableObject WHERE id == :id")
    fun getTableObject(id: Int): LiveData<TableObject>

    @Insert()
    fun insertTableObject(tableObject: TableObject)
}