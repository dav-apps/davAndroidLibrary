package app.dav.davandroidlibrary.data

import android.arch.persistence.room.Entity

class Table(
        val id: Int,
        val appId: Int,
        val name: String
)