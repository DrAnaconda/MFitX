package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.Math.abs
import java.util.*

object MainRecordsTable {

    const val TableName = "MainRecords"

    var ColumnNames = arrayOf("ID", "Date", "Steps", "Calories")
    var ColumnsForExtraction = arrayOf("Date", "Steps", "Calories")

    var ColumnNamesCloneAdditional = arrayListOf("Analyzed")

    fun getCreateTableCommandClone(): String {
        return "CREATE TABLE if not exists " + TableName + "COPY(" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                ColumnNames[1] + " INTEGER UNIQUE, " +
                ColumnNames[2] + " INTEGER, " +
                ColumnNames[3] + " INTEGER," +
                ColumnNamesCloneAdditional[0] + " INTEGER default 0" +
                ");"
    }

    fun getCreateTableCommand(): String {
        return "create table if not exists " + MainRecordsTable.TableName + "(" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                ColumnNames[1] + " INTEGER UNIQUE, " +
                ColumnNames[2] + " INTEGER, " +
                ColumnNames[3] + " INTEGER" +
                ");"
    }

//region data inserting

    fun getDeltaInMinutes(currentTime: Calendar, Operator: SQLiteDatabase): Long {
        val curs = Operator.query(MainRecordsTable.TableName,
                arrayOf(ColumnNames[1]), null, null, null, null, ColumnNames[1] + " DESC", "1")
        curs.moveToFirst()
        if (curs.count > 0) {
            val nearestCalendar = CustomDatabaseUtils.LongToCalendar(curs.getLong(0), true)
            curs.close()
            val diff: Long = currentTime.timeInMillis - nearestCalendar.timeInMillis
            val seconds = diff / 1000
            val minutes = seconds / 60
            return abs(minutes)
        } else return -1
    }

    private fun checkIsExistsToday(TimeRecord: Calendar, Operator: SQLiteDatabase, Steps: Int, Calories: Int): Long? {
        TimeRecord.set(Calendar.HOUR_OF_DAY, 0)
        TimeRecord.set(Calendar.MINUTE, 0)
        val from = CustomDatabaseUtils.CalendarToLong(TimeRecord, true)
        val to = from + 2359

        val curs = Operator.query(MainRecordsTable.TableName, arrayOf(ColumnNames[1], ColumnNames[2], ColumnNames[3]),
                " " + ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(from.toString(), to.toString()), null, null, null, "1")

        return if (curs.count > 0) {
            curs.moveToFirst()
            if (curs.getLong(1) > Steps)
                throw Exception("Corrupted Data")
            val result = curs.getLong(0); curs.close(); result
        } else null
    }

    fun insertRecordV2(TimeRecord: Calendar, Steps: Int, Calories: Int, Operator: SQLiteDatabase): Long {
        if (CustomDatabaseUtils.CalendarToLong(TimeRecord, false) >
                CustomDatabaseUtils.CalendarToLong(Calendar.getInstance(), true)) return -1
        val Values = ContentValues()
        val recordDate = CustomDatabaseUtils.CalendarToLong(TimeRecord, true)
        Values.put(ColumnNames[1], recordDate)
        Values.put(ColumnNames[2], Steps)
        Values.put(ColumnNames[3], Calories)
        GlobalScope.launch(Dispatchers.IO) {
            HRRecordsTable.updateAnalyticalViaMainInfo(TimeRecord, Steps, Operator)
            writeIntermediate(MainRecord(TimeRecord, Steps, Calories), Operator)
        }
        try {
            val curs = Operator.query(MainRecordsTable.TableName + "COPY", arrayOf(ColumnNames[1]), ColumnNames[1] + " = ?",
                    arrayOf(recordDate.toString()), null, null, null)
            if (curs.count == 0)
                Operator.insert(MainRecordsTable.TableName + "COPY", null, Values)
            curs.close()
        } catch (ex: Exception) {
        }
        return try {
            val checker = checkIsExistsToday(TimeRecord, Operator, Steps, Calories)
            if (checker == null)
                Operator.insert(MainRecordsTable.TableName, null, Values)
            else {
                updateExisting(Values, checker, Operator)
                checker
            }
        } catch (ex: Exception) {
            -1
        }
    }

    private fun updateExisting(values: ContentValues, targetTime: Long, Operator: SQLiteDatabase) {
        Operator.update(MainRecordsTable.TableName, values, " " + ColumnNames[1] + " = ?", arrayOf(targetTime.toString()))
    }

//endregion

    fun extractRecords(From: Long, To: Long, Operator: SQLiteDatabase): Cursor {
        val record = Operator.query(MainRecordsTable.TableName, ColumnsForExtraction,
                ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(From.toString(), To.toString()), null, null, ColumnNames[1])
        record.moveToFirst()
        return record
    }

    fun extractFuncOnIntervalSteps(Where: Long, To: Long, Operator: SQLiteDatabase): Cursor {
        val Record = Operator.query(
                MainRecordsTable.TableName,
                arrayOf("AVG(Steps)", "MIN(Steps)", "MAX(Steps)"),
                ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(Where.toString(), To.toString()), null, null, ColumnNames[1])
        Record.moveToFirst()
        return Record
    }

    fun extractFuncOnIntervalCalories(Where: Long, To: Long, Operator: SQLiteDatabase): Cursor {
        val Record = Operator.query(
                MainRecordsTable.TableName,
                arrayOf("AVG(Calories)", "MIN(Calories)", "MAX(Calories)"),
                ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(Where.toString(), To.toString()), null, null, ColumnNames[1])
        Record.moveToFirst()
        return Record
    }

    fun generateReport(From: Calendar?, To: Calendar?, Operator: SQLiteDatabase, context: Context?): MainReport {
        val from: Long
        val to: Long
        if (From == null || To == null) {
            from = 0
            to = Long.MAX_VALUE
        } else {
            from = CustomDatabaseUtils.CalendarToLong(From, true)
            to = CustomDatabaseUtils.CalendarToLong(To, true)
        }
        val curs = Operator.query(MainRecordsTable.TableName,
                arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*"),
                        CustomDatabaseUtils.niceSQLFunctionBuilder("SUM", ColumnNames[2]),
                        CustomDatabaseUtils.niceSQLFunctionBuilder("SUM", ColumnNames[3])),
                ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(from.toString(), to.toString()), null, null, null)
        curs.moveToFirst()
        val result = MainReport(curs.getInt(1), curs.getInt(2), curs.getInt(0), null, context)
        curs.close()
        return result
    }

//region advanced tracking

    private fun extractFreshRecord(db: SQLiteDatabase): MainRecord {
        val curs = db.query(MainRecordsTable.TableName, ColumnsForExtraction, null, null, null, null, ColumnNames[1] + " desc", "1")
        return if (curs.count > 0) {
            curs.moveToFirst()
            val result = MainRecord(CustomDatabaseUtils.LongToCalendar(curs.getLong(0), true), curs.getInt(1), curs.getInt(2)); curs.close(); result
        } else {
            curs.close()
            val calendar = Calendar.getInstance(); calendar.set(Calendar.HOUR_OF_DAY, 0); MainRecord(calendar, 0, 0)
        }
    }

    private fun writeIntermediate(newData: MainRecord, db: SQLiteDatabase) {
        val freshData = extractFreshRecord(db)
        val deltaMinutes = kotlin.math.abs(Utils.getDeltaCalendar(freshData.RTime, newData.RTime, Calendar.MINUTE))
        val deltaSteps = kotlin.math.abs(freshData.Steps - newData.Steps)
        if (deltaMinutes > 120 && deltaSteps < 10) return
        val speed = if (deltaMinutes.toInt() != 0) deltaSteps.toDouble() / deltaMinutes else deltaSteps.toDouble() / 1
        AdvancedActivityTracker.insertRecord(freshData.RTime, deltaMinutes.toInt(), speed, db)
    }

//endregion

//region optimization algos

    private fun truncateBadData(Operator: SQLiteDatabase) { // TODO in optimizers
        Operator.delete(MainRecordsTable.TableName, " " + ColumnNames[2] + " <= 0", null)
    }

    private fun truncateID(prevId: Int, newID: Int, Operator: SQLiteDatabase) {
        val values = ContentValues()
        values.put(ColumnNames[0], newID)
        Operator.update(MainRecordsTable.TableName,
                values, " " + ColumnNames[0] + " = ?", arrayOf(prevId.toString()))
    }

    private fun getFirstDate(Operator: SQLiteDatabase): Long {
        val curs = Operator.query(MainRecordsTable.TableName, arrayOf(ColumnNames[1]), null, null, null, null, ColumnNames[1], "1")
        return if (curs.count > 0) {
            curs.moveToFirst()
            val result = curs.getLong(0)
            curs.close()
            result
        } else 0
    }

    private fun deleteNotActualData(curs: Cursor, Operator: SQLiteDatabase) {
        if (curs.count <= 1) return
        val idsToDelete: ArrayList<Int> = ArrayList<Int>()
        curs.moveToFirst()
        do {
            idsToDelete.add(curs.getInt(0))
        } while (curs.moveToNext())
        curs.close()
        val prevId = idsToDelete[idsToDelete.size - 1]
        if (idsToDelete.size <= 1) return
        var lower = Int.MAX_VALUE
        idsToDelete.removeAt(idsToDelete.size - 1)
        idsToDelete.forEach {
            if (it < lower) lower = it
            Operator.delete(MainRecordsTable.TableName, " " + ColumnNames[0] + " = ?", arrayOf(it.toString()))


        }

        truncateID(prevId, lower, Operator)
    }

    /* OUTDATED
    fun executeDataCollapse(From: Long, SharedPrefs: SharedPreferences, Operator: SQLiteDatabase) {
        truncateBadData(Operator)
        val lol: Long = 0
        var from = From
        if (from == lol) {
            from = getFirstDate(Operator)
        }
        var protector = 0
        var lockedFrom = from
        do {
            val to = from + 2359 // 10K = one day. WARNING overlapping is possible
            val curs = Operator.query(MainRecordsTable.TableName,
                    arrayOf(ColumnNames[0], ColumnNames[1], ColumnNames[2]), "" + ColumnNames[1] + " BETWEEN ? AND ?",
                    arrayOf(from.toString(), to.toString()), null, null, ColumnNames[0], null)
            deleteNotActualData(curs, Operator)
            if (((from % 1000000) / 10000) != CustomDatabaseUtils.LongToCalendar(to, false).getActualMaximum(Calendar.DAY_OF_MONTH).toLong())
                from += 10000
            else {
                from += 1000000
                from -= (from % 1000000)
                if ((from % 100000000) / 1000000 > 12) {
                    from += 100000000
                    from -= (from % 100000000)
                    from += 1010000
                }
            }
            from -= (from % 10000)
            if (curs.count > 0) {
                protector = 0
                lockedFrom = from
            } else
                protector++
            curs.close()
        } while (protector < 250)
        SharedPrefs.edit().putLong(SharedPrefsMainCollapsedConst, lockedFrom).apply()
    }*/

//endregion

    class MainRecord(val RTime: Calendar, val Steps: Int, val Calories: Int)

    class MainReport(var stepsCount: Int = -1, var caloriesCount: Int = -1, var recordsCount: Int = -1, var analytics: String? = null,
                     private val context: Context?) {
        var passedKm: Float = 0.0f

        init {
            passedKm =
                    if (context != null) Utils.getSharedPrefs(context).getFloat(SettingsActivity.stepsSize, 0.5f) * stepsCount
                    else Utils.SharedPrefs!!.getFloat(SettingsActivity.stepsSize, 0.5f) * stepsCount
        }
    }

    const val SharedPrefsMainCollapsedConst = "LastMainDataCollapsed"
}