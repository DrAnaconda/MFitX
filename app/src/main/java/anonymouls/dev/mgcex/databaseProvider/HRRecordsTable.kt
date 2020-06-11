package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.util.HRAnalyzer
import java.util.*
import kotlin.math.abs

object HRRecordsTable {

    enum class AnalyticTypes(val type: Byte) { Unknown(0), Steady(1), PhysicalStress(2), LowPhysical(3), MediumPhysical(4), Sleeping(8) }

    val ColumnsNames = arrayOf("ID", "Date", "HRValue", "AnalyticType")
    val ColumnsForExtraction = arrayOf("Date", "HRValue")

    fun getCreateTableCommand(): String {
        return ("CREATE TABLE if not exists " + DatabaseController.HRRecordsTableName + " (" +
                ColumnsNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnsNames[1] + " INTEGER UNIQUE," +
                ColumnsNames[2] + " INTEGER," +
                ColumnsNames[3] + " BYTE DEFAULT 0"
                + ");")
    }

//region data insertion and updating

    private fun isAlreadyExistsV2(Target: Calendar, Operator: SQLiteDatabase): Long? {
        val DT = CustomDatabaseUtils.CalendarToLong(Target, true)
        val data = Operator.query(DatabaseController.HRRecordsTableName, arrayOf(ColumnsNames[0]),
                ColumnsNames[1] + " = ?", arrayOf(DT.toString()), null, null, null, "1")
        data.moveToFirst()
        return if (data.count > 0) {
            val result = data.getLong(0)
            data.close()
            result
        } else {
            data.close()
            null
        }
    }

    private fun updateRecord(ID: Long, values: ContentValues, Operator: SQLiteDatabase) {
        Operator.update(DatabaseController.HRRecordsTableName, values, " " + ColumnsNames[0] + " = ?", arrayOf(ID.toString()))
    }

    fun insertRecord(RecordTime: Calendar, HRValue: Int, Operator: SQLiteDatabase): Long {
        val values = ContentValues()
        values.put(ColumnsNames[1], CustomDatabaseUtils.CalendarToLong(RecordTime, true))
        values.put(ColumnsNames[2], HRValue)
        val checkID = isAlreadyExistsV2(RecordTime, Operator)
        if (checkID == null)
            return Operator.insert(DatabaseController.HRRecordsTableName, null, values)
        else {
            updateRecord(checkID, values, Operator)
            return checkID
        }
    }


    fun updateAnalyticalViaMainInfo(RecordTime: Calendar, Steps: Int, Operator: SQLiteDatabase) {
        if (abs((RecordTime.timeInMillis - Calendar.getInstance().timeInMillis) / 1000 / 60) > 30) return
        val from = CustomDatabaseUtils.CalendarToLong(RecordTime, true) - 10
        val to = from + 20
        val curs = Operator.query(MainRecordsTable.TableName, arrayOf(MainRecordsTable.ColumnNames[2]), " " + MainRecordsTable.ColumnNames[1] + " BETWEEN ? AND ?",
                arrayOf(from.toString(), to.toString()), null, null, null, "1")
        if (curs.count > 0) {
            curs.moveToFirst()
            val values = ContentValues()
            val deltaInMinutes = MainRecordsTable.getDeltaInMinutes(RecordTime, Operator).toInt()
            if (deltaInMinutes > 250 || deltaInMinutes <= -1) return
            val type = HRAnalyzer.physicalStressDetermining(abs(curs.getInt(0) - Steps), deltaInMinutes); curs.close()
            values.put(ColumnsNames[3], AnalyticTypes.valueOf(type.name).type)
            Operator.update(DatabaseController.HRRecordsTableName, values, " " + ColumnsNames[1] + " BETWEEN ? AND ? AND " + ColumnsNames[3] + " = 0",
                    arrayOf(from.toString(), to.toString()))
        }
    }

    fun updateAnalyticalViaSleepInterval(From: Calendar, To: Calendar, Operator: SQLiteDatabase) {
        val from = CustomDatabaseUtils.CalendarToLong(From, true)
        val to = CustomDatabaseUtils.CalendarToLong(To, true)
        val values = ContentValues()
        values.put(ColumnsNames[3], AnalyticTypes.Sleeping.type)
        Operator.update(DatabaseController.HRRecordsTableName, values, " " + ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(from.toString(), to.toString()))
    }

//endregion

    /* region data extraction */
    fun getOverallAverage(Operator: SQLiteDatabase): Int {
        val curs = Operator.query(DatabaseController.HRRecordsTableName,
                arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("AVG", ColumnsNames[2])), null, null, null, null, null)
        curs.moveToFirst()
        return curs.getInt(0)
    }

    fun extractRecords(From: Long, To: Long, Operator: SQLiteDatabase): Cursor? {
        try {
            val record = Operator.query(DatabaseController.HRRecordsTableName, ColumnsForExtraction,
                    ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(java.lang.Long.toString(From), java.lang.Long.toString(To)), null, null, ColumnsNames[1])
            record.moveToFirst()
            return record
        } catch (ex: Exception) {
            ex.hashCode()
        }
        return null
    }

    fun extractFuncOnInterval(Where: Long, To: Long, Operator: SQLiteDatabase): Cursor {
        val record = Operator.query(DatabaseController.HRRecordsTableName, arrayOf("AVG(HRValue)", "MIN(HRValue)", "MAX(HRValue)"),
                ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(java.lang.Long.toString(Where), java.lang.Long.toString(To)), null, null, ColumnsNames[1])
        record.moveToFirst()
        return record
    }
//endregion

    fun generateReport(From: Calendar?, To: Calendar?, Operator: SQLiteDatabase): HRReport {
        var from: Long
        var to: Long
        if (From == null || To == null) {
            from = 0
            to = Long.MAX_VALUE
        } else {
            from = CustomDatabaseUtils.CalendarToLong(From, true)
            to = CustomDatabaseUtils.CalendarToLong(To, true)
        }
        val curs = Operator.query(DatabaseController.HRRecordsTableName,
                arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*"), //0
                        CustomDatabaseUtils.niceSQLFunctionBuilder("AVG", ColumnsNames[2]),//1
                        CustomDatabaseUtils.niceSQLFunctionBuilder("MIN", ColumnsNames[2]),//2
                        CustomDatabaseUtils.niceSQLFunctionBuilder("MAX", ColumnsNames[2])),//3
                ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(from.toString(), to.toString()), null, null, null)
        curs.moveToFirst()
        return HRReport(curs.getInt(2), curs.getInt(1), curs.getInt(3), curs.getInt(0), countAnomalies(From, To, curs.getInt(1), Operator))
    }

    private fun countAnomalies(From: Calendar?, To: Calendar?, avgHR: Int, Operator: SQLiteDatabase): Int {
        var from: Long
        var to: Long
        if (From == null || To == null) {
            from = 0
            to = Long.MAX_VALUE
        } else {
            from = CustomDatabaseUtils.CalendarToLong(From, true)
            to = CustomDatabaseUtils.CalendarToLong(To, true)
        }
        //val lowerDelta = avgHR-(avgHR*0.2f)
        val upperDelta = avgHR + (avgHR * 0.2f)
        var curs = Operator.query(DatabaseController.HRRecordsTableName, arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*")),
                ColumnsNames[1] + " BETWEEN ? AND ? AND (" + ColumnsNames[2] + " > ?)",
                arrayOf(from.toString(), to.toString(), upperDelta.toInt().toString()), null, null, null)
        curs.moveToFirst()
        return curs.getInt(0)
    }

    class HRReport(val MinHR: Int, val AvgHR: Int, val MaxHR: Int, val recordsCount: Int, anomaliesReport: Int) {
        var anomaliesPercent: Int = -1

        init {
            anomaliesPercent = ((anomaliesReport.toFloat() / recordsCount.toFloat()) * 100.0f).toInt()
        }
    }
}