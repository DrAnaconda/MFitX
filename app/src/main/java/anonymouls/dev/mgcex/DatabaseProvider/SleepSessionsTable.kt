package anonymouls.dev.mgcex.DatabaseProvider

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.util.*

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
        val records = Operator.query(DatabaseController.SleepSessionTableName, ColumnNames,
                ColumnNames[1] + " BETWEEN ? AND ?", arrayOf(java.lang.Long.toString(SelectArgument), java.lang.Long.toString(EndArgument)), null, null,
                ColumnNames[1] + " DESC")
        records.moveToFirst()
        do {
            Results.add(SleepRecordSession(CustomDatabaseUtils.LongToCalendar(records.getLong(1), false),
                    records.getInt(2), records.getInt(3)))
        } while (records.moveToNext())
        records.close()
        return Results
    }

    fun getLastSyncTime(Operator: SQLiteDatabase): Calendar {
        val record = Operator.query(DatabaseController.SleepSessionTableName, ColumnNames, null, null, null, null, ColumnNames[1] + " DESC", "1")
        record.moveToFirst()
        val result = CustomDatabaseUtils.LongToCalendar(record.getLong(1), false)
        record.close()
        return result
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