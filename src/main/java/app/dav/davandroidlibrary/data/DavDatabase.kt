package app.dav.davandroidlibrary.data

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import app.dav.davandroidlibrary.Dav.databaseName
import app.dav.davandroidlibrary.models.PropertyEntity
import app.dav.davandroidlibrary.models.TableObjectEntity

@Database(entities = [TableObjectEntity::class, PropertyEntity::class], version = 1)
abstract class DavDatabase : RoomDatabase() {
    abstract fun tableObjectDao(): TableObjectDao
    abstract fun propertyDao(): PropertyDao

    companion object {
        // For Singleton instantiation
        @Volatile private var instance: DavDatabase? = null

        fun getInstance(context: Context): DavDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): DavDatabase {
            return Room.databaseBuilder(context, DavDatabase::class.java, databaseName).build()
        }
    }
}