package anonymouls.dev.MGCEX.DatabaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.MGCEX.DatabaseProvider.SleepSessionsTable.ColumnNames

import java.util.ArrayList
import java.util.Calendar

object SleepSessionsTable {
    val ColumnNames = arrayOf("ID", "Date", "Durability", "DeepDurability")

    fun GetCreateTableCommand(): String {
        return "CREATE TABLE " + DatabaseController.SleepSessionTableName + " (" +
                ColumnNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnNames[1] + " TEXT UNIQUE," +
                ColumnNames[2] + " INTEGER DEFAULT -1," +
                ColumnNames[3] + " INTEGER DEFAULT -1" +
                ");"
    }

    fun InsertRecord(RecordTime: Calendar, DurabilityMinutes: Int, DeepDurability: Int, Operator: SQLiteDatabase): Long {
        val Values = ContentValues()
        Values.put(ColumnNames[1], CustomDatabaseUtils.CalendarToLong(RecordTime, false))
        if (DurabilityMinutes != -1) Values.put(ColumnNames[2], DurabilityMinutes)
        if (DeepDurability != -1) Values.put(ColumnNames[3], DeepDurability)
        return Operator.insert(DatabaseController.SleepSessionTableName, null, Values)
    }

    fun ExtractValues(Argument: Calendar, OffsetArg: Long, Operator: SQLiteDatabase): List<SleepRecordSession> {
        val Results = ArrayList<SleepRecordSession>()
        val SelectArgument = CustomDatabaseUtils.CalendarToLong(Argument, false)
        val EndArgument = SelectArgument + OffsetArg
        val Records = Operator.query(DatabaseController.SleepSessionTableName, ColumnNames,
                ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(java.lang.Long.toString(SelectArgument), java.lang.Long.toString(EndArgument)), null, null,
                ColumnNames[1] + " DESC")
        Records.moveToFirst()
        do {
            Results.add(SleepRecordSession(CustomDatabaseUtils.LongToCalendar(Records.getLong(1), false),
                    Records.getInt(2), Records.getInt(3)))
        } while (Records.moveToNext())
        return Results
    }

    fun GetLastSyncTime(Operator: SQLiteDatabase): Calendar {
        val Record = Operator.query(DatabaseController.SleepSessionTableName, ColumnNames, null, null, null, null, ColumnNames[1] + " DESC", "1")
        Record.moveToFirst()
        return CustomDatabaseUtils.LongToCalendar(Record.getLong(1), false)
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