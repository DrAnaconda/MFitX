package anonymouls.dev.mgcex.databaseProvider

import android.database.sqlite.SQLiteDatabase
import java.util.*
import kotlin.collections.ArrayList

object CustomDatabaseUtils {

    private fun CheckForLength(Target: Calendar, FieldID: Int): String {
        val ArrayCompensation = if (FieldID != Calendar.MONTH) 0 else 1
        val Result = Integer.toString(Target.get(FieldID) + ArrayCompensation)
        return if (Target.get(FieldID) + ArrayCompensation < 10) "0$Result" else Result

    }

    fun CalendarToLong(Target: Calendar, IsTimeDetailsNeeded: Boolean): Long {
        var Buff = ""
        Buff += Integer.toString(Target.get(Calendar.YEAR))
        Buff += CheckForLength(Target, Calendar.MONTH)
        Buff += CheckForLength(Target, Calendar.DAY_OF_MONTH)
        if (IsTimeDetailsNeeded) {
            Buff += CheckForLength(Target, Calendar.HOUR_OF_DAY)
            Buff += CheckForLength(Target, Calendar.MINUTE)
        }
        return java.lang.Long.parseLong(Buff)
    }

    fun LongToCalendar(Target: Long, IsTimeDetailsNeeded: Boolean): Calendar {
        val Result = Calendar.getInstance()
        val LongTarget = java.lang.Long.toString(Target).toCharArray()
        val Year = Integer.parseInt(String(LongTarget, 0, 4))
        val Month = Integer.parseInt(String(LongTarget, 4, 2)) - 1
        val Day = Integer.parseInt(String(LongTarget, 6, 2))
        var Hour = 0
        var Minute = 0
        if (IsTimeDetailsNeeded) {
            Hour = Integer.parseInt(String(LongTarget, 8, 2))
            Minute = Integer.parseInt(String(LongTarget, 10, 2))
        }
        Result.set(Year, Month, Day, Hour, Minute, 0)
        Result.set(Calendar.HOUR_OF_DAY, Hour)
        return Result
    }

    fun CalculateOffsetValue(StandardValue: Long, FieldType: Int, IsShort: Boolean): Long {
        val Result: Long
        when (FieldType) {
            Calendar.YEAR -> Result = if (IsShort) StandardValue * 10000 else StandardValue * 100000000
            Calendar.MONTH -> Result = if (IsShort) StandardValue * 100 else StandardValue * 1000000
            Calendar.DAY_OF_MONTH -> Result = if (IsShort) StandardValue else StandardValue * 10000
            Calendar.HOUR_OF_DAY -> Result = StandardValue * 100
            Calendar.MINUTE -> Result = StandardValue
            else -> Result = -1
        }
        return Result
    }

    fun SumLongs(A: Long, B: Long, IsShort: Boolean): Long {
        val Sum = A + B
        val SumStr = java.lang.Long.toString(Sum)
        var Year = Integer.parseInt(SumStr.substring(0, 4))
        var Month = Integer.parseInt(SumStr.substring(4, 6))
        var Day = Integer.parseInt(SumStr.substring(6, 8))
        var Hour = if (!IsShort) Integer.parseInt(SumStr.substring(8, 10)) else 0
        var Minute = if (!IsShort) Integer.parseInt(SumStr.substring(10, 12)) else 0
        while (Minute > 59) {
            Hour++
            Minute -= 60
        }
        while (Hour > 23) {
            Day++
            Hour -= 24
        }
        while (Day > 31) {//TODO is okay for february?
            Month++
            Day -= 30
        }
        while (Month > 12) {
            Year++
            Month -= 11
        }
        var Result = Year.toString()
        Result += if (Month > 9) Month.toString() else "0$Month"
        Result += if (Day > 9) Day.toString() else "0$Day"
        Result += if (Hour > 9) Hour.toString() else "0$Hour"
        Result += if (Minute > 9) Minute.toString() else "0$Minute"
        return java.lang.Long.parseLong(Result)
    }

    fun GetLastSyncFromTable(TableName: String, ReqColumns: Array<String>, IsShort: Boolean, Operator: SQLiteDatabase): Calendar {
        val record = Operator.query(TableName, ReqColumns, null, null, null, null,
                ReqColumns[1] + " DESC", "1")
        record.moveToFirst()
        if (record.count < 1) {
            val Buff = Calendar.getInstance()
            Buff.add(Calendar.MONTH, -5)
            return Buff
        }
        val result = LongToCalendar(record.getLong(1), IsShort)
        record.close()
        return result

    }

    fun niceSQLFunctionBuilder(function: String, param: String): String {
        return "$function($param)"
    }

    fun <T> listIDsForEnum(list: ArrayList<T>): String {
        var result = ""
        for (x in list.indices) {
            result += list[x].toString()
            if (x == list.size - 1) break else result += ','
        }
        return "($result)"
    }
}
