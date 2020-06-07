package anonymouls.dev.mgcex.DatabaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.*

object SleepRecordsTable {
    val ColumnNames = arrayOf("ID", "Timestamp", "IDSS", "Duration", "Type")


    fun GetCreateTableCommand(): String {
        return "CREATE TABLE " + DatabaseController.SleepRecordsTableName + "(" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnNames[1] + " INTEGER UNIQUE," +
                ColumnNames[2] + " INTEGER," +
                ColumnNames[3] + " INTEGER," +
                ColumnNames[4] + " INTEGER," +
                "FOREIGN KEY (" + ColumnNames[2] + ") REFERENCES " + DatabaseController.SleepSessionTableName + " (" + SleepSessionsTable.ColumnNames[0] + ")" +
                ");"
    }

    fun insertRecord(RecordTime: Calendar, SSID: Long, Duration: Int, Type: Int, Operator: SQLiteDatabase): Long {
        val Values = ContentValues()
        val recordDate = CustomDatabaseUtils.CalendarToLong(RecordTime, true)
        Values.put(ColumnNames[1], recordDate)
        Values.put(ColumnNames[2], SSID)
        Values.put(ColumnNames[3], Duration)
        Values.put(ColumnNames[4], Type)
        return try {
            val curs = Operator.query(DatabaseController.SleepRecordsTableName, arrayOf(ColumnNames[1]), ColumnNames[1] + " = ?", arrayOf(recordDate.toString()),
                    null, null, null)
            if (curs.count > 0) {
                curs.close(); -1
            } else {
                curs.close()
                Operator.insert(DatabaseController.SleepRecordsTableName, null, Values)
            }
        } catch (ex: Exception) {
            -1
        }
    }

    fun ExtractValues(Argument: Calendar, Operator: SQLiteDatabase): List<SleepRecord> {
        val Results = ArrayList<SleepRecord>()
        val SelectArg = CustomDatabaseUtils.CalendarToLong(Argument, false)
        val SSID = GetIDSleepSession(SelectArg, Operator)
        val records = Operator.query(DatabaseController.SleepRecordsTableName, ColumnNames,
                ColumnNames[2] + "=?", arrayOf(SSID.toString()), null, null, ColumnNames[1] + " DESC")
        records.moveToFirst()
        do {
            Results.add(SleepRecord(CustomDatabaseUtils.LongToCalendar(records.getLong(1), true),
                    records.getInt(3), records.getInt(4)))
        } while (records.moveToNext())
        records.close()
        return Results
    }

    fun GetIDSleepSession(Argument: Calendar, Operator: SQLiteDatabase): Long {
        val FindArgument = CustomDatabaseUtils.CalendarToLong(Argument, false)
        return GetIDSleepSession(FindArgument, Operator)
    }

    fun fixDurations(Operator: SQLiteDatabase) {
        val curs = Operator.query(DatabaseController.SleepRecordsTableName, arrayOf(ColumnNames[0], ColumnNames[3]),
                " " + ColumnNames[3] + " < 0", null, null, null, null)
        if (curs.count == 0) return
        curs.moveToFirst()
        do {
            val values = ContentValues()
            val duration = curs.getInt(1)
            values.put(ColumnNames[3], (duration and 0xFF))
            Operator.update(DatabaseController.SleepRecordsTableName, values, " " + ColumnNames[0] + " = ?", arrayOf(curs.getInt(0).toString()))
        } while (curs.moveToNext())
        curs.close()
    }

    fun GetIDSleepSession(Argument: Long, Operator: SQLiteDatabase): Long {
        val record = Operator.query(DatabaseController.SleepSessionTableName,
                SleepSessionsTable.ColumnNames,
                SleepSessionsTable.ColumnNames[1] + " =?", arrayOf(java.lang.Long.toString(Argument)), null, null, null)
        return if (record.count == 0) {
            record.close(); -1
        } else {
            record.moveToFirst()
            val result = record.getLong(0)
            record.close()
            result
        }
    }

    class SleepRecord(private val RTime: Calendar, private val Duration: Int, private val TypeRecord: Int) {
        companion object {
            private val DeepSleepType: Short = 0
            private val LightSleepType: Short = 1
            private val AwakeType: Short = 2
        }
    }
}
// Works fine
