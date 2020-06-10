package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.app.AlarmProvider

object AlarmsTable {
    //                                                  0    1       2          3       4
    val ColumnsNames = arrayOf("ID", "Hour", "Minute", "Days", "Enabled", "HourStart", "MinStart", "syncable")

    fun GetCreateTableCommand(): String {
        return ("CREATE TABLE if not exists " + DatabaseController.AlarmsTableName + " (" +
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
        val request = Operator.query(DatabaseController.AlarmsTableName, arrayOf(ColumnsNames[0]), null, null, null, null, ColumnsNames[0] + " DESC", null)
        request.moveToFirst()
        return if (request.count < 1) {
            request.close()
            1
        } else {
            val result = request.getLong(0) + 1
            request.close()
            result
        }
    }

    fun insertRecord(AP: AlarmProvider, Operator: SQLiteDatabase): Long {
        val Values = ContentValues()
        val DefID: Long = -1
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
        val record = Operator.query(DatabaseController.AlarmsTableName, ColumnsNames, null, null, null, null, ColumnsNames[0])
        record.moveToFirst()
        return record
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
}