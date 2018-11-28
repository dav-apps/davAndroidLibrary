package app.dav.davandroidlibrary.common

import app.dav.davandroidlibrary.models.TableObject

interface ITriggerAction{
    fun updateAllOfTable(tableId: Int)
    fun updateTableObject(tableObject: TableObject, fileDownloaded: Boolean)
    fun deleteTableObject(tableObject: TableObject)
}