package app.dav.davandroidlibrary.data

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import android.arch.persistence.room.Update

@Dao
interface PropertyDao{
    @Insert()
    fun insertProperty(property: PropertyEntity) : Long

    @Query("SELECT * FROM Property;")
    fun getProperties(): LiveData<List<PropertyEntity>>

    @Query("SELECT * FROM Property WHERE id == :id;")
    fun getProperty(id: Long): LiveData<PropertyEntity>

    @Query("SELECT * FROM Property WHERE table_object_id == :tableObjectId;")
    fun getPropertiesOfTableObject(tableObjectId: Long): LiveData<List<PropertyEntity>>

    @Query("SELECT * FROM Property WHERE table_object_id == :tableObjectId;")
    fun getNonObservablePropertiesOfTableObject(tableObjectId: Long) : Array<PropertyEntity>

    @Update()
    fun updateProperty(property: PropertyEntity)
}