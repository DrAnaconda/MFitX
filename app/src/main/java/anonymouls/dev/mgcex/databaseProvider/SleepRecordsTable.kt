package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.app.backend.ApplicationStarter
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

    fun getLastSync(): Long {
        val db = DatabaseController.getDCObject(ApplicationStarter.appContext).readableDatabase
        db.query(TableName, arrayOf(ColumnNames[1]), null, null,
                null, null, ColumnNames[1] + " DESC", "1").use {
            if (it.count > 0) {
                it.moveToFirst()
                return it.getLong(0)
            }
            val result = Calendar.getInstance(); result.add(Calendar.MONTH, -6)
            return CustomDatabaseUtils.calendarToLong(result, true)
        }
    }

    private fun assignSSID(recordDate: Long, Duration: Int, Type: Int, Operator: SQLiteDatabase): Long {
        var result: Long = -1
        when (Type) {
            SleepRecord.RecordTypes.LightOrOverall.code -> {
                result = if (Duration > 90) {
                    SleepSessionsTable.insertRecord(recordDate, Operator)
                } else {
                    SleepSessionsTable.findAssigment(recordDate, Operator)
                }
            }
            SleepRecord.RecordTypes.Deep.code -> {
                result = SleepSessionsTable.findAssigment(recordDate, Operator)
            }
        }
        return result
    }

    fun insertRecord(RecordTime: Calendar, SSID: Long, Duration: Int, Type: Int, Operator: SQLiteDatabase): Long {
        val values = ContentValues()
        var ssID = SSID
        val recordDate = CustomDatabaseUtils.calendarToLong(RecordTime, true)
        values.put(ColumnNames[1], recordDate)
        if (ssID < 0) {
            ssID = assignSSID(CustomDatabaseUtils.calendarToLong(RecordTime, true), Duration, Type, Operator)
        }
        values.put(ColumnNames[2], ssID)
        values.put(ColumnNames[3], Duration)
        values.put(ColumnNames[4], Type)
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
                Operator.insert(TableName, null, values)
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