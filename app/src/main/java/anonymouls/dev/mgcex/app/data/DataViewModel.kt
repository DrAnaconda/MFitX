package anonymouls.dev.mgcex.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.databaseProvider.CustomDatabaseUtils
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.HRRecordsTable
import anonymouls.dev.mgcex.databaseProvider.MainRecordsTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.lang.Math.random
import java.text.SimpleDateFormat
import java.util.*

class DataViewModel : ViewModel() {

    private lateinit var database: SQLiteDatabase
    private var fromLong: Long = -1
    private var toLong: Long = -1
    private var currentLock: Long = 0
    private var staticOffset: Long = 0
    private var grouping = true
    private var dataType: DataView.DataTypes = DataView.DataTypes.HR
    private var scale: DataView.Scalings = DataView.Scalings.Day
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

    private var startValue: Long = 0
    private fun calculatePercentage(currentProgress: Long, endProgress: Long): String {
        val a = currentProgress - startValue
        val b = 100
        val c = endProgress - startValue
        return (b / c.toDouble() * a).toInt().toString()
    }

    private fun convertDateWithScaling(calendar: Calendar, scale: DataView.Scalings): String {
        return when (scale) {
            DataView.Scalings.Day -> SimpleDateFormat("LLLL d HH:mm", Locale.getDefault()).format(calendar.time)
            DataView.Scalings.Week -> SimpleDateFormat("LLLL W yyyy", Locale.getDefault()).format(calendar.time)
            DataView.Scalings.Month -> SimpleDateFormat("d LLLL yyyy", Locale.getDefault()).format(calendar.time)
        }
    }

    private fun setOffset() {
        staticOffset = when (scale) {
            DataView.Scalings.Day -> CustomDatabaseUtils.CalculateOffsetValue(5, Calendar.MINUTE, false)
            DataView.Scalings.Week -> CustomDatabaseUtils.CalculateOffsetValue(7, Calendar.DAY_OF_MONTH, false)
            DataView.Scalings.Month -> CustomDatabaseUtils.CalculateOffsetValue(1, Calendar.MONTH, false)
        }
    }

    private fun fetchDataStageB(activity: Context): kotlinx.coroutines.flow.Flow<Record?> = flow {
        startValue = currentLock
        while (currentLock < toLong) {
            _currentProgress.postValue(activity.getString(R.string.current_progress_date) + " " +
                    convertDateWithScaling(CustomDatabaseUtils.LongToCalendar(currentLock, true), scale) + " — " +
                    convertDateWithScaling(CustomDatabaseUtils.LongToCalendar(currentLock + staticOffset, true), scale) + " (" + calculatePercentage(currentLock, toLong) + "%)")

            if (cancelled) {
                emit(null); return@flow
            }
            var results: Cursor? = null
            when (dataType) {
                DataView.DataTypes.HR -> {
                    results = if (grouping)
                        HRRecordsTable.extractFuncOnInterval(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                    else
                        HRRecordsTable.extractRecords(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                }
                DataView.DataTypes.Calories -> {
                    results = if (grouping)
                        MainRecordsTable.extractFuncOnIntervalCalories(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                    else
                        MainRecordsTable.extractRecords(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                }
                DataView.DataTypes.Steps -> {
                    results = if (grouping)
                        MainRecordsTable.extractFuncOnIntervalSteps(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                    else
                        MainRecordsTable.extractRecords(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
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
                return@flow
            } finally {
                results?.close()
                currentLock = CustomDatabaseUtils.SumLongs(currentLock, staticOffset, false)
            }
        }
        emit(null)
    }


    fun fetchDataStageA(context: Context, From: Calendar, To: Calendar,
                        scale: DataView.Scalings, DataType: DataView.DataTypes) {
        this.cancelled = false
        this.database = DatabaseController.getDCObject(context).readableDatabase
        this.fromLong = CustomDatabaseUtils.CalendarToLong(From, true)
        this.toLong = CustomDatabaseUtils.CalendarToLong(To, true)
        currentLock = this.fromLong
        this.dataType = DataType
        this.scale = scale
        grouping = scale != DataView.Scalings.Day
        _loading.value = View.VISIBLE
        setOffset()
        val inputQuene: Queue<Record> = LinkedList<Record>()
        viewModelScope.launch(Dispatchers.IO) {
            fetchDataStageB(context).collect {
                if (it != null) {
                    inputQuene.add(it); _result.postValue(inputQuene); }
                else
                    _loading.postValue(View.GONE)
            }
        }
    }


}

class Record(Result: Cursor?, var recordDate: Long, isGrouping: Boolean, private val scale: DataView.Scalings) {
    private var whenCalendar: Calendar = Calendar.getInstance()
    var mainValue: Int = -1// AVG + Default
    var minValue: Int = -1
    var maxValue: Int = -1

    val dateString: String
        get() {
            return when (scale) {
                DataView.Scalings.Day -> SimpleDateFormat("LLLL d HH:mm", Locale.getDefault()).format(CustomDatabaseUtils.LongToCalendar(recordDate, true).time)
                DataView.Scalings.Week -> SimpleDateFormat("LLLL W yyyy", Locale.getDefault()).format(CustomDatabaseUtils.LongToCalendar(recordDate, true).time)
                DataView.Scalings.Month -> SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(CustomDatabaseUtils.LongToCalendar(recordDate, true).time)
            }
        }


    init {
        if (Result != null && recordDate > 0) {
            whenCalendar = CustomDatabaseUtils.LongToCalendar(recordDate, true)
            if (isGrouping) {
                mainValue = Result.getInt(0)//AVG
                minValue = Result.getInt(1)//MIN
                maxValue = Result.getInt(2)//MAX
            } else {
                recordDate = Result.getLong(0)
                whenCalendar = CustomDatabaseUtils.LongToCalendar(recordDate, true)
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


    companion object {
        fun getTestRecord(): Record {
            val result = Record(null, 202007070707, false, DataView.Scalings.Day)
            result.mainValue = random().toInt() % 1000
            result.whenCalendar = Calendar.getInstance(); result.whenCalendar.add(Calendar.MINUTE, -1 * (random().toInt() % 10000))
            return result
        }
    }
}

/* REMOVED NOT OPTIMAL
class DataAdapter() : ListAdapter<Record, DataAdapter.RecordItemHolder>(Companion) {

    companion object : DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean = oldItem === newItem
        override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean = oldItem.recordDate == newItem.recordDate
    }

    class RecordItemHolder(var binding: TableItemBinding) : RecyclerView.ViewHolder(binding.root){   }

    private var _itemCount = 0

    override fun onCurrentListChanged(previousList: MutableList<Record>, currentList: MutableList<Record>) {
        super.onCurrentListChanged(previousList, currentList)
        _itemCount = currentList.size
    }
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordItemHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = TableItemBinding.inflate(layoutInflater)
        return RecordItemHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordItemHolder, position: Int) {
        try {
            val currentItem = getItem(position)
            holder.binding.record = currentItem
            holder.binding.executePendingBindings()
        }catch (ex: Exception){

        }
    }

    override fun getItemCount(): Int {
        return _itemCount
    }
}
 */
