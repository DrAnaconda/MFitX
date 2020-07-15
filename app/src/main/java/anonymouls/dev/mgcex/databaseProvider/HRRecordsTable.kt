package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.util.HRAnalyzer
import java.util.*

object HRRecordsTable {

    enum class AnalyticTypes(val type: Byte) {
        Unknown(0), Steady(1), PhysicalStress(2),
        LowPhysical(3), MediumPhysical(4), SleepingLight(7), Sleeping(8)
    }

    const val TableName = "HRRecords"

    val ColumnsNames = arrayOf("ID", "Date", "HRValue", "AnalyticType")
    val ColumnsForExtraction = arrayOf("Date", "HRValue")

    fun getCreateTableCommand(): String {
        return ("CREATE TABLE if not exists $TableName (" +
                ColumnsNames[0] + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ColumnsNames[1] + " INTEGER UNIQUE," +
                ColumnsNames[2] + " INTEGER," +
                ColumnsNames[3] + " BYTE DEFAULT 0"
                + ");")
    }

//region data insertion and updating

    private fun isAlreadyExistsV2(Target: Calendar, Operator: SQLiteDatabase): Long? {
        val DT = CustomDatabaseUtils.calendarToLong(Target, true)
        val data = Operator.query(TableName, arrayOf(ColumnsNames[0]),
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
        Operator.update(TableName, values, " " + ColumnsNames[0] + " = ?", arrayOf(ID.toString()))
    }

    fun insertRecord(RecordTime: Calendar, HRValue: Int, Operator: SQLiteDatabase): Long {
        val values = ContentValues()
        values.put(ColumnsNames[1], CustomDatabaseUtils.calendarToLong(RecordTime, true))
        values.put(ColumnsNames[2], HRValue)
        val checkID = isAlreadyExistsV2(RecordTime, Operator)
        if (checkID == null)
            return Operator.insert(TableName, null, values)
        else {
            updateRecord(checkID, values, Operator)
            return checkID
        }
    }


    fun updateAnalyticalViaMainInfo(deltaMin: Int, stepsMin: Double, from: Long,
                                    db: SQLiteDatabase) {
        val calendarTo = CustomDatabaseUtils.longToCalendar(from, true)
        calendarTo.add(Calendar.MINUTE, deltaMin)
        val to = CustomDatabaseUtils.calendarToLong(calendarTo, true) + 1
        val arctificalCoeff = 1 + (deltaMin - 5) * ((3 - 1) / (60 - 5))
        val mutatedSteps = stepsMin * arctificalCoeff
        val content = ContentValues()
        content.put(ColumnsNames[3], HRAnalyzer.physicalStressDetermining(mutatedSteps).type)
        db.update(TableName, content, ColumnsNames[1] + " BETWEEN ? AND ?",
                arrayOf((from - 1).toString(), to.toString()))
    }

    fun updateAnalyticalViaSleepInterval(From: Calendar, To: Calendar, isDeep: Boolean, Operator: SQLiteDatabase) {
        val from = CustomDatabaseUtils.calendarToLong(From, true)
        val to = CustomDatabaseUtils.calendarToLong(To, true)
        val values = ContentValues()
        if (isDeep)
            values.put(ColumnsNames[3], AnalyticTypes.Sleeping.type)
        else
            values.put(ColumnsNames[3], AnalyticTypes.SleepingLight.type)
        Operator.update(TableName, values,
                ColumnsNames[1] + " BETWEEN ? AND ? AND AnalyticType < 8",
                arrayOf(from.toString(), to.toString()))
    }

//endregion

    /* region data extraction */
    fun getOverallAverage(Operator: SQLiteDatabase): Int {
        val curs = Operator.query(TableName,
                arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("AVG", ColumnsNames[2])), null, null, null, null, null)
        curs.moveToFirst()
        val result = curs.getInt(0)
        curs.close()
        return result
    }

    fun extractRecords(From: Long, To: Long, Operator: SQLiteDatabase): Cursor? {
        try {
            val record = Operator.query(TableName, ColumnsForExtraction,
                    ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(java.lang.Long.toString(From), java.lang.Long.toString(To)), null, null, ColumnsNames[1])
            record.moveToFirst()
            return record
        } catch (ex: Exception) {
            ex.hashCode()
        }
        return null
    }

    fun extractFuncOnInterval(Where: Long, To: Long, Operator: SQLiteDatabase): Cursor {
        val record = Operator.query(TableName, arrayOf("AVG(HRValue)", "MIN(HRValue)", "MAX(HRValue)"),
                ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(java.lang.Long.toString(Where), java.lang.Long.toString(To)), null, null, ColumnsNames[1])
        record.moveToFirst()
        return record
    }
//endregion

    fun generateReport(From: Calendar?, To: Calendar?, Operator: SQLiteDatabase): HRReport {
        val from: Long
        val to: Long
        if (From == null || To == null) {
            from = 0
            to = Long.MAX_VALUE
        } else {
            from = CustomDatabaseUtils.calendarToLong(From, true)
            to = CustomDatabaseUtils.calendarToLong(To, true)
        }
        val curs = Operator.query(TableName,
                arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*"), //0
                        CustomDatabaseUtils.niceSQLFunctionBuilder("AVG", ColumnsNames[2]),//1
                        CustomDatabaseUtils.niceSQLFunctionBuilder("MIN", ColumnsNames[2]),//2
                        CustomDatabaseUtils.niceSQLFunctionBuilder("MAX", ColumnsNames[2])),//3
                ColumnsNames[1] + " BETWEEN ? AND ?", arrayOf(from.toString(), to.toString()), null, null, null)
        curs.moveToFirst()
        val result = HRReport(curs.getInt(2), curs.getInt(1), curs.getInt(3), curs.getInt(0), countAnomalies(From, To, curs.getInt(1), Operator))
        curs.close()
        return result
    }

    private fun countAnomalies(From: Calendar?, To: Calendar?, avgHR: Int, Operator: SQLiteDatabase): Int {
        val from: Long
        val to: Long
        if (From == null || To == null) {
            from = 0
            to = Long.MAX_VALUE
        } else {
            from = CustomDatabaseUtils.calendarToLong(From, true)
            to = CustomDatabaseUtils.calendarToLong(To, true)
        }
        //val lowerDelta = avgHR-(avgHR*0.2f)
        val upperDelta = avgHR + (avgHR * 0.2f)
        val curs = Operator.query(TableName, arrayOf(CustomDatabaseUtils.niceSQLFunctionBuilder("COUNT", "*")),
                ColumnsNames[1] + " BETWEEN ? AND ? AND (" + ColumnsNames[2] + " > ?)",
                arrayOf(from.toString(), to.toString(), upperDelta.toInt().toString()), null, null, null)
        curs.moveToFirst()
        val result = curs.getInt(0); curs.close()
        return result
    }

    class HRReport(val MinHR: Int, val AvgHR: Int, val MaxHR: Int, val recordsCount: Int, anomaliesReport: Int) {
        var anomaliesPercent: Int = -1

        init {
            anomaliesPercent = ((anomaliesReport.toFloat() / recordsCount.toFloat()) * 100.0f).toInt()
        }
    }
}

class HRRecord(val recordTime: Calendar, val hr: Int)