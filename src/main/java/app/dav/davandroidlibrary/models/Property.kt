package app.dav.davandroidlibrary.models

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey
import app.dav.davandroidlibrary.Dav

@Entity(
        tableName = "Property",
        foreignKeys = [
            ForeignKey(entity = TableObjectEntity::class, parentColumns = ["id"], childColumns = ["table_object_id"], onDelete = ForeignKey.CASCADE)
        ]
)
data class PropertyEntity(
        @ColumnInfo(name = "table_object_id") var tableObjectId: Long,
        var name: String,
        var value: String
){
    @PrimaryKey var id: Long? = null
}

class Property{
    var id: Long = 0
    var tableObjectId: Long = 0
    var name: String = ""
    var value: String = ""

    constructor()

    constructor(tableObjectId: Long, name: String, value: String){
        this.tableObjectId = tableObjectId
        this.name = name
        this.value = value
    }

    suspend fun setPropertyValue(value: String){
        this.value = value
        save()
    }

    private suspend fun save(){
        // Check if the table object already exists
        if(tableObjectId != 0L){
            if(!Dav.Database.propertyExistsAsync(id).await()){
                id = Dav.Database.createProperty(this)
            }else{
                Dav.Database.updateProperty(this)
            }
        }
    }

    fun toPropertyEntity() : PropertyEntity{
        val propertyEntity = PropertyEntity(tableObjectId, name, value)
        propertyEntity.id = if(id == 0L) null else id
        return propertyEntity
    }

    companion object {
        suspend fun create(tableObjectId: Long, name: String, value: String) : Property{
            val property = Property(tableObjectId, name, value)
            property.save()
            return property
        }

        fun convertPropertyEntityToProperty(propertyEntity: PropertyEntity) : Property {
            val property = Property()
            property.id = propertyEntity.id ?: 0
            property.tableObjectId = propertyEntity.tableObjectId
            property.name = propertyEntity.name
            property.value = propertyEntity.value
            return property
        }
    }
}