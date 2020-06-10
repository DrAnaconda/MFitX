package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.util.Utils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

object SleepRecordsTable {
    const val TableName = "SleepRecords"
    val ColumnNames = arrayOf("ID", "Timestamp", "IDSS", "Duration", "Type")


    fun getCreateTableCommand(): String {
        return "create table if not exists " + TableName + "(" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnNames[1] + " INTEGER UNIQUE," +
                ColumnNames[2] + " INTEGER null," +
                ColumnNames[3] + " INTEGER," +
                ColumnNames[4] + " INTEGER," +
                "FOREIGN KEY (" + ColumnNames[2] + ") REFERENCES " + SleepSessionsTable.TableName + " (" + SleepSessionsTable.ColumnNames[0] + ") ON DELETE SET NULL" +
                ");"
    }

    fun insertRecord(RecordTime: Calendar, SSID: Long, Duration: Int, Type: Int, Operator: SQLiteDatabase): Long {
        val Values = ContentValues()
        val recordDate = CustomDatabaseUtils.CalendarToLong(RecordTime, true)
        Values.put(ColumnNames[1], recordDate)
        if (SSID > 0) Values.put(ColumnNames[2], SSID)
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

    class SleepRecord(private val RTime: Calendar, private val Duration: Int, private val TypeRecord: Int) {
        companion object {
            private val DeepSleepType: Short = 0
            private val LightSleepType: Short = 1
            private val AwakeType: Short = 2
        }
    }


    private fun setHRActivity(from: Long, duration: Int, db: SQLiteDatabase) {
        val calendarFrom = CustomDatabaseUtils.LongToCalendar(from, true)
        val calendarTo = CustomDatabaseUtils.LongToCalendar(from, true); calendarTo.add(Calendar.MINUTE, duration)
        HRRecordsTable.updateAnalyticalViaSleepInterval(calendarFrom, calendarTo, db)
    }

    private fun checkIsNext(timeLongA: Long, currentTime: Long): Boolean {
        val calendarA = CustomDatabaseUtils.LongToCalendar(timeLongA, true)
        val calendarB = CustomDatabaseUtils.LongToCalendar(currentTime, true)
        val delta = abs(Utils.getDeltaCalendar(calendarA, calendarB, Calendar.HOUR))
        return delta >= 8
    }

    private fun pushSession(idsList: ArrayList<Long>, durability: Int, deepDurability: Int, startTime: Long, db: SQLiteDatabase) {
        val sessionID = SleepSessionsTable.insertRecord(startTime, durability, deepDurability, db)
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
            GlobalSettings.isLaunched = true
            curs.moveToFirst()
            while (curs.getInt(4) != 1) curs.moveToNext()
            lockedFirstTime = curs.getLong(1)
            do {
                when (curs.getInt(4)) {
                    2 -> {
                        setHRActivity(curs.getLong(1), curs.getInt(3), db)
                        overallDeepDuration += curs.getInt(3)
                    }
                    1 -> {
                        if (!checkIsNext(lockedFirstTime, curs.getLong(1))) {
                            idsToNext.add(curs.getLong(0))
                        } else {
                            pushSession(idsToNext, overallDuration, overallDeepDuration, lockedFirstTime, db)
                            if (!GlobalSettings.ignoreLightSleepData) {
                                val endCalendar = CustomDatabaseUtils.LongToCalendar(lockedFirstTime, true)
                                endCalendar.add(Calendar.MINUTE, overallDuration)
                                setHRActivity(lockedFirstTime, overallDuration, db)
                                AdvancedActivityTracker.optimizeBySleepRange(lockedFirstTime, CustomDatabaseUtils.CalendarToLong(endCalendar, true), db)
                            } else {
                                // TODO What to do next?
                            }
                            overallDeepDuration = 0; overallDuration = 0; idsToNext.clear(); lockedFirstTime = curs.getLong(1)
                        }
                    }
                }
                overallDuration += curs.getInt(3)
                idsToNext.add(curs.getLong(0))
                if (iterator++ >= curs.count - 1) pushSession(idsToNext, overallDuration, overallDeepDuration, lockedFirstTime, db)

            } while (curs.moveToNext())
        }
        curs.close()
        GlobalSettings.isLaunched = false
    }


    object GlobalSettings {
        var ignoreLightSleepData = false
        var isLaunched = false
    }
}


// Works fine
