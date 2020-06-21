package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.*
import kotlin.collections.ArrayList

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

    private fun fixRecord(record: SleepRecordSession, recordID: Long, db: SQLiteDatabase) {
        val calendar = CustomDatabaseUtils.LongToCalendar(record.TimeRecord, true)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
        val from = CustomDatabaseUtils.CalendarToLong(calendar, true)
        calendar.add(Calendar.HOUR, 10)
        val to = CustomDatabaseUtils.CalendarToLong(calendar, true)
        val curs = db.query(SleepRecordsTable.TableName, SleepRecordsTable.ColumnNames, "DATE BETWEEN ? AND ? AND (IDSS is null or IDSS = -1) ",
                arrayOf(from.toString(), to.toString()), null, null, null)
        val ids = ArrayList<Long>()
        if (curs.count > 0) {
            curs.moveToFirst()
            do {
                val sRecord = SleepRecordsTable.SleepRecord(curs.getLong(1), curs.getInt(3), curs.getInt(4))
                if (sRecord.TypeRecord == 2) {
                    record.DeepDuration += sRecord.Duration
                    HRRecordsTable.updateAnalyticalViaSleepInterval(
                            CustomDatabaseUtils.LongToCalendar(sRecord.RTime, true),
                            CustomDatabaseUtils.LongToCalendar(sRecord.RTime + sRecord.Duration, true),
                            true, db)
                } else {
                    HRRecordsTable.updateAnalyticalViaSleepInterval(
                            CustomDatabaseUtils.LongToCalendar(sRecord.RTime, true),
                            CustomDatabaseUtils.LongToCalendar(sRecord.RTime + sRecord.Duration, true),
                            false, db)
                }
                ids.add(curs.getLong(0))
            } while (curs.moveToNext())
        }
        curs.close()
        val content = ContentValues(); content.put("IDSS", recordID)
        db.update(SleepRecordsTable.TableName, content, "ID in ${CustomDatabaseUtils.listIDsForEnum(ids)}", null)
    }

    fun doubleCheck(db: SQLiteDatabase) {
        val cursor = db.query(TableName, ColumnNames, null, null, null, null, null, "15")
        if (cursor.count > 0) {
            cursor.moveToFirst()
            do {
                val record = SleepRecordSession(cursor.getLong(1), cursor.getInt(2), cursor.getInt(3))
                fixRecord(record, cursor.getLong(0), db)
            } while (cursor.moveToNext())
        }
        cursor.close()
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