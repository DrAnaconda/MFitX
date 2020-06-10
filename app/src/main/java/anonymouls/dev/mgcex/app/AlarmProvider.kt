package anonymouls.dev.mgcex.app

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.databaseProvider.AlarmsTable
import java.util.*

class AlarmProvider(var Hour: Int, var Minute: Int, var DayMask: Int, ID: Long, var IsEnabled: Boolean,
                    var HourStart: Int, var MinuteStart: Int, var IsSyncable: Boolean) {

    var ID: Long = -1
    var IsPassed: Boolean? = null

    private var lastSyncable: Boolean? = null

    init {
        if (ID > -1) this.ID = ID
        lastSyncable = IsSyncable
    }

    private fun performInsertOperation(Operator: SQLiteDatabase): Long {
        AlarmsTable.insertRecord(this, Operator)
        return ID
    }

    private fun performUpdateInformation(Operator: SQLiteDatabase) {
        AlarmsTable.updateRecord(this, Operator)
    }

    fun saveAlarmRecord(Operator: SQLiteDatabase): Long {
        val DefID: Long = -1
        if (this.ID == DefID)
            performInsertOperation(Operator)
        else
            performUpdateInformation(Operator)
        if (IsSyncable != lastSyncable)
            Algorithm.postCommand(CommandInterpreter.SetAlarm(ID, IsEnabled, Hour, Minute, DayMask), false)
        return ID
    }

    fun commitSuicide(Operator: SQLiteDatabase) {
        AlarmsTable.deleteRecord(this, Operator)
    }

    object DaysMasks {
        private const val Monday = 0x01.toChar()
        private const val Tuesday = 0x02.toChar()
        private const val Wednesday = 0x04.toChar()
        private const val Thursday = 0x08.toChar()
        private const val Friday = 0x10.toChar()
        private const val Saturday = 0x20.toChar()
        private const val Sunday = 0x40.toChar()

        fun GetMask(DayNumber: Int): Int {
            when (DayNumber) {
                Calendar.MONDAY -> return Monday.toInt()
                Calendar.TUESDAY -> return Tuesday.toInt()
                Calendar.WEDNESDAY -> return Wednesday.toInt()
                Calendar.THURSDAY -> return Thursday.toInt()
                Calendar.FRIDAY -> return Friday.toInt()
                Calendar.SATURDAY -> return Saturday.toInt()
                Calendar.SUNDAY -> return Sunday.toInt()
            }
            return 128
        }
    }

    companion object {

        fun isAlarmsEqual(A1: AlarmProvider?, A2: AlarmProvider?): Boolean {
            if (A1 == null || A2 == null) return false
            if (A1.MinuteStart != A2.MinuteStart) return false
            if (A1.HourStart != A2.HourStart) return false
            if (A1.Hour != A2.Hour) return false
            if (A1.Minute != A2.Minute) return false
            return A1.DayMask == A2.DayMask
        }

        fun setDayMask(IsMonday: Boolean, IsTuesday: Boolean, IsWednesday: Boolean,
                       IsThursday: Boolean, IsFriday: Boolean, IsSaturday: Boolean, IsSunday: Boolean): Int {
            var dayMask = 0
            if (IsMonday) dayMask += 1
            if (IsTuesday) dayMask += 2
            if (IsWednesday) dayMask += 4
            if (IsThursday) dayMask += 8
            if (IsFriday) dayMask += 16
            if (IsSaturday) dayMask += 32
            if (IsSunday) dayMask += 64
            if (dayMask == 0) dayMask = 128
            return dayMask
        }

        fun loadFromCursor(Record: Cursor): AlarmProvider {
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
