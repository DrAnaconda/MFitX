package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.util.Utils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

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
        return CustomDatabaseUtils.CalendarToLong(result, true)
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
        val recordDate = CustomDatabaseUtils.CalendarToLong(RecordTime, true)
        Values.put(ColumnNames[1], recordDate)
        if (SSID < 1) {
            SSID = assignSSID(CustomDatabaseUtils.CalendarToLong(RecordTime, true), Duration, Type, Operator)
        }
        Values.put(ColumnNames[2], SSID)
        Values.put(ColumnNames[3], Duration)
        Values.put(ColumnNames[4], Type)
        return try {
            val curs = Operator.query(TableName, arrayOf(ColumnNames[1]), ColumnNames[1] + " = ?", arrayOf(recordDate.toString()),
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

    private fun setHRActivity(from: Long, duration: Int, isDeep: Boolean, db: SQLiteDatabase) {
        val calendarFrom = CustomDatabaseUtils.LongToCalendar(from, true)
        val calendarTo = CustomDatabaseUtils.LongToCalendar(from, true); calendarTo.add(Calendar.MINUTE, duration)
        HRRecordsTable.updateAnalyticalViaSleepInterval(calendarFrom, calendarTo, isDeep, db)
    }

    private fun checkIsNext(timeLongA: Long, currentTime: Long): Boolean {
        val calendarA = CustomDatabaseUtils.LongToCalendar(timeLongA, true)
        val calendarB = CustomDatabaseUtils.LongToCalendar(currentTime, true)
        val delta = abs(Utils.getDeltaCalendar(calendarA, calendarB, Calendar.HOUR))
        return delta >= 8
    }

    private fun pushSession(idsList: ArrayList<Long>, startTime: Long, db: SQLiteDatabase) {
        val sessionID = SleepSessionsTable.insertRecord(startTime, db)
        val content = ContentValues(); content.put(ColumnNames[2], sessionID)
        val paramString = CustomDatabaseUtils.listIDsForEnum(idsList)
        db.update(TableName, content, "(" + ColumnNames[2] + " = -1 or " + ColumnNames[2] + " is null) and " + ColumnNames[0] + " in " + paramString,
                null)
        idsList.clear()
    }

    fun executeAnalyze(db: SQLiteDatabase) {
        val curs = db.query(TableName, ColumnNames, ColumnNames[2] + " is null or " + ColumnNames[2] + " = -1",
                null, null, null, ColumnNames[1], null)

        val idsToNext = ArrayList<Long>()
        var overallDuration = 0
        var overallDeepDuration = 0
        var lockedFirstTime: Long = 0
        var iterator = 0

        if (curs.count > 0) {
            GlobalSettings.isLaunched.postValue(true)
            curs.moveToFirst()
            while (curs.getInt(4) != 1) curs.moveToNext()
            lockedFirstTime = curs.getLong(1)
            do {
                when (curs.getInt(4)) {
                    2 -> {
                        setHRActivity(curs.getLong(1), curs.getInt(3), true, db)
                        overallDeepDuration += curs.getInt(3)
                        AdvancedActivityTracker.optimizeBySleepRange(lockedFirstTime, curs.getLong(1) + curs.getInt(3).toLong(), db)
                    }
                    1 -> {
                        if (!checkIsNext(lockedFirstTime, curs.getLong(1))) {
                            idsToNext.add(curs.getLong(0))
                        } else {
                            pushSession(idsToNext, lockedFirstTime, db)
                            if (!GlobalSettings.ignoreLightSleepData) {
                                val endCalendar = CustomDatabaseUtils.LongToCalendar(lockedFirstTime, true)
                                endCalendar.add(Calendar.MINUTE, overallDuration)
                                setHRActivity(lockedFirstTime, overallDuration, false, db)
                                AdvancedActivityTracker.optimizeBySleepRange(lockedFirstTime, CustomDatabaseUtils.CalendarToLong(endCalendar, true), db)
                            }
                            overallDeepDuration = 0; overallDuration = 0; idsToNext.clear(); lockedFirstTime = curs.getLong(1)
                        }
                    }
                }
                overallDuration += curs.getInt(3)
                idsToNext.add(curs.getLong(0))
                if (iterator++ >= curs.count - 1) pushSession(idsToNext, lockedFirstTime, db)

            } while (curs.moveToNext())
        }
        curs.close()
        GlobalSettings.isLaunched.postValue(false)
    }

    class SleepRecord(var RTime: Long, var Duration: Int, var TypeRecord: Int) {
        enum class RecordTypes(val code: Int) { Deep(2), LightOrOverall(1) }

    }

    object GlobalSettings {
        var ignoreLightSleepData = false
        var isLaunched = MutableLiveData<Boolean>(false)
    }
}