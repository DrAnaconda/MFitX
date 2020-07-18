package anonymouls.dev.mgcex.app.backend

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import anonymouls.dev.mgcex.app.main.DeviceControllerViewModel
import anonymouls.dev.mgcex.databaseProvider.*
import java.util.*

@ExperimentalStdlibApi
class CommandCallbacks(context: Context) : CommandInterpreter.CommandReaction {

    private val database: SQLiteDatabase = DatabaseController.getDCObject(context).writableDatabase
    private var lastSyncMain: Long = -1
    private var lastSyncHR: Long = -1

    override fun mainInfo(Steps: Int, Calories: Int) {
        try {
            lastSyncMain = CustomDatabaseUtils.calendarToLong(Calendar.getInstance(), true)
            DeviceControllerViewModel.instance?._lastStepsIncomed?.postValue(Steps); SavedValues.savedSteps = Steps
            DeviceControllerViewModel.instance?._lastCcalsIncomed?.postValue(Calories); SavedValues.savedCCals = Calories
            MainRecordsTable.insertRecordV2(Calendar.getInstance(), Steps, Calories, database)
        } catch (Ex: Exception) {

        }
    }

    override fun batteryInfo(Charge: Int) {
        try {
            if (Charge > 5) {
                DeviceControllerViewModel.instance?._batteryHolder?.postValue(Charge)
                SavedValues.savedBattery = Charge
            }
            // TODO Power consumption and battery health
        } catch (Ex: Exception) {

        }
    }

    override fun hrIncome(Time: Calendar, HRValue: Int) {
        if (Time.time > Calendar.getInstance().time) return
        var ResultHR = HRValue
        if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
        if (lastSyncHR <= CustomDatabaseUtils.calendarToLong(Time, true)) {
            val record = HRRecord(Time, ResultHR)
            DeviceControllerViewModel.instance?._lastHearthRateIncomed?.postValue(record); SavedValues.savedHR = record
            lastSyncHR = CustomDatabaseUtils.calendarToLong(Time, true)
        }
    }

    override fun hrHistoryRecord(Time: Calendar, HRValue: Int) {
        if (Time.time > Calendar.getInstance().time) return
        try {
            var ResultHR = HRValue
            if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
            HRRecordsTable.insertRecord(Time, ResultHR, database)
            if (lastSyncHR < CustomDatabaseUtils.calendarToLong(Time, true)) {
                val record = HRRecord(Time, ResultHR)
                DeviceControllerViewModel.instance?._lastHearthRateIncomed?.postValue(record); SavedValues.savedHR = record
                lastSyncHR = CustomDatabaseUtils.calendarToLong(Time, true)
            }
        } catch (Ex: Exception) {

        }
    }

    override fun mainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int) {
        if (Time.time > Calendar.getInstance().time) return
        if (Steps < 0 || Calories < 0) return
        try {
            if (MainRecordsTable.insertRecordV2(Time, Steps, Calories, database) > 0) {
                val current = CustomDatabaseUtils.calendarToLong(Time, true)
                if (current > lastSyncMain) {
                    DeviceControllerViewModel.instance?._lastCcalsIncomed?.postValue(Calories); SavedValues.savedCCals = Calories
                    DeviceControllerViewModel.instance?._lastStepsIncomed?.postValue(Steps); SavedValues.savedSteps = Steps
                    lastSyncMain = CustomDatabaseUtils.calendarToLong(Time, true)
                }
            }
        } catch (Ex: Exception) {

        }

    }

    override fun sleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int) {
        if (Time.time > Calendar.getInstance().time) return
        try {
            var newDuration = Duration
            if (newDuration < 0) newDuration = (newDuration and 0xFF)
            SleepRecordsTable.insertRecord(Time, -1, newDuration, Type, database)
        } catch (ex: Exception) {

        }
    }


    companion object {

        lateinit var SelfPointer: CommandCallbacks

        object SavedValues {

            var savedCCals = -1
            var savedSteps = -1
            var savedHR: HRRecord = HRRecord(Calendar.getInstance(), -20)
            var savedBattery = -1
            var savedStatus = ""
        }
    }
}