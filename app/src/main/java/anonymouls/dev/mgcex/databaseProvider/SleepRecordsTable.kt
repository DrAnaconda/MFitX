package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.MutableLiveData
import java.util.*

@ExperimentalStdlibApi
object SleepRecordsTable {
    const val TableName = "SleepRecords"
    val ColumnNames = arrayOf("ID", "Date", "IDSS", "Duration", "Type")


    fun getCreateTableCommand(): String {
        return "create table if not exists " + TableName + "(" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnNames[1] + " INTEGER UNIQUE," +
                ColumnNames[2] + " INTEGER null," +
                ColumnNames[3] + " INTEGER," +
                ColumnNames[4] + " INTEGER," +
                "FOREIGN KEY (" + ColumnNames[2] + ") REFERENCES " + SleepSessionsTable.TableName + " (" + SleepSessionsTable.ColumnNames[0] + ") ON DELETE CASCADE" +
                ");"
    }

    fun getLastSync(db: SQLiteDatabase): Long {
        val curs = db.query(TableName, arrayOf(ColumnNames[1]), null, null, null, null, ColumnNames[1] + " DESC", "1")
        if (curs.count > 0) {
            curs.moveToFirst()
            return curs.getLong(0)
        }
        curs.close()
        val result = Calendar.getInstance(); result.add(Calendar.MONTH, -6)
        return CustomDatabaseUtils.calendarToLong(result, true)
    }

    private fun assignSSID(recordDate: Long, Duration: Int, Type: Int, Operator: SQLiteDatabase): Long {
        var result: Long = -1
        when (Type) {
            SleepRecord.RecordTypes.LightOrOverall.code -> {
                if (Duration > 90) {
                    result = SleepSessionsTable.insertRecord(recordDate, Operator)
                } else {
                    result = SleepSessionsTable.findAssigment(recordDate, Operator)
                }
            }
            SleepRecord.RecordTypes.Deep.code -> {
                result = SleepSessionsTable.findAssigment(recordDate, Operator)
            }
        }
        return result
    }

    fun insertRecord(RecordTime: Calendar, SSID: Long, Duration: Int, Type: Int, Operator: SQLiteDatabase): Long {
        val Values = ContentValues()
        var SSID = SSID
        val recordDate = CustomDatabaseUtils.calendarToLong(RecordTime, true)
        Values.put(ColumnNames[1], recordDate)
        if (SSID < 0) {
            SSID = assignSSID(CustomDatabaseUtils.calendarToLong(RecordTime, true), Duration, Type, Operator)
        }
        Values.put(ColumnNames[2], SSID)
        Values.put(ColumnNames[3], Duration)
        Values.put(ColumnNames[4], Type)
        RecordTime.add(Calendar.MINUTE, Duration)
        HRRecordsTable.updateAnalyticalViaSleepInterval(
                CustomDatabaseUtils.longToCalendar(recordDate, true),
                RecordTime, Type == 2, Operator)
        return try {
            val curs = Operator.query(TableName, arrayOf(ColumnNames[1]), ColumnNames[1] + " = ?",
                    arrayOf(recordDate.toString()),
                    null, null, null)
            if (curs.count > 0) {
                curs.close(); -1
            } else {
                curs.close()
                Operator.insert(TableName, null, Values)
            }
        } catch (ex: Exception) {
            -1
        }
    }

    fun fixDurations(Operator: SQLiteDatabase) {
        val curs = Operator.query(TableName, arrayOf(ColumnNames[0], ColumnNames[3]),
                " " + ColumnNames[3] + " < 0", null, null, null, null)
        if (curs.count == 0) return
        curs.moveToFirst()
        do {
            val values = ContentValues()
            val duration = curs.getInt(1)
            values.put(ColumnNames[3], (duration and 0xFF))
            Operator.update(TableName, values, " " + ColumnNames[0] + " = ?", arrayOf(curs.getInt(0).toString()))
        } while (curs.moveToNext())
        curs.close()
    }

    class SleepRecord(var RTime: Long, var Duration: Int, var TypeRecord: Int) {
        enum class RecordTypes(val code: Int) { Deep(2), LightOrOverall(1), Awake(3) }

    }

    object GlobalSettings {
        var ignoreLightSleepData = false
        var isLaunched = MutableLiveData<Boolean>(false)
    }
}