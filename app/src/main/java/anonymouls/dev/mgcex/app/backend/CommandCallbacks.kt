package anonymouls.dev.mgcex.app.backend

import android.content.Context
import anonymouls.dev.mgcex.app.main.ui.main.MainViewModel
import anonymouls.dev.mgcex.databaseProvider.*
import java.util.*

@ExperimentalStdlibApi
open class CommandCallbacks(private val context: Context) : CommandInterpreter.CommandReaction {

    private var lastSyncMain: Long = -1
    private var lastSyncHR: Long = -1

    var savedCCals = 0
    var savedSteps = 0
    var savedHR: HRRecord = HRRecord(Calendar.getInstance(), -20)
    var savedBattery = 0
    var savedStatus = ""

    override fun mainInfo(Steps: Int, Calories: Int) {
        try {
            lastSyncMain = CustomDatabaseUtils.calendarToLong(Calendar.getInstance(), true)
            MainViewModel.publicModel?._lastStepsIncomed?.postValue(Steps); savedSteps = Steps
            MainViewModel.publicModel?._lastCcalsIncomed?.postValue(Calories); savedCCals = Calories
            MainViewModel.publicModel?.mainInfo(savedSteps, savedCCals)
            MainRecordsTable.insertRecordV2(Calendar.getInstance(), Steps, Calories,
                    DatabaseController.getDCObject(context).writableDatabase)
        } catch (Ex: Exception) {

        }
    }

    override fun batteryInfo(Charge: Int) {
        try {
            if (Charge > 5) {
                MainViewModel.publicModel?._batteryHolder?.postValue(Charge)
                savedBattery = Charge
            }
            // TODO Power consumption and battery health
        } catch (Ex: Exception) {

        }
    }

    override fun hrIncome(Time: Calendar, HRValue: Int) {
        if (Time.time > Calendar.getInstance().time) return
        var ResultHR = HRValue
        if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
        if (ResultHR > 220 || ResultHR < 6) return
        if (lastSyncHR <= CustomDatabaseUtils.calendarToLong(Time, true)) {
            val record = HRRecord(Time, ResultHR)
            savedHR = record; MainViewModel.publicModel?._lastHearthRateIncomed?.postValue(record)
            lastSyncHR = CustomDatabaseUtils.calendarToLong(Time, true)
            MainViewModel.publicModel?.hrIncome(Time, HRValue)
        }
    }

    override fun hrHistoryRecord(Time: Calendar, HRValue: Int) {
        if (Time.time > Calendar.getInstance().time) return
        try {
            var ResultHR = HRValue
            if (ResultHR < 0) ResultHR = (ResultHR and 0xFF)
            HRRecordsTable.insertRecord(Time, ResultHR, DatabaseController.getDCObject(context).writableDatabase)
            if (lastSyncHR < CustomDatabaseUtils.calendarToLong(Time, true)) {
                val record = HRRecord(Time, ResultHR)
                savedHR = record
                MainViewModel.publicModel?._lastHearthRateIncomed?.postValue(record)
                lastSyncHR = CustomDatabaseUtils.calendarToLong(Time, true)
                MainViewModel.publicModel?.hrHistoryRecord(Time, HRValue)
            }
        } catch (Ex: Exception) {

        }
    }

    override fun mainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int) {
        if (Time.time > Calendar.getInstance().time) return
        if (Steps < 0 || Calories < 0) return
        try {
            if (MainRecordsTable.insertRecordV2(Time, Steps, Calories, DatabaseController.getDCObject(context).writableDatabase) > 0) {
                val current = CustomDatabaseUtils.calendarToLong(Time, true)
                if (current > lastSyncMain) {
                    savedCCals = Calories; MainViewModel.publicModel?._lastCcalsIncomed?.postValue(Calories)
                    savedSteps = Steps; MainViewModel.publicModel?._lastStepsIncomed?.postValue(Steps)
                    lastSyncMain = CustomDatabaseUtils.calendarToLong(Time, true)
                    MainViewModel.publicModel?.mainHistoryRecord(Time, Steps, Calories)
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
            SleepRecordsTable.insertRecord(Time, -1, newDuration, Type, DatabaseController.getDCObject(context).writableDatabase)
        } catch (ex: Exception) {

        }
    }


    companion object {

        private lateinit var SelfPointer: CommandCallbacks

        fun getCallback(context: Context): CommandCallbacks {
            if (!this::SelfPointer.isInitialized)
                SelfPointer = CommandCallbacks(context)
            return SelfPointer
        }
    }
}