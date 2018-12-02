package app.dav.davandroidlibrary.models

import org.json.JSONObject

class Table(
        val id: Int,
        val appId: Int,
        val name: String
)

internal class TableData(json: String) : JSONObject(json){
    val id: Int = this.optInt("id")
    val app_id: Int? = this.optInt("app_id")
    val name: String = this.optString("name")
    val pages: Int = this.optInt("pages")
    val table_objects = this.optJSONArray("table_objects")
            ?.let { 0.until(it.length()).map { i -> it.optJSONObject(i) } }
            ?.map { TableObjectData(it.toString()) }
}