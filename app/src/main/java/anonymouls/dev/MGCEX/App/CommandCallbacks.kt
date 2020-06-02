package anonymouls.dev.MGCEX.App

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.MGCEX.DatabaseProvider.*
import java.util.*

internal class CommandCallbacks(context: Context) : CommandInterpreter.CommandReaction {

    private val database: SQLiteDatabase = DatabaseController.getDCObject(context).currentDataBase!!
    private var lastSyncMain: Long = -1
    private var lastSyncHR: Long = -1

    override fun MainInfo(Steps: Int, Calories: Int) {
        try {
            MainRecordsTable.insertRecordV2(Calendar.getInstance(), Steps, Calories, database)
            lastSyncMain = CustomDatabaseUtils.CalendarToLong(Calendar.getInstance(), true)
            Algorithm.LastStepsIncomed = Steps
            Algorithm.LastCcalsIncomed = Calories
        } catch (Ex: Exception) {

        }
    }

    override fun BatteryInfo(Charge: Int) {
        try {
            Algorithm.BatteryHolder = Charge
            // TODO Power consumption and battery health
        } catch (Ex: Exception) {

        }
    }

    override fun HRIncome(Time: Calendar, HRValue: Int) {
        var ResultHR = HRValue
        if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
        //if (Algorithm.IsAlarmWaiting) Algorithm.SelfPointer?.alarmTriggerDecider(ResultHR)
        if (lastSyncHR < CustomDatabaseUtils.CalendarToLong(Time, true) && DeviceControllerActivity.isFirstLaunch) {
            Algorithm.LastHearthRateIncomed = ResultHR
            lastSyncHR = CustomDatabaseUtils.CalendarToLong(Time, true)
        }
    }

    override fun HRHistoryRecord(Time: Calendar, HRValue: Int) {
        try {
            var ResultHR = HRValue
            if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
            HRRecordsTable.insertRecord(Time, ResultHR, database)
            if (lastSyncHR < CustomDatabaseUtils.CalendarToLong(Time,true) && DeviceControllerActivity.isFirstLaunch) {
                Algorithm.LastHearthRateIncomed = ResultHR
                lastSyncHR = CustomDatabaseUtils.CalendarToLong(Time, true)
            }

            //if (Algorithm.IsAlarmWaiting) Algorithm.SelfPointer?.alarmTriggerDecider(ResultHR)
        } catch (Ex: Exception) {

        }
    }

    override fun MainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int) {
        if (Steps <= 0 || Calories <= 0) return
        try {
            if (MainRecordsTable.insertRecordV2(Time, Steps, Calories, database)>0) {
                if (CustomDatabaseUtils.CalendarToLong(Time,true) > lastSyncMain) {
                    Algorithm.LastCcalsIncomed = Calories
                    Algorithm.LastStepsIncomed = Steps
                    lastSyncMain = CustomDatabaseUtils.CalendarToLong(Time, true)
                }
            }
        } catch (Ex: Exception) {

        }

    }

    override fun SleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int) {
        try{
            var Duration = Duration
            if (Duration < 0) Duration = (Duration and 0xFF)
            SleepRecordsTable.InsertRecord(Time, -1, Duration, Type, database)
        }catch (ex: Exception){

        }
    }


    companion object {
        lateinit var SelfPointer: CommandCallbacks
    }
}