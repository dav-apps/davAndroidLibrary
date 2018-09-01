package app.dav.davandroidlibrary.data

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey
import org.jetbrains.annotations.NotNull
/*
@Entity(
        foreignKeys = [
            ForeignKey(entity = TableObject::class, parentColumns = ["id"], childColumns = ["table_object_id"])
        ]
)
*/
@Entity
class Property(
        @PrimaryKey @NotNull val id: Int,
        @ColumnInfo(name = "table_object_id") val tableObjectId: Int,
        val name: String,
        val value: String
)