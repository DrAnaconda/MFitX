package anonymouls.dev.mgcex.app.data
// TODO Warning. Optimizations needed. Есть костыли, которые фиксят дублирование данных, однако надо вырезать костыли нахрен
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.ApplicationStarter
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.main.ui.main.MainViewModel
import anonymouls.dev.mgcex.databaseProvider.*
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.lang.Math.random
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


@ExperimentalStdlibApi
class DataViewModel : ViewModel() {

    //region Main Properties

    private val loader = CoroutineScope(Job())

    private val _loading = MutableLiveData(View.VISIBLE)
    val loading: LiveData<Int>
        get() = _loading

    private val _result = MutableLiveData<Queue<IExtractable>>()
    val data: LiveData<Queue<IExtractable>>
        get() {
            return _result
        }

    //endregion

    //region Stats Full

    //region Stats Properties

    private lateinit var readableDatabase: SQLiteDatabase
    private lateinit var writableDatabase: SQLiteDatabase
    private var fromLong: Long = -1
    private var toLong: Long = -1
    private var currentLock: Long = 0
    private var staticOffset: Long = 0
    private var grouping = true
    private var dataType: DataFragment.DataTypes = DataFragment.DataTypes.HR
    private var scale: DataFragment.Scalings = DataFragment.Scalings.Day
    var manualHRRequesting = false



    private val _currentProgress = MutableLiveData<String>("")
    val currentProgress: LiveData<String>
        get() {
            return _currentProgress
        }

    private val _infoBlockVisible = MutableLiveData(View.GONE)
    val infoBlockVisible: LiveData<Int>
        get() = _infoBlockVisible

    //endregion

    //region Stats

    private var startValue: Long = 0
    private fun calculatePercentage(currentProgress: Long, endProgress: Long): String {
        val a = currentProgress - startValue
        val b = 100
        val c = endProgress - startValue
        return (b / c.toDouble() * a).toInt().toString()
    }

    private fun convertDateWithScaling(calendar: Calendar): String {
        return when (scale) {
            DataFragment.Scalings.Day -> SimpleDateFormat(Utils.SDFPatterns.DayScaling.pattern, Locale.getDefault()).format(calendar.time)
            DataFragment.Scalings.Week -> SimpleDateFormat(Utils.SDFPatterns.WeekScaling.pattern, Locale.getDefault()).format(calendar.time)
            DataFragment.Scalings.Month -> SimpleDateFormat(Utils.SDFPatterns.MonthScaling.pattern, Locale.getDefault()).format(calendar.time)
        }
    }

    private fun setOffset() {
        staticOffset = when (scale) {
            DataFragment.Scalings.Day -> CustomDatabaseUtils.calculateOffsetValue(5, Calendar.MINUTE, false)
            DataFragment.Scalings.Week -> CustomDatabaseUtils.calculateOffsetValue(7, Calendar.DAY_OF_MONTH, false)
            DataFragment.Scalings.Month -> CustomDatabaseUtils.calculateOffsetValue(1, Calendar.MONTH, false)
        }
    }

    private fun jobFinished(){
        _infoBlockVisible.postValue(View.GONE)
        _loading.postValue(View.GONE)
    }

    private fun fetchDataStageB(activity: Context): kotlinx.coroutines.flow.Flow<Record?> = flow {
        startValue = currentLock
        while (currentLock < toLong) {
            _currentProgress.postValue(activity.getString(R.string.current_progress_date) + " " +
                    convertDateWithScaling(CustomDatabaseUtils.longToCalendar(currentLock, true)) + " — " +
                    convertDateWithScaling(CustomDatabaseUtils.longToCalendar(currentLock + staticOffset, true)) +
                    " (" + calculatePercentage(currentLock, toLong) + "%)")
            var results: Cursor? = null
            when (dataType) {
                DataFragment.DataTypes.HR -> {
                    results = if (grouping)
                        HRRecordsTable.extractFuncOnInterval(currentLock, CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false), readableDatabase)
                    else
                        HRRecordsTable.extractRecords(currentLock, CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false), readableDatabase)
                }
                DataFragment.DataTypes.Calories -> {
                    results = if (grouping)
                        MainRecordsTable.extractFuncOnIntervalCalories(currentLock, CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false), readableDatabase)
                    else
                        MainRecordsTable.extractRecords(currentLock,
                                CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false),
                                readableDatabase, scale)
                }
                DataFragment.DataTypes.Steps -> {
                    results = if (grouping)
                        MainRecordsTable.extractFuncOnIntervalSteps(currentLock,
                                CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false),
                                readableDatabase)
                    else
                        MainRecordsTable.extractRecords(currentLock,
                                CustomDatabaseUtils.sumLongs(currentLock, staticOffset + 1, false),
                                readableDatabase, scale)
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
                jobFinished()
                return@flow
            } finally {
                results?.close()
                currentLock = CustomDatabaseUtils.sumLongs(currentLock, staticOffset, false)

            }
        }
        jobFinished()
    }


    fun fetchDataStageA(From: Calendar, To: Calendar,
                        scale: DataFragment.Scalings, DataType: DataFragment.DataTypes,
                        tableAdapter: DoubleTaskTableViewAdapter, activity: Activity) {
        this.readableDatabase = DatabaseController.getDCObject(activity).readableDatabase
        this.fromLong = CustomDatabaseUtils.calendarToLong(From, true)
        this.toLong = CustomDatabaseUtils.calendarToLong(To, true)
        currentLock = this.fromLong
        this.dataType = DataType
        this.scale = scale
        grouping = scale != DataFragment.Scalings.Day
        _loading.postValue(View.VISIBLE)
        _infoBlockVisible.postValue(View.VISIBLE)
        setOffset()
        val hashCodes = HashSet<String>()
        loader.launch(Dispatchers.Default) {
            fetchDataStageB(activity).collect {
                try {
                    if (it != null) {
                        if (!hashCodes.contains(it.toString())) {
                            activity.runOnUiThread {
                                tableAdapter.addRow(tableAdapter.countRows++,
                                        null, it.getCellsList() as MutableList<Cell?>)
                            }
                        }
                    }
                }catch (e: CancellationException){
                    jobFinished()
                    this.coroutineContext.cancelChildren()
                }
            }
        }
    }

    fun onCancelClick(v: View?) {
        this.toLong = -1
        loader.coroutineContext.cancel(null)
        loader.cancel(null)
        if (manualHRRequesting) {
            fetchCurrentHR(null, true)
            unsubscribe()
        }
    }

//endregion

    //region Manual HR

    private lateinit var observer: androidx.lifecycle.Observer<HRRecord>
    private var firstHR = -2

    // TODO Animation. Progress into check
    private fun initObserver(context: Context) {
        if (this::observer.isInitialized) return
        observer = androidx.lifecycle.Observer<HRRecord> {
            if (firstHR == -2) firstHR = it.hr
            else if (it.hr > 0 && firstHR != -2) {
                _currentProgress.postValue(context.getString(R.string.measuredHR)
                        + it.hr + context.getString(R.string.at)
                        + SimpleDateFormat(Utils.SDFPatterns.DayScaling.pattern, Locale.getDefault()).format(it.recordTime.time))
                val dummy: Queue<IExtractable> = LinkedList<IExtractable>()
                val rec = Record(null, CustomDatabaseUtils.calendarToLong(it.recordTime, true),
                        false, DataFragment.Scalings.Day)
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
        MainViewModel.publicModel?.currentHR?.removeObserver(observer)
    }
    fun fetchCurrentHR(context: AppCompatActivity?, cancel: Boolean = false) {
        if (cancel) {
            manualHRRequesting = false
            CommandInterpreter.getInterpreter(ApplicationStarter.appContext).requestManualHRMeasure(true)
            _loading.postValue(View.GONE)
            _infoBlockVisible.postValue(View.GONE)
        } else if (context != null) {
            initObserver(context)
            if (!CommandInterpreter.getInterpreter(context).hRRealTimeControlSupport) {
                _loading.postValue(View.VISIBLE)
                _infoBlockVisible.postValue(View.VISIBLE)
                _currentProgress.postValue(context.getString(R.string.waiting_for_result_HR))
                CommandInterpreter.getInterpreter(context).requestManualHRMeasure(manualHRRequesting)
                manualHRRequesting = !manualHRRequesting
                MainViewModel.publicModel?.currentHR?.observe(context, observer)
            }
        }
    }

    //endregion

    //endregion

    // region Application Filter Full

    //region Filter Properties

    private val preloaded = ArrayList<ApplicationRow>()
    private var loaded = false

    //endregion

    //region Application filter

    private fun pushArrayToAdapter(context: Activity, adapter: DoubleTaskTableViewAdapter){
        loader.launch {
            while (!loaded)
                continue
            adapter.removeEverything()
            for (x in preloaded) {
                context.runOnUiThread {
                    adapter.addRow(adapter.countRows++,
                            null, x.getCellsList() as MutableList<Cell?>)
                }
            }
        }
    }
    private fun loadIcon(packageManager: PackageManager, pack: ResolveInfo, context: Activity): Drawable {
        return try {
            packageManager.getApplicationIcon(pack.activityInfo.packageName)
        } catch (ex: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                context.getDrawable(android.R.drawable.ic_menu_help)!!
            } else{
                ContextCompat.getDrawable(context, android.R.drawable.ic_menu_help)!!
            }
        }
    }
    private val switchCallback = { v: View, r: Int, c: Int ->
        preloaded[r].updateInfo((v as SwitchCompat).isChecked, this.writableDatabase)
    }
    fun load(context: Activity, adapter: DoubleTaskTableViewAdapter) {
        val reqIntent = Intent(Intent.ACTION_MAIN, null)
        adapter.callback = switchCallback
        reqIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val packList = context.packageManager.queryIntentActivities(reqIntent, 0)
        context.runOnUiThread{ adapter.setColumnHeaderItems(DoubleTaskTableViewAdapter.simpleHeader(3, 
                context.getString(R.string.app_filter)) as List<ColumnHeader?>?)}
        if (!loaded && preloaded.size == 0) {
            loader.launch(Dispatchers.IO) {
                val jobs = ArrayList<Job>()
                for (Pack in packList) {
                    this@DataViewModel.readableDatabase = DatabaseController.getDCObject(context).readableDatabase
                    this@DataViewModel.writableDatabase = DatabaseController.getDCObject(context).writableDatabase
                    jobs.add(launch(Dispatchers.IO) {
                        val ar = ApplicationRow(Pack.activityInfo.packageName,
                                loadIcon(context.packageManager, Pack, context),
                                Pack.loadLabel(context.packageManager).toString(),
                                NotifyFilterTable.isEnabled(Pack.activityInfo.packageName, this@DataViewModel.readableDatabase))
                        preloaded.add(ar)
                        context.runOnUiThread {
                            adapter.addRow(adapter.countRows++, null,
                                    ar.getCellsList() as MutableList<Cell?>)
                        }
                    })
                    jobs.joinAll()
                    loaded = true
                }
            }
        } else if (loaded) pushArrayToAdapter(context, adapter)
    }

    //endregion

    //endregion

}

//region Helper classes

interface IExtractable{
    fun getCellsList(): MutableList<Cell>
}

@ExperimentalStdlibApi
class Record(Result: Cursor?, var recordDate: Long, isGrouping: Boolean, private val scale: DataFragment.Scalings): IExtractable {
    private var whenCalendar: Calendar = Calendar.getInstance()
    var mainValue: Int = -1// AVG + Default
    var minValue: Int = -1
    var maxValue: Int = -1

    private val dateString: String
        get() {
            return when (scale) {
                DataFragment.Scalings.Day -> SimpleDateFormat(Utils.SDFPatterns.DayScaling.pattern, Locale.getDefault()).format(CustomDatabaseUtils.longToCalendar(recordDate, true).time)
                DataFragment.Scalings.Week -> SimpleDateFormat(Utils.SDFPatterns.WeekScaling.pattern, Locale.getDefault()).format(CustomDatabaseUtils.longToCalendar(recordDate, true).time)
                DataFragment.Scalings.Month -> SimpleDateFormat(Utils.SDFPatterns.WeekScaling.pattern, Locale.getDefault()).format(CustomDatabaseUtils.longToCalendar(recordDate, true).time)
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

    override fun getCellsList(): MutableList<Cell> {
        val size = if (maxValue > 0) 4 else 2
        val result = MutableList<Cell>(size) {
            TextCell("")
        }
        result[0] = (TextCell(dateString))
        if (minValue > 0) result[1] = (TextCell(minValue.toString()))
        if (minValue > 0) result[2] = (TextCell(mainValue.toString())) else result[1] = TextCell(mainValue.toString())
        if (maxValue > 0) result[3] = TextCell(maxValue.toString())
        return result
    }

    override fun toString(): String {
        return dateString + mainValue.toString()
    }

    companion object {
        fun getTestRecord(): Record {
            val result = Record(null, 202007070707, false, DataFragment.Scalings.Day)
            result.mainValue = random().toInt() % 1000
            result.whenCalendar = Calendar.getInstance(); result.whenCalendar.add(Calendar.MINUTE, -1 * (random().toInt() % 10000))
            return result
        }
    }
}

//endregion