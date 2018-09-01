package app.dav.davandroidlibrary.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

@Dao
interface PropertyDao{
    @Query("SELECT * FROM Property")
    fun getProperties(): LiveData<List<Property>>

    @Query("SELECT * FROM Property WHERE id == :id")
    fun getProperty(id: Int): LiveData<Property>

    @Insert()
    fun insertProperty(property: Property)
}