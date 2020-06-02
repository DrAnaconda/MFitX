package anonymouls.dev.MGCEX.App

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

import java.util.Calendar

import anonymouls.dev.MGCEX.DatabaseProvider.AlarmsTable

class AlarmProvider(var Hour: Int, var Minute: Int, var DayMask: Int, ID: Long, var IsEnabled: Boolean,
                    var HourStart: Int, var MinuteStart: Int, var IsSyncable: Boolean) {

    var ID: Long = -1
    var IsPassed: Boolean = false

    init {
        if (ID > -1) this.ID = ID
    }

    object DaysMasks {
        const val Monday: Char = 0x01.toChar()
        const val Tuesday: Char = 0x02.toChar()
        const val Wednesday: Char = 0x04.toChar()
        const val Thursday: Char = 0x08.toChar()
        const val Friday: Char = 0x10.toChar()
        const val Saturday: Char = 0x20.toChar()
        const val Sunday: Char = 0x40.toChar()

        fun GetMask(DayNumber: Int): Int {
            when (DayNumber) {
                Calendar.MONDAY -> return DaysMasks.Monday.toInt()
                Calendar.TUESDAY -> return DaysMasks.Tuesday.toInt()
                Calendar.WEDNESDAY -> return DaysMasks.Wednesday.toInt()
                Calendar.THURSDAY -> return DaysMasks.Thursday.toInt()
                Calendar.FRIDAY -> return DaysMasks.Friday.toInt()
                Calendar.SATURDAY -> return DaysMasks.Saturday.toInt()
                Calendar.SUNDAY -> return DaysMasks.Sunday.toInt()
            }
            return 128
        }
    }

    private fun PerformInsertOperation(Operator: SQLiteDatabase): Long {
        AlarmsTable.InsertRecord(this, Operator)
        return ID
    }

    private fun PerformUpdateInformation(Operator: SQLiteDatabase) {
        AlarmsTable.UpdateRecord(this, Operator)
    }

    fun SaveAlarmRecord(Operator: SQLiteDatabase): Long {
        val DefID: Long = -1
        if (this.ID == DefID)
            PerformInsertOperation(Operator)
        else
            PerformUpdateInformation(Operator)
        if (IsSyncable)
            Algorithm.postCommand(CommandInterpreter.SetAlarm(ID, IsEnabled, Hour, Minute, DayMask),false)
        else
            Algorithm.postCommand(CommandInterpreter.SetAlarm(ID, false, Hour, Minute, DayMask),false)
        return ID
    }

    fun CommitSuicide(Operator: SQLiteDatabase) {
        AlarmsTable.DeleteRecord(this, Operator)
    }

    companion object {

        fun IsAlarmsEqual(A1: AlarmProvider?, A2: AlarmProvider?): Boolean {
            if (A1 == null || A2 == null) return false
            if (A1.MinuteStart != A2.MinuteStart) return false
            if (A1.HourStart != A2.HourStart) return false
            if (A1.Hour != A2.Hour) return false
            if (A1.Minute != A2.Minute) return false
            return if (A1.DayMask != A2.DayMask) false else true
        }

        fun SetDayMask(IsMonday: Boolean, IsTuesday: Boolean, IsWednesday: Boolean,
                       IsThursday: Boolean, IsFriday: Boolean, IsSaturday: Boolean, IsSunday: Boolean): Int {
            var DayMask = 0
            if (IsMonday) DayMask += 1
            if (IsTuesday) DayMask += 2
            if (IsWednesday) DayMask += 4
            if (IsThursday) DayMask += 8
            if (IsFriday) DayMask += 16
            if (IsSaturday) DayMask += 32
            if (IsSunday) DayMask += 64
            if (DayMask == 0) DayMask = 128
            return DayMask
        }

        fun LoadFromCursor(Record: Cursor): AlarmProvider {
            val Hour = Record.getInt(1)
            val Minute = Record.getInt(2)
            val ID = Record.getInt(0)
            val Days = Record.getInt(3)
            var Bool = Record.getInt(4)
            val StartHour = Record.getInt(5)
            val StartMinute = Record.getInt(6)
            var IsEnabled = true
            var IsSyncable = false
            if (Bool == 0) IsEnabled = !IsEnabled
            Bool = Record.getInt(7)
            if (Bool == 1) IsSyncable = !IsSyncable
            return AlarmProvider(Hour, Minute, Days, ID.toLong(), IsEnabled, StartHour, StartMinute, IsSyncable)
        }
    }
}
