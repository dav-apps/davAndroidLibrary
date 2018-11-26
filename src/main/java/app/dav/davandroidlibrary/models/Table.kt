package app.dav.davandroidlibrary.models

class Table(
        val id: Int,
        val appId: Int,
        val name: String
)

internal data class TableData(
        val id: Int,
        val app_id: Int,
        val name: String,
        val pages: Int,
        val table_objects: Array<TableObjectData>
)