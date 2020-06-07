package anonymouls.dev.mgcex.app.Backend

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.DatabaseProvider.*
import anonymouls.dev.mgcex.app.DeviceControllerActivity
import java.util.*

internal class CommandCallbacks(context: Context) : CommandInterpreter.CommandReaction {

    private val database: SQLiteDatabase = DatabaseController.getDCObject(context).currentDataBase!!
    private var lastSyncMain: Long = -1
    private var lastSyncHR: Long = -1

    override fun MainInfo(Steps: Int, Calories: Int) {
        try {
            lastSyncMain = CustomDatabaseUtils.CalendarToLong(Calendar.getInstance(), true)
            Algorithm.LastStepsIncomed = Steps
            Algorithm.LastCcalsIncomed = Calories
            MainRecordsTable.insertRecordV2(Calendar.getInstance(), Steps, Calories, database)
        } catch (Ex: Exception) {

        }
    }

    override fun BatteryInfo(Charge: Int) {
        try {
            if (Charge > 0)
                Algorithm.BatteryHolder = Charge
            // TODO Power consumption and battery health
        } catch (Ex: Exception) {

        }
    }

    override fun HRIncome(Time: Calendar, HRValue: Int) {
        var ResultHR = HRValue
        if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
        //if (Algorithm.IsAlarmWaiting) Algorithm.SelfPointer?.alarmTriggerDecider(ResultHR)
        if (lastSyncHR <= CustomDatabaseUtils.CalendarToLong(Time, true)) {
            Algorithm.LastHearthRateIncomed = ResultHR
            lastSyncHR = CustomDatabaseUtils.CalendarToLong(Time, true)
        }
    }

    override fun HRHistoryRecord(Time: Calendar, HRValue: Int) {
        if (Time.time > Calendar.getInstance().time) return
        try {
            var ResultHR = HRValue
            if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
            HRRecordsTable.insertRecord(Time, ResultHR, database)
            if (lastSyncHR < CustomDatabaseUtils.CalendarToLong(Time, true) && DeviceControllerActivity.isFirstLaunch) {
                Algorithm.LastHearthRateIncomed = ResultHR
                lastSyncHR = CustomDatabaseUtils.CalendarToLong(Time, true)
            }

            //if (Algorithm.IsAlarmWaiting) Algorithm.SelfPointer?.alarmTriggerDecider(ResultHR)
        } catch (Ex: Exception) {

        }
    }

    override fun MainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int) {
        if (Time.time > Calendar.getInstance().time) return
        if (Steps <= 0 || Calories <= 0) return
        try {
            if (MainRecordsTable.insertRecordV2(Time, Steps, Calories, database) > 0) {
                val current = CustomDatabaseUtils.CalendarToLong(Time, true)
                if (current > lastSyncMain) {
                    Algorithm.LastCcalsIncomed = Calories
                    Algorithm.LastStepsIncomed = Steps
                    lastSyncMain = CustomDatabaseUtils.CalendarToLong(Time, true)
                }
            }
        } catch (Ex: Exception) {

        }

    }

    override fun SleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int) {
        try {
            var newDuration = Duration
            if (newDuration < 0) newDuration = (newDuration and 0xFF)
            SleepRecordsTable.insertRecord(Time, -1, newDuration, Type, database)
        } catch (ex: Exception) {

        }
    }


    companion object {
        lateinit var SelfPointer: CommandCallbacks
    }
}