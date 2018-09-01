package app.dav.davandroidlibrary.data

import android.arch.persistence.room.TypeConverter
import java.util.*

class Converters{
    @TypeConverter
    fun tableObjectVisibilityToInt(visibility: TableObjectVisibility) : Int{
        return when(visibility){
            TableObjectVisibility.Public -> 2
            TableObjectVisibility.Protected -> 1
            else -> 0
        }
    }

    @TypeConverter
    fun intToTableObjectVisibility(visibility: Int) : TableObjectVisibility{
        return when(visibility){
            2 -> TableObjectVisibility.Public
            1 -> TableObjectVisibility.Protected
            else -> TableObjectVisibility.Private
        }
    }

    @TypeConverter
    fun tableObjectUploadStatusToInt(uploadStatus: TableObjectUploadStatus) : Int{
        return when(uploadStatus){
            TableObjectUploadStatus.NoUpload -> 4
            TableObjectUploadStatus.Deleted -> 3
            TableObjectUploadStatus.Updated -> 2
            TableObjectUploadStatus.New -> 1
            else -> 0
        }
    }

    @TypeConverter
    fun intToTableObjectUploadStatus(uploadStatus: Int) : TableObjectUploadStatus{
        return when(uploadStatus){
            4 -> TableObjectUploadStatus.NoUpload
            3 -> TableObjectUploadStatus.Deleted
            2 -> TableObjectUploadStatus.Updated
            1 -> TableObjectUploadStatus.New
            else -> TableObjectUploadStatus.UpToDate
        }
    }

    @TypeConverter
    fun uuidToString(uuid: UUID) : String{
        return uuid.toString()
    }

    @TypeConverter
    fun stringToUuid(uuid: String) : UUID{
        return UUID.fromString(uuid)
    }
}