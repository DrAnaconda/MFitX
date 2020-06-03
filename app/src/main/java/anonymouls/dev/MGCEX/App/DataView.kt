package anonymouls.dev.MGCEX.App

import android.app.Activity
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import anonymouls.dev.MGCEX.DatabaseProvider.CustomDatabaseUtils
import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController
import anonymouls.dev.MGCEX.DatabaseProvider.HRRecordsTable
import anonymouls.dev.MGCEX.DatabaseProvider.MainRecordsTable
import anonymouls.dev.MGCEX.util.AdsController
import anonymouls.dev.MGCEX.util.HRAnalyzer
import com.google.android.gms.ads.AdView
import com.squareup.timessquare.CalendarPickerView
import java.text.SimpleDateFormat
import java.util.*

class DataView : Activity() {

    enum class DataTypes { HR, Steps, Calories }
    enum class Scalings { Day, Week, Month }

    private lateinit var complexColumnsUniversal: Array<String>
    private lateinit var hrColumns: Array<String>
    private lateinit var mainCaloriesColumns: Array<String>
    private lateinit var mainStepsColumns: Array<String>
    private lateinit var mainColumns: Array<String>

    private lateinit var database: SQLiteDatabase
    private var dataComplexer: DataComplexer? = null

    private var lastValue = -1

    private var calendarTool: CalendarPickerView? = null
    private var confirmBtn: MenuItem? = null
    private var requestBtn: MenuItem? = null
    private var data: String? = null
    private var mainTable: TableLayout? = null
    private var scaleGroup: RadioGroup? = null
    private var dayScale: RadioButton? = null
    private var weekScale: RadioButton? = null
    private var monthScale: RadioButton? = null
    private var ad: AdView? = null

    private var scale = Scalings.Day

    private fun init() {
        complexColumnsUniversal = arrayOf(getString(R.string.date_str),
                getString(R.string.min_str), getString(R.string.avg_str), getString(R.string.max_str))
        hrColumns = arrayOf(getString(R.string.DateTime), getString(R.string.value_str))
        mainCaloriesColumns = arrayOf(getString(R.string.DateTime), getString(R.string.calories_str))
        mainStepsColumns = arrayOf(getString(R.string.DateTime), getString(R.string.steps_str))
        mainColumns = arrayOf(getString(R.string.DateTime), getString(R.string.calories_str), getString(R.string.steps_str))


        ad = findViewById(R.id.dataAD)
        AdsController.initAdBanned(ad!!, this)

        calendarTool = findViewById(R.id.CalendarTool)
        val To = Calendar.getInstance()
        To.add(Calendar.DAY_OF_MONTH, 1)
        val From = Calendar.getInstance()
        From.add(Calendar.YEAR, -1)
        calendarTool!!.init(From.time, To.time, Locale.ENGLISH)
                .inMode(CalendarPickerView.SelectionMode.RANGE)
        calendarTool!!.scrollToDate(To.time)
        calendarTool!!.selectedDates
        database = DatabaseController.getDCObject(this).readableDatabase

        scaleGroup = findViewById(R.id.ScaleGroup)
        mainTable = findViewById(R.id.MainTable)
        dayScale = findViewById(R.id.DayScale)
        weekScale = findViewById(R.id.WeekScale)
        monthScale = findViewById(R.id.MonthScale)
        when (ViewMode) {
            0 -> {
            }
            else -> {
                mainTable!!.visibility = View.VISIBLE
                mainTable!!.isEnabled = true
            }
        }///graph add?
    }

    private fun headerChooser() {
        if (scale == Scalings.Day) {
            when (data) {
                "HR" -> executeTableHeaderCreation(hrColumns)
                "CALORIES" -> executeTableHeaderCreation(mainCaloriesColumns)
                "STEPS" -> executeTableHeaderCreation(mainStepsColumns)
                "MAIN" -> executeTableHeaderCreation(mainColumns)
            }
        } else {
            executeTableHeaderCreation(complexColumnsUniversal)
        }
    }

    private fun executeTableHeaderCreation(Target: Array<String>) {
        mainTable!!.removeAllViews()
        val Header = TableRow(this)
        //      Header.setLayoutParams(new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
        //                    TableRow.LayoutParams.MATCH_PARENT));
        for (Column in Target) {
            Header.addView(textViewCreator(Column, 22, true, -1))
        }
        mainTable!!.addView(Header)
    }

    private fun textViewCreator(Text: String, TextSizeValue: Int, IsHeader: Boolean,
                                TextColor: Int): TextView {
        var Text = Text
        val result = TextView(this)
        try {
            if (data == "HR" && HRAnalyzer.isAnomaly(Text.toInt(), -1, this))
            {
                result.setTextColor(Color.parseColor("#ff0000"))
            }
        }catch (ex: Exception){

        }
        result.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT)
        result.setPadding(5, 5, 5, 5)
        result.gravity = Gravity.CENTER
        result.textSize = TextSizeValue.toFloat()
        if (TextColor != -1) result.setTextColor(TextColor)
        if (IsHeader) result.setBackgroundColor(Color.GRAY)
        result.text = Text
        return result
    }

    private fun updateView(Rec: DataComplexer.Record) {
        if (Rec.MainValue == 0) return
        if (Rec.MainValue == lastValue)
            return
        else
            lastValue = Rec.MainValue

        if (scale != Scalings.Day && (Rec.MinValue == 0 || Rec.MaxValue == 0)) return

        val Row = TableRow(this)
        Row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        lateinit var SDF: SimpleDateFormat
        if (dayScale!!.isChecked)
            SDF = SimpleDateFormat("LLLL d H:mm", Locale.ENGLISH)
        else if (weekScale!!.isChecked)
            SDF = SimpleDateFormat("LLLL W yyyy", Locale.ENGLISH)
        else if (monthScale!!.isChecked)
            SDF = SimpleDateFormat("LLLL yyyy", Locale.ENGLISH)
        if (scale == Scalings.Day) {
            Row.addView(textViewCreator(SDF.format(Rec.whenCalendar.time),
                    18, false, -1))
            Row.addView(textViewCreator(Integer.toString(Rec.MainValue), 18,
                    false, -1))
        } else {
            Row.addView(textViewCreator(SDF.format(Rec.whenCalendar.time), 18,
                    false, -1))
            Row.addView(textViewCreator(Integer.toString(Rec.MinValue), 18,
                    false, -1))
            Row.addView(textViewCreator(Integer.toString(Rec.MainValue), 18,
                    false, -1))
            Row.addView(textViewCreator(Integer.toString(Rec.MaxValue), 18,
                    false, -1))
        }
        mainTable!!.addView(Row,
                TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT))

    }

    private fun executeTableCreation(From: Calendar, To: Calendar, DataType: DataTypes) {
        headerChooser()
        dataComplexer = DataComplexer(database, From, To, scale, DataType, 1, this)
        dataComplexer?.compute()
        findViewById<ProgressBar>(R.id.loadingInProgress).visibility = View.VISIBLE
    }


    private fun loadData(From: Calendar, To: Calendar) {
        when (data) {
            "HR" -> if (ViewMode != 0)
                executeTableCreation(From, To, DataTypes.HR)
            "STEPS" -> if (ViewMode != 0)
                executeTableCreation(From, To, DataTypes.Steps)
            "CALORIES" -> if (ViewMode != 0)
                executeTableCreation(From, To, DataTypes.Calories)
        }
    }

    private fun getLastOrFirstDate(IsFromNeeded: Boolean): Calendar {
        val Dates = calendarTool!!.selectedDates
        val Result = Calendar.getInstance()
        val Output: Date
        if (IsFromNeeded)
            Output = Dates[0]
        else
            Output = Dates[Dates.size - 1]
        Result.time = Output
        if (IsFromNeeded) {
            Result.set(Calendar.HOUR_OF_DAY, 0)
            Result.set(Calendar.MINUTE, 0)
            Result.set(Calendar.SECOND, 0)
        } else {
            Result.set(Calendar.HOUR_OF_DAY, 23)
            Result.set(Calendar.MINUTE, 59)
            Result.set(Calendar.SECOND, 59)
        }
        return Result
    }

    private fun hideCalendar() {
        requestBtn!!.isVisible = true
        scaleGroup!!.visibility = View.INVISIBLE
        calendarTool!!.visibility = View.INVISIBLE
        mainTable!!.visibility = View.VISIBLE
        ad!!.visibility = View.VISIBLE
    }

    private fun showCalendar() {
        confirmBtn!!.isVisible = true
        scaleGroup!!.visibility = View.VISIBLE
        calendarTool!!.visibility = View.VISIBLE
        calendarTool!!.clearHighlightedDates()
        mainTable!!.visibility = View.INVISIBLE
        ad!!.visibility = View.INVISIBLE
    }


//region Default Android

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdsController.showUltraRare(this)
        setContentView(R.layout.activity_data_view)
        AdsController.showUltraRare(this)
        init()
        val extras = intent.extras
        data = extras!!.get(ExtraDataToLoad) as String
        ViewMode = extras.get(ExtraViewMode) as Int
        val TodayStart = Calendar.getInstance()
        TodayStart.set(Calendar.HOUR_OF_DAY, 0)
        TodayStart.set(Calendar.MINUTE, 0)
        TodayStart.set(Calendar.SECOND, 0)
        val todayEnd = Calendar.getInstance()
        todayEnd.set(Calendar.HOUR_OF_DAY, 23)
        todayEnd.set(Calendar.MINUTE, 59)
        todayEnd.set(Calendar.SECOND, 59)
        mainTable!!.isEnabled = true
        mainTable!!.visibility = View.VISIBLE
        headerChooser()
        loadData(TodayStart, todayEnd)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        confirmBtn = menu.findItem(R.id.ConfirmMenuBtn)
        requestBtn = menu.findItem(R.id.RequestHistory)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.data_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ConfirmMenuBtn -> {
                mainTable!!.removeAllViews()
                if (dayScale!!.isChecked)
                    scale = Scalings.Day
                else if (weekScale!!.isChecked)
                    scale = Scalings.Week
                else
                    scale = Scalings.Month
                item.isVisible = false
                hideCalendar()
                loadData(getLastOrFirstDate(true), getLastOrFirstDate(false))
            }
            R.id.RequestHistory -> {
                item.isVisible = false; showCalendar(); }
        }
        return super.onOptionsItemSelected(item)
    }
    override fun onBackPressed() {
        if (confirmBtn!!.isVisible) {
            hideCalendar()
        } else finish()
        super.onBackPressed()
    }

    override fun onDestroy() {
        dayScale?.isSelected = true
        monthScale?.isSelected = false
        weekScale?.isSelected = false
        AdsController.cancelBigAD()
        super.onDestroy()
    }

//endregion


    inner class DataComplexer(private val database: SQLiteDatabase, From: Calendar, To: Calendar,
                              private val scale: Scalings, private val DataType: DataTypes, private val ViewStyle: Int, private val DV: DataView) {

        private val fromLong = CustomDatabaseUtils.CalendarToLong(From, true)
        private val toLong = CustomDatabaseUtils.CalendarToLong(To, true)
        private var currentLock: Long = 0
        private var staticOffset: Long = 0
        private lateinit var taskerObject: AsyncTasker
        private var grouping = true

        init {
            From.add(Calendar.DAY_OF_MONTH, -1)
            currentLock = this.fromLong
            setOffset()
            if (scale == Scalings.Day) grouping = false
        }

        private fun setOffset() {
            staticOffset = when (scale) {
                Scalings.Day -> CustomDatabaseUtils.CalculateOffsetValue(5, Calendar.MINUTE, false)
                Scalings.Week -> CustomDatabaseUtils.CalculateOffsetValue(7, Calendar.DAY_OF_MONTH, false)
                Scalings.Month -> CustomDatabaseUtils.CalculateOffsetValue(1, Calendar.MONTH, false)
            }
        }

        fun compute() {
            taskerObject = AsyncTasker()
            taskerObject.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }

        inner class Record(Result: Cursor, private var recordDate: Long, isGrouping: Boolean) {
            var whenCalendar: Calendar
            var MainValue: Int = 0// AVG + Default
            var MinValue: Int = 0
            var MaxValue: Int = 0

            init {
                whenCalendar = CustomDatabaseUtils.LongToCalendar(recordDate, true)
                if (isGrouping) {
                    MainValue = Result.getInt(0)//AVG
                    MinValue = Result.getInt(1)//MIN
                    MaxValue = Result.getInt(2)//MAX
                } else {
                    recordDate = Result.getLong(0)
                    whenCalendar = CustomDatabaseUtils.LongToCalendar(recordDate, true)
                    MainValue = Result.getInt(1)
                }
            }
        }

        inner class AsyncTasker : AsyncTask<Void, Record, Boolean>() {
            override fun onProgressUpdate(vararg values: Record?) {
                for (value in values) {
                    if (value == null) continue
                    DV.updateView(value)
                }
                super.onProgressUpdate(*values)
            }
            override fun doInBackground(vararg params: Void?): Boolean {
                Thread.currentThread().name = "DataLoader"
                while (currentLock < toLong) {
                    var results: Cursor? = null
                        when (DataType) {
                            DataTypes.HR -> {
                                results = if (grouping)
                                    HRRecordsTable.ExtractFuncOnInterval(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                                else
                                    HRRecordsTable.ExtractRecords(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                            }
                            DataTypes.Calories -> {
                                results = if (grouping)
                                    MainRecordsTable.ExtractFuncOnIntervalCalories(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                                else
                                    MainRecordsTable.ExtractRecords(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                            }
                            DataTypes.Steps -> {
                                results = if (grouping)
                                    MainRecordsTable.ExtractFuncOnIntervalSteps(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                                else
                                    MainRecordsTable.ExtractRecords(currentLock, CustomDatabaseUtils.SumLongs(currentLock, staticOffset + 1, false), database)
                            }
                        }
                    try {
                        if (results != null) publishProgress(Record(results, currentLock, grouping))
                    } catch (E: Exception) {
                        //CurrentLock = CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset, false)
                    } finally {
                        currentLock = CustomDatabaseUtils.SumLongs(currentLock, staticOffset, false)
                    }
                }
                return true
            }

            override fun onPostExecute(result: Boolean?) {
                findViewById<ProgressBar>(R.id.loadingInProgress).visibility = View.GONE
                super.onPostExecute(result)
            }
        }

    }

    companion object {

        var ExtraDataToLoad = "EXTRA_DATA"
        var ExtraViewMode = "EXTRA_VIEW_MODE"

        private var ViewMode = 0
    }

}
