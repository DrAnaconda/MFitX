package anonymouls.dev.MGCEX.DatabaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.MGCEX.App.AlarmProvider
import java.util.*

object AlarmsTable {
    //                                                  0    1       2          3       4
    val ColumnsNames = arrayOf("ID", "Hour", "Minute", "Days", "Enabled", "HourStart", "MinStart", "syncable")

    fun GetCreateTableCommand(): String {
        return ("CREATE TABLE " + DatabaseController.AlarmsTableName + " (" +
                ColumnsNames[0] + " INTEGER PRIMARY KEY," +
                ColumnsNames[1] + " INTEGER," +
                ColumnsNames[2] + " INTEGER," +
                ColumnsNames[3] + " INTEGER," +
                ColumnsNames[4] + " BOOLEAN," +
                ColumnsNames[5] + " INTEGER," +
                ColumnsNames[6] + " INTEGER," +
                ColumnsNames[7] + " BOOLEAN"
                + ");")
    }

    private fun getLastID(Operator: SQLiteDatabase): Long {
        val Request = Operator.query(DatabaseController.AlarmsTableName, arrayOf(ColumnsNames[0]), null, null, null, null, ColumnsNames[0] + " DESC", null)
        Request.moveToFirst()
        return if (Request.count < 1)
            1
        else
            Request.getLong(0) + 1
    }

    fun insertRecord(AP: AlarmProvider, Operator: SQLiteDatabase): Long {
        val Values = ContentValues()
        val DefID : Long = -1
        if (AP.ID == DefID) AP.ID = getLastID(Operator)
        Values.put(ColumnsNames[0], AP.ID)
        Values.put(ColumnsNames[1], AP.Hour)
        Values.put(ColumnsNames[2], AP.Minute)
        Values.put(ColumnsNames[3], AP.DayMask)
        Values.put(ColumnsNames[4], AP.IsEnabled)
        Values.put(ColumnsNames[5], AP.HourStart)
        Values.put(ColumnsNames[6], AP.MinuteStart)
        return Operator.insert(DatabaseController.AlarmsTableName, null, Values)
    }

    fun extractRecords(Operator: SQLiteDatabase): Cursor {
        val Record = Operator.query(DatabaseController.AlarmsTableName, ColumnsNames, null, null, null, null, ColumnsNames[0])
        Record.moveToFirst()
        return Record
    }

    fun updateRecord(AP: AlarmProvider, Operator: SQLiteDatabase) {
        val Values = ContentValues()
        Values.put(ColumnsNames[1], AP.Hour)
        Values.put(ColumnsNames[2], AP.Minute)
        Values.put(ColumnsNames[3], AP.DayMask)
        Values.put(ColumnsNames[4], AP.IsEnabled)
        Values.put(ColumnsNames[5], AP.HourStart)
        Values.put(ColumnsNames[6], AP.MinuteStart)
        Operator.update(DatabaseController.AlarmsTableName, Values, "ID=" + java.lang.Long.toString(AP.ID), null)
    }

    fun deleteRecord(AP: AlarmProvider, Operator: SQLiteDatabase) {
        Operator.delete(DatabaseController.AlarmsTableName, "ID=" + java.lang.Long.toString(AP.ID), null)
    }

    fun getApproachingAlarm(): Cursor {
        val Operator = SQLiteDatabase.openDatabase(DatabaseController.DatabaseName, null, 0)
        val Now = Calendar.getInstance()
        val Days = AlarmProvider.DaysMasks.GetMask(Now.get(Calendar.DAY_OF_WEEK))
        val Hour = Now.get(Calendar.HOUR_OF_DAY)
        val Minute = Now.get(Calendar.MINUTE)
        val Result = Operator.query(DatabaseController.AlarmsTableName,
                ColumnsNames, "(Hour >= ? OR (Hour = ? AND Minute>=?)) AND Enabled = 1 AND Days >= ?",
                arrayOf(Integer.toString(Hour), Integer.toString(Hour), Integer.toString(Minute), Integer.toString(Days)), null, null,
                ColumnsNames[5] + " ASC, " + ColumnsNames[6] + " ASC", null)
        Result.moveToFirst()
        return Result
    }
}