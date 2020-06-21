package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.*

object AdvancedActivityTracker {

    const val TableName = "AdvancedActivity"

    val TableColumns = arrayListOf("ID", "DATE", "deltaMin", "deltaSteps")

    fun getCreateCommand(): String {
        return "Create table if not exists " + TableName + "(" +
                TableColumns[0] + " integer primary key autoincrement," +
                TableColumns[1] + " integer unique not null," +
                TableColumns[2] + " integer not null," +
                TableColumns[3] + " double not null);"
    }

    private fun checkIsExists(time: Long, db: SQLiteDatabase): Boolean {
        val curs = db.query(TableName, arrayOf(TableColumns[0]), TableColumns[1] + " = ?", arrayOf(time.toString()), null, null, null, "1")
        return if (curs.count > 0) {
            curs.close(); true
        } else {
            curs.close(); false; }
    }

    fun insertRecord(time: Calendar, deltaMin: Int, speedMin: Double, db: SQLiteDatabase): Long {
        return insertRecord(CustomDatabaseUtils.CalendarToLong(time, true), deltaMin, speedMin, db)
    }

    fun insertRecord(time: Long, deltaMin: Int, speedMin: Double, db: SQLiteDatabase): Long {
        val content = ContentValues()
        content.put(TableColumns[2], deltaMin)
        content.put(TableColumns[3], speedMin)
        if (!checkIsExists(time, db)) {
            content.put(TableColumns[1], time)
            return db.insert(TableName, null, content)
        } else {
            return updateRecord(content, time, db).toLong()
        }
    }

    private fun updateRecord(contentValues: ContentValues, time: Long, db: SQLiteDatabase): Int {
        return db.update(TableName, contentValues, TableColumns[1] + " = ?", arrayOf(time.toString()))
    }


    fun optimizeBySleepRange(from: Long, to: Long, db: SQLiteDatabase) {
        db.delete(TableName, TableColumns[1] + " >= ? and " + TableColumns[1] + " <= ?", arrayOf(from.toString(), to.toString()))
    }

    fun optimizeBySleepRange(from: Calendar, to: Calendar, db: SQLiteDatabase) {
        optimizeBySleepRange(CustomDatabaseUtils.CalendarToLong(from, true),
                CustomDatabaseUtils.CalendarToLong(to, true), db)
    }
}

