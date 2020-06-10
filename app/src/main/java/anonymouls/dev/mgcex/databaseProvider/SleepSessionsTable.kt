package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.*

object SleepSessionsTable {
    const val TableName = "SleepSessions"
    val ColumnNames = arrayOf("ID", "Date", "Durability", "DeepDurability")

    fun getCreateTableCommand(): String {
        return "create table if not exists " + TableName + " (" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnNames[1] + " INTEGER UNIQUE," +
                ColumnNames[2] + " INTEGER DEFAULT -1," +
                ColumnNames[3] + " INTEGER DEFAULT -1" +
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

    fun insertRecord(recTime: Long, DurabilityMinutes: Int, DeepDurability: Int, db: SQLiteDatabase): Long {
        val values = ContentValues()
        values.put(ColumnNames[1], recTime)
        if (DurabilityMinutes != -1) values.put(ColumnNames[2], DurabilityMinutes)
        if (DeepDurability != -1) values.put(ColumnNames[3], DeepDurability)
        val checker: Long = getIDFromRecTime(recTime, db)
        return if (checker == (-1).toLong())
            db.insert(TableName, null, values)
        else checker

    }

    fun insertRecord(RecordTime: Calendar, DurabilityMinutes: Int, DeepDurability: Int, Operator: SQLiteDatabase): Long {
        return insertRecord(CustomDatabaseUtils.CalendarToLong(RecordTime, true), DurabilityMinutes, DeepDurability, Operator)
    }


    class SleepRecordSession(private val TimeRecord: Calendar, Duration: Int, DeepDuration: Int) {
        private var Duration = -1
        private var DeepDuration = -1
        private var LightDuration = -1

        private fun AutoCalculateLight() {
            if (Duration != -1 && LightDuration != -1) {
                LightDuration = Duration - DeepDuration
            }
        }

        init {
            this.DeepDuration = DeepDuration
            this.Duration = Duration
            AutoCalculateLight()
        }
    }

}