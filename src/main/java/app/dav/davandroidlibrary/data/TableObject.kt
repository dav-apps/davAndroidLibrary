package app.dav.davandroidlibrary.data

import android.arch.persistence.room.*
import org.jetbrains.annotations.NotNull
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/*
(
        foreignKeys = [
            ForeignKey(entity = Table::class, parentColumns = ["id"], childColumns = ["table_id"])
        ]
)
*/
@Entity
class TableObject{
    @PrimaryKey @NotNull var id: Int = 0
    var tableId: Int = 0
    var visibility: TableObjectVisibility = TableObjectVisibility.Private
    var uuid: UUID = UUID.randomUUID()
    @Ignore val isFile: Boolean = false
    @Ignore val properties: ArrayList<Property> = ArrayList<Property>()
    var uploadStatus: TableObjectUploadStatus = TableObjectUploadStatus.New
    var etag: String = ""

    constructor(tableId: Int){
        this.tableId = tableId
    }

    @Ignore
    constructor(uuid: UUID, tableId: Int){
        this.uuid = uuid
        this.tableId = tableId
    }

    @Ignore
    constructor(uuid: UUID, tableId: Int, file: File){
        this.uuid = uuid
        this.tableId = tableId
    }

    @Ignore
    constructor(uuid: UUID, tableId: Int, properties: ArrayList<Property>){
        this.uuid = uuid
        this.tableId = tableId
        for (p in properties) this.properties.add(p)
    }
}

enum class TableObjectVisibility(val visibility: Int){
    Private(0),
    Protected(1),
    Public(2)
}

enum class TableObjectUploadStatus(val uploadStatus: Int){
    UpToDate(0),
    New(1),
    Updated(2),
    Deleted(3),
    NoUpload(4)
}