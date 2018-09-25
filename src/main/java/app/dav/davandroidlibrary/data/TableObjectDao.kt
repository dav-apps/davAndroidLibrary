package app.dav.davandroidlibrary.data

import android.arch.persistence.room.*
import app.dav.davandroidlibrary.models.TableObjectEntity

@Dao
interface TableObjectDao {
    @Insert()
    fun insertTableObject(tableObject: TableObjectEntity) : Long

    @Query("SELECT * FROM TableObject;")
    fun getTableObjects() : List<TableObjectEntity>

    @Query("SELECT * FROM TableObject WHERE id == :id;")
    fun getTableObject(id: Long) : TableObjectEntity

    @Query("SELECT * FROM TableObject WHERE uuid == :uuid;")
    fun getTableObject(uuid: String) : TableObjectEntity

    @Update()
    fun updateTableObject(tableObject: TableObjectEntity)

    @Delete
    fun deleteTableObject(tableObject: TableObjectEntity)
}