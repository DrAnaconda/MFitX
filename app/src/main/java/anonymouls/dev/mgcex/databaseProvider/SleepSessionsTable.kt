package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.*

object SleepSessionsTable {
    const val TableName = "SleepSessions"
    val ColumnNames = arrayOf("ID", "Date")

    fun getCreateTableCommand(): String {
        return "create table if not exists " + TableName + " (" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnNames[1] + " INTEGER UNIQUE" +
                ");"
    }

    private fun getIDFromRecTime(recTime: Long, db: SQLiteDatabase): Long {
        val curs = db.query(TableName, arrayOf(ColumnNames[0]), ColumnNames[1] + " = ?", arrayOf(recTime.toString()),
                null, null, null, "1")
        var result: Long = -1
        if (curs.count > 0) {
            curs.moveToFirst(); result = curs.getLong(0)
        }
        curs.close()
        return result
    }

    fun insertRecord(recTime: Long, db: SQLiteDatabase): Long {
        val values = ContentValues()
        values.put(ColumnNames[1], recTime)
        val checker: Long = getIDFromRecTime(recTime, db)
        return if (checker == (-1).toLong())
            db.insert(TableName, null, values)
        else checker

    }

    fun insertRecord(RecordTime: Calendar, Operator: SQLiteDatabase): Long {
        return insertRecord(CustomDatabaseUtils.CalendarToLong(RecordTime, true), Operator)
    }

    fun findAssigment(recTarget: Long, db: SQLiteDatabase): Long {
        val curs = db.query(TableName, arrayOf(ColumnNames[0]), ColumnNames[1] + " <= ?", arrayOf(recTarget.toString()),
                null, null, ColumnNames[1] + " DESC", "1")
        var result: Long = -1
        if (curs.count > 0) {
            result = curs.getLong(0)
        }
        curs.close()
        return result
    }


    fun dropSession(ID: Long, db: SQLiteDatabase) {
        db.delete(SleepRecordsTable.TableName, "IDSS = $ID", null)
        db.delete(TableName, "ID = $ID", null)
    }

    class SleepRecordSession(var TimeRecord: Long, var Duration: Int, var DeepDuration: Int) {
        private var LightDuration = -1

        private fun AutoCalculateLight() {
            if (Duration != -1 && LightDuration != -1) {
                LightDuration = Duration - DeepDuration
            }
        }

        init {
            AutoCalculateLight()
        }
    }

}