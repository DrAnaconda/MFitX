package anonymouls.dev.mgcex.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.main.DeviceControllerViewModel
import anonymouls.dev.mgcex.databaseProvider.*
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.lang.Math.random
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet


@ExperimentalStdlibApi
class DataViewModel : ViewModel() {

    private lateinit var database: SQLiteDatabase
    private var fromLong: Long = -1
    private var toLong: Long = -1
    private var currentLock: Long = 0
    private var staticOffset: Long = 0
    private var grouping = true
    private var dataType: DataView.DataTypes = DataView.DataTypes.HR
    private var scale: DataView.Scalings = DataView.Scalings.Day
    var manualHRRequesting = false
    var cancelled = false

    private val _loading = MutableLiveData(View.VISIBLE)
    val loading: LiveData<Int>
        get() = _loading

    private val _result = MutableLiveData<Queue<Record>>()
    val data: LiveData<Queue<Record>>
        get() {
            return _result
        }

    private val _currentProgress = MutableLiveData<String>("")
    val currentProgress: LiveData<String>
        get() {
            return _currentProgress
        }

    private val _infoBlockVisible = MutableLiveData(View.GONE)
    val infoBlockVisible: LiveData<Int>
        get() = _infoBlockVisible

//region Stats

    private var startValue: Long = 0
    private fun calculatePercentage(currentProgress: Long, endProgress: Long): String {
        val a = currentProgress - startValue
        val b = 100
        val c = endProgress - startValue
        return (b / c.toDouble() * a).toInt().toString()
    }

    private fun convertDateWithScaling(calendar: Calendar, scale: DataView.Scalings): String {
        return when (scale) {
            DataView.Scalings.Day -> SimpleDateFormat(Utils.SDFPatterns.DayScaling.pattern, Locale.getDefault()).format(calendar.time)
            DataView.Scalings.Week -> SimpleDateFormat(Utils.SDFPatterns.WeekScaling.pattern, Locale.getDefault()).format(calendar.time)
            DataView.Scalings.Month -> SimpleDateFormat(Utils.SDFPatterns.MonthScaling.pattern, Locale.getDefault()).format(calendar.time)
        }
    }

    private fun setOffset() {
        staticOffset = when (scale) {
            DataView.Scalings.Day -> CustomDatabaseUtils.calculateOffsetValue(5, Calendar.MINUTE, false)
            DataView.Scalings.Week -> CustomDatabaseUtils.calculateOffsetValue(7, Calendar.DAY_OF_MONTH, false)
            DataView.Scalings.Month -> CustomDatabaseUtils.calculateOffsetValue(1, Calendar.MONTH, false)
        }
    }

    private fun fullCancel() {
        _infoBlockVisible.postValue(View.GONE)
        _loading.postValue(View.GONE)
    }

    private fun fetchDataStageB(activity: Context): kotlinx.coroutines.flow.Flow<Record?> = flow {
        startValue = currentLock
        while (currentLock < toLong) {
            _currentProgress.postValue(activity.getString(R.string.current_progress_date) + " " +
                    convertDateWithScaling(CustomDatabaseUtils.longToCalendar(currentLock, true), scale) + " — " +
                    convertDateWithScaling(CustomDatabaseUtils.longToCalendar(currentLock + staticOffset, true), scale) + " (" + calculatePercentage(currentLock, toLong) + "%)")

            if (cancelled) {
                fullCancel(); return@flow
            }
            var results: Cursor? = null
            when (dataType) {
                DataView.DataTypes.HR -> {
                    results = if (grouping)
                        HRRecordsTable.extractFuncOnInterval(currentLock, CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false), database)
                    else
                        HRRecordsTable.extractRecords(currentLock, CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false), database)
                }
                DataView.DataTypes.Calories -> {
                    results = if (grouping)
                        MainRecordsTable.extractFuncOnIntervalCalories(currentLock, CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false), database)
                    else
                        MainRecordsTable.extractRecords(currentLock,
                                CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false),
                                database, scale)
                }
                DataView.DataTypes.Steps -> {
                    results = if (grouping)
                        MainRecordsTable.extractFuncOnIntervalSteps(currentLock,
                                CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false),
                                database)
                    else
                        MainRecordsTable.extractRecords(currentLock,
                                CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false),
                                database, scale)
                }
            }
            try {
                if (results != null && results.count > 0) {
                    val rec = Record(results, currentLock, grouping, scale)
                    if (rec.mainValue > 0) {
                        emit(rec)
                    }
                    results.close(); }
            } catch (E: Exception) {
                Log.e("fetchDataStageB", E.toString() + ":" + E.message)
            } catch (e: CancellationException) {
                fullCancel()
                return@flow
            } finally {
                results?.close()
                currentLock = CustomDatabaseUtils.sumLongs(currentLock, staticOffset, false)
            }
        }
        fullCancel()
    }


    fun fetchDataStageA(context: Context, From: Calendar, To: Calendar,
                        scale: DataView.Scalings, DataType: DataView.DataTypes) {
        this.cancelled = false
        this.database = DatabaseController.getDCObject(context).readableDatabase
        this.fromLong = CustomDatabaseUtils.calendarToLong(From, true)
        this.toLong = CustomDatabaseUtils.calendarToLong(To, true)
        currentLock = this.fromLong
        this.dataType = DataType
        this.scale = scale
        grouping = scale != DataView.Scalings.Day
        _loading.postValue(View.VISIBLE)
        _infoBlockVisible.postValue(View.VISIBLE)
        setOffset()
        val hashCodes = HashSet<String>()
        val inputQuene: Queue<Record> = LinkedList<Record>()
        viewModelScope.launch(Dispatchers.IO) {
            fetchDataStageB(context).collect {
                if (it != null) {
                    if (!hashCodes.contains(it.toString())) {
                        hashCodes.add(it.toString())
                        inputQuene.add(it)
                        _result.postValue(inputQuene)
                    }
                }
            }
        }
    }

//endregion

    private lateinit var observer: androidx.lifecycle.Observer<HRRecord>
    private var firstHR = -2

    // TODO SDF to consts
// TODO Animation. Progress into check
    private fun initObserver(context: AppCompatActivity) {
        if (this::observer.isInitialized) return
        observer = androidx.lifecycle.Observer<HRRecord> {
            if (firstHR == -2) firstHR = it.hr
            else if (it.hr > 0 && firstHR != -2) {
                _currentProgress.postValue(context.getString(R.string.measuredHR)
                        + it.hr + context.getString(R.string.at)
                        + SimpleDateFormat(Utils.SDFPatterns.DayScaling.pattern, Locale.getDefault()).format(it.recordTime.time))
                val dummy: Queue<Record> = LinkedList<Record>()
                val rec = Record(null, CustomDatabaseUtils.calendarToLong(it.recordTime, true),
                        false, DataView.Scalings.Day)
                rec.mainValue = it.hr
                dummy.add(rec)
                _result.postValue(dummy)
                _loading.postValue(View.GONE)
                _infoBlockVisible.postValue(View.VISIBLE)
                unsubscribe()
            }
        }
    }

    private fun unsubscribe() {
        DeviceControllerViewModel.instance?.currentHR?.removeObserver(observer)
    }


    fun fetchCurrentHR(context: AppCompatActivity, cancel: Boolean = false) {
        initObserver(context)
        if (cancel) {
            manualHRRequesting = false
            CommandInterpreter.getInterpreter(context).requestManualHRMeasure(true)
            _loading.postValue(View.GONE)
            _infoBlockVisible.postValue(View.GONE)
        } else {

            if (!CommandInterpreter.getInterpreter(context).hRRealTimeControlSupport) {
                _loading.postValue(View.VISIBLE)
                _infoBlockVisible.postValue(View.VISIBLE)
                _currentProgress.postValue(context.getString(R.string.waiting_for_result_HR))
                CommandInterpreter.getInterpreter(context).requestManualHRMeasure(manualHRRequesting)
                manualHRRequesting = !manualHRRequesting
                DeviceControllerViewModel.instance?.currentHR?.observe(context, observer)
            }
        }
    }
}

@ExperimentalStdlibApi
class Record(Result: Cursor?, var recordDate: Long, isGrouping: Boolean, private val scale: DataView.Scalings) {
    private var whenCalendar: Calendar = Calendar.getInstance()
    var mainValue: Int = -1// AVG + Default
    var minValue: Int = -1
    var maxValue: Int = -1

    private val dateString: String
        get() {
            return when (scale) {
                DataView.Scalings.Day -> SimpleDateFormat(Utils.SDFPatterns.DayScaling.pattern, Locale.getDefault()).format(CustomDatabaseUtils.longToCalendar(recordDate, true).time)
                DataView.Scalings.Week -> SimpleDateFormat(Utils.SDFPatterns.WeekScaling.pattern, Locale.getDefault()).format(CustomDatabaseUtils.longToCalendar(recordDate, true).time)
                DataView.Scalings.Month -> SimpleDateFormat(Utils.SDFPatterns.WeekScaling.pattern, Locale.getDefault()).format(CustomDatabaseUtils.longToCalendar(recordDate, true).time)
            }
        }


    init {
        if (Result != null && recordDate > 0) {
            whenCalendar = CustomDatabaseUtils.longToCalendar(recordDate, true)
            if (isGrouping) {
                mainValue = Result.getInt(0)//AVG
                minValue = Result.getInt(1)//MIN
                maxValue = Result.getInt(2)//MAX
            } else {
                recordDate = Result.getLong(0)
                whenCalendar = CustomDatabaseUtils.longToCalendar(recordDate, true)
                mainValue = Result.getInt(1)
            }
        }
    }

    fun getCellsList(): MutableList<Cell> {
        val size = if (maxValue > 0) 4 else 2
        val result = MutableList<Cell>(size) {
            Cell("")
        }
        result[0] = (Cell(dateString))
        if (minValue > 0) result[1] = (Cell(minValue.toString()))
        if (minValue > 0) result[2] = (Cell(mainValue.toString())) else result[1] = Cell(mainValue.toString())
        if (maxValue > 0) result[3] = Cell(maxValue.toString())
        return result
    }

    override fun toString(): String {
        return dateString + mainValue.toString()
    }

    companion object {
        fun getTestRecord(): Record {
            val result = Record(null, 202007070707, false, DataView.Scalings.Day)
            result.mainValue = random().toInt() % 1000
            result.whenCalendar = Calendar.getInstance(); result.whenCalendar.add(Calendar.MINUTE, -1 * (random().toInt() % 10000))
            return result
        }
    }
}
