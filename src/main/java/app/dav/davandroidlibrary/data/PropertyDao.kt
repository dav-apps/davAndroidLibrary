package app.dav.davandroidlibrary.data

import androidx.room.*
import app.dav.davandroidlibrary.models.PropertyEntity

@Dao
interface PropertyDao{
    @Insert()
    fun insertProperty(property: PropertyEntity) : Long

    @Transaction @Query("SELECT * FROM Property;")
    fun getProperties() : List<PropertyEntity>

    @Transaction @Query("SELECT * FROM Property WHERE id == :id;")
    fun getProperty(id: Long) : PropertyEntity

    @Transaction @Query("SELECT * FROM Property WHERE table_object_id == :tableObjectId;")
    fun getPropertiesOfTableObject(tableObjectId: Long) : List<PropertyEntity>

    @Update()
    fun updateProperty(property: PropertyEntity)
}