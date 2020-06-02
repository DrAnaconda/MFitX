package anonymouls.dev.MGCEX.App

import android.app.Activity
import android.database.Cursor
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

import com.squareup.timessquare.CalendarPickerView

import java.text.SimpleDateFormat

import anonymouls.dev.MGCEX.DatabaseProvider.CustomDatabaseUtils
import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController
import anonymouls.dev.MGCEX.DatabaseProvider.HRRecordsTable
import anonymouls.dev.MGCEX.DatabaseProvider.MainRecordsTable
import anonymouls.dev.MGCEX.util.AdsController
import anonymouls.dev.MGCEX.util.HRAnalyzer
import java.util.*

@Suppress("NAME_SHADOWING")
class DataView : Activity() {

    private var DC: DatabaseController? = null
    private var DHRC : DataComplexer? = null

    private var LastValue = -1

    private var CalendarTool: CalendarPickerView? = null
    private var ConfirmBtn: MenuItem? = null
    private var RequestBtn: MenuItem? = null
    private var Data: String? = null
    private var MainTable: TableLayout? = null
    private var ScaleGroup: RadioGroup? = null
    private var DayScale: RadioButton? = null
    private var WeekScale: RadioButton? = null
    private var MonthScale: RadioButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdsController.showUltraRare(this)
        setContentView(R.layout.activity_data_view)
        init()
        val Extras = intent.extras
        Data = Extras!!.get(ExtraDataToLoad) as String
        ViewMode = Extras.get(ExtraViewMode) as Int
        val TodayStart = Calendar.getInstance()
        TodayStart.set(Calendar.HOUR_OF_DAY, 0)
        TodayStart.set(Calendar.MINUTE, 0)
        TodayStart.set(Calendar.SECOND, 0)
        val TodayEnd = CustomDatabaseUtils.GetLastSyncFromTable(DatabaseController.HRRecordsTableName,
                HRRecordsTable.ColumnsNames, true, DC!!.currentDataBase!!)
        MainTable!!.isEnabled = true
        MainTable!!.visibility = View.VISIBLE
        HeaderChooser()
        LoadData(TodayStart, TodayEnd)
    }

    private fun init() {
        CalendarTool = findViewById(R.id.CalendarTool)
        val To = Calendar.getInstance()
        To.add(Calendar.DAY_OF_MONTH, 1)
        val From = Calendar.getInstance()
        From.add(Calendar.YEAR, -1)
        CalendarTool!!.init(From.time, To.time, Locale.ENGLISH)
                .inMode(CalendarPickerView.SelectionMode.RANGE)
        CalendarTool!!.scrollToDate(To.time)
        CalendarTool!!.selectedDates
        DC = DatabaseController.getDCObject(this)

        ScaleGroup = findViewById(R.id.ScaleGroup)
        MainTable = findViewById(R.id.MainTable)
        DayScale = findViewById(R.id.DayScale)
        WeekScale = findViewById(R.id.WeekScale)
        MonthScale = findViewById(R.id.MonthScale)
        when (ViewMode) {
            0 -> {
            }
            else -> {
                MainTable!!.visibility = View.VISIBLE
                MainTable!!.isEnabled = true
            }
        }///graph add?
    }

    private fun HeaderChooser() {
        when (Data) {
            "HR" -> if (Mode == 1)
                ExecuteTableHeaderCreation(DataView.HRColumns)
            else
                ExecuteTableHeaderCreation(HRColumnsComplex)
            "CALORIES" -> if (Mode == 1)
                ExecuteTableHeaderCreation(MainCaloriesColumns)
            else
                ExecuteTableHeaderCreation(MainCaloriesComplex)
            "STEPS" -> if (Mode == 1)
                ExecuteTableHeaderCreation(MainStepsColumns)
            else
                ExecuteTableHeaderCreation(MainStepsColumns)
            "MAIN" -> if (Mode == 1)
                ExecuteTableHeaderCreation(MainColumns)
            else
                ExecuteTableHeaderCreation(MainColumnsComplex)
        }
    }

    private fun ExecuteTableHeaderCreation(Target: Array<String>) {
        MainTable!!.removeAllViews()
        val Header = TableRow(this)
        //      Header.setLayoutParams(new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT,
        //                    TableRow.LayoutParams.MATCH_PARENT));
        for (Column in Target) {
            Header.addView(TextViewCreator(Column, 22, true, -1))
        }
        MainTable!!.addView(Header)
    }

    private fun TextViewCreator(Text: String, TextSizeValue: Int, IsHeader: Boolean,
                                TextColor: Int): TextView {
        var Text = Text
        try {
            if (Data == "HR" && HRAnalyzer.isAnomaly(Text.toInt(), -1))
            {
                Text += " !!!"
            }
        }catch (ex: Exception){

        }
        val Result = TextView(this)
        Result.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT)
        Result.setPadding(5, 5, 5, 5)
        Result.gravity = Gravity.CENTER
        Result.textSize = TextSizeValue.toFloat()
        if (TextColor != -1) Result.setTextColor(TextColor)
        if (IsHeader) Result.setBackgroundColor(Color.GRAY)
        Result.text = Text
        return Result
    }

    private fun UpdateView(Rec: DataComplexer.Record) {
        if (Rec.MainValue == 0) return
        if (Rec.MainValue == LastValue)
            return
        else
            LastValue = Rec.MainValue

        if (Mode != 1 && (Rec.MinValue == 0 || Rec.MaxValue == 0)) return

        val Row = TableRow(this)
        Row.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
        lateinit var SDF: SimpleDateFormat
        if (DayScale!!.isChecked)
            SDF = SimpleDateFormat("LLLL d H:mm", Locale.ENGLISH)
        else if (WeekScale!!.isChecked)
            SDF = SimpleDateFormat("LLLL W yyyy", Locale.ENGLISH)
        else if (MonthScale!!.isChecked)
            SDF = SimpleDateFormat("LLLL yyyy", Locale.ENGLISH)
        if (Mode == 1) {
            Row.addView(TextViewCreator(SDF.format(Rec.WhenCalendar.time),
                    18, false, -1))
            Row.addView(TextViewCreator(Integer.toString(Rec.MainValue), 18,
                    false, -1))
        } else {
            Row.addView(TextViewCreator(SDF.format(Rec.WhenCalendar.time), 18,
                    false, -1))
            Row.addView(TextViewCreator(Integer.toString(Rec.MinValue), 18,
                    false, -1))
            Row.addView(TextViewCreator(Integer.toString(Rec.MainValue), 18,
                    false, -1))
            Row.addView(TextViewCreator(Integer.toString(Rec.MaxValue), 18,
                    false, -1))
        }
        MainTable!!.addView(Row,
                TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT))

    }

    private fun ExecuteTableCreation(From: Calendar, To: Calendar, DataType: Int) {
        HeaderChooser()
        DHRC = DataComplexer(DC!!, From, To, Mode, DataType, 1, this)
        DHRC?.Compute()
    }


    private fun LoadData(From: Calendar, To: Calendar) {
        when (Data) {
            "HR" -> if (ViewMode != 0)
            //              ExecuteGraphBuilding(From, To,2);
            //else
                ExecuteTableCreation(From, To, 2)
            "STEPS" -> if (ViewMode != 0)
            //            ExecuteGraphBuilding(From, To,3);
            //  else
                ExecuteTableCreation(From, To, 3)
            "CALORIES" -> if (ViewMode != 0)
            //          ExecuteGraphBuilding(From, To,4);
            //    else
                ExecuteTableCreation(From, To, 4)
        }
    }

    private fun GetLastOrFirstDate(IsFromNeeded: Boolean): Calendar {
        val Dates = CalendarTool!!.selectedDates
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConfirmBtn = menu.findItem(R.id.ConfirmMenuBtn)
        RequestBtn = menu.findItem(R.id.RequestHistory)
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.data_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ConfirmMenuBtn -> {
                MainTable!!.removeAllViews()
                if (DayScale!!.isChecked)
                    Mode = 1
                else if (WeekScale!!.isChecked)
                    Mode = 2
                else
                    Mode = 3
                item.isVisible = false
                RequestBtn!!.isVisible = true
                ScaleGroup!!.visibility = View.INVISIBLE
                CalendarTool!!.visibility = View.INVISIBLE
                MainTable!!.visibility = View.VISIBLE
                LoadData(GetLastOrFirstDate(true), GetLastOrFirstDate(false))
            }
            R.id.RequestHistory -> {
                item.isVisible = false
                ConfirmBtn!!.isVisible = true
                ScaleGroup!!.visibility = View.VISIBLE
                CalendarTool!!.visibility = View.VISIBLE
                CalendarTool!!.clearHighlightedDates()
                MainTable!!.visibility = View.INVISIBLE
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }

    inner class DataComplexer(private val DC: DatabaseController, private val From: Calendar, private val To: Calendar,
                                       Mode: Int, private val DataType: Int, ViewStyle: Int, private val DV: DataView) {
        private val FromLong: Long
        private val ToLong: Long
        private var CurrentLock: Long = 0
        private var StaticOffset: Long = 0
        private var Mode = 2
        var Output: List<Record> = ArrayList()
        private var ViewStyle = 0
        private lateinit var TaskerObject : AsyncTasker

        init {
            this.Mode = Mode
            this.ViewStyle = ViewStyle
            //if (DataType > 2) {
            ToLong = CustomDatabaseUtils.CalendarToLong(To, true)
            FromLong = CustomDatabaseUtils.CalendarToLong(From, true)
            //}
            /*else{
            ToLong = CustomDatabaseUtils.CalendarToLong(To, false);
            FromLong = CustomDatabaseUtils.CalendarToLong(From,false);}*/
            From.add(Calendar.DAY_OF_MONTH, -1)
            CurrentLock = this.FromLong
            SetOffset()
        }
        private fun SetOffset() {
            when (Mode) {
                1 -> StaticOffset = CustomDatabaseUtils.CalculateOffsetValue(5, Calendar.MINUTE, false)
                2 -> StaticOffset = CustomDatabaseUtils.CalculateOffsetValue(7, Calendar.DAY_OF_MONTH, false)
                3 -> StaticOffset = CustomDatabaseUtils.CalculateOffsetValue(1, Calendar.MONTH, false)
            }
        }
        fun Compute() {
            TaskerObject = AsyncTasker()
            TaskerObject.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
        inner class Record(Result: Cursor, When: Long, Mode: Int, DataPosition: Int) {
            private val When: Long
            var WhenCalendar: Calendar
            var MainValue: Int = 0// AVG + Default
            var MinValue: Int = 0
            var MaxValue: Int = 0

            init {
                var When = When
                this.When = When
                WhenCalendar = CustomDatabaseUtils.LongToCalendar(When, true)
                if (Mode != 1) {
                    MainValue = Result.getInt(0)//AVG
                    MinValue = Result.getInt(1)//MIN
                    MaxValue = Result.getInt(2)//MAX
                } else {
                    When = Result.getLong(0)
                    WhenCalendar = CustomDatabaseUtils.LongToCalendar(When, true)
                    MainValue = Result.getInt(DataPosition - 1)
                }
            }
        }

        inner class AsyncTasker : AsyncTask<Void, Record, Boolean>() {
            override fun onProgressUpdate(vararg values: Record?) {
                for (NewRec in values)
                    DV.UpdateView(NewRec!!)
                super.onProgressUpdate(*values)
            }
            override fun doInBackground(vararg params: Void?): Boolean {
                Thread.currentThread().name = "DataLoader"
                while (CurrentLock < ToLong) {
                    val Results: Cursor?
                    if (Mode != 1) {
                        when (DataType) {
                            2 -> Results = HRRecordsTable.ExtractFuncOnInterval(CurrentLock, CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset + 1, false), DC.currentDataBase!!)
                            4 -> Results = MainRecordsTable.ExtractFuncOnIntervalCalories(CurrentLock, CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset + 1, false), DC.currentDataBase!!)
                            3 -> Results = MainRecordsTable.ExtractFuncOnIntervalSteps(CurrentLock, CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset + 1, false), DC.currentDataBase!!)
                            else -> Results = null
                        }
                    } else {
                        when (DataType) {
                            2 -> Results = HRRecordsTable.ExtractRecords(CurrentLock, CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset + 1, false), DC.currentDataBase!!)
                            else -> Results = MainRecordsTable.ExtractRecords(CurrentLock, CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset + 1, false), DC.currentDataBase!!)
                        }
                    }
                    try {
                        if (DataType != 2) {
                            val Rec = Record(Results!!, CurrentLock, Mode, DataType - 1)
                            //Output.add(new Record(Results, CurrentLock, Mode, DataType - 1));
                            //DV.Hndlr.post { DV.UpdateView(Rec) }
                            publishProgress(Rec)
                        } else {
                            val Rec = Record(Results!!, CurrentLock, Mode, DataType)
                            //Output.add(new Record(Results, CurrentLock, Mode, DataType));
                            //DV.Hndlr.post { DV.UpdateView(Rec) }
                            publishProgress(Rec)
                        }
                    } catch (E: Exception) {
                        // pofig
                    } finally {
                        CurrentLock = CustomDatabaseUtils.SumLongs(CurrentLock, StaticOffset, false)
                    }
                }
                return true
            }

        }

    }

    companion object {

        var ExtraDataToLoad = "EXTRA_DATA"
        var ExtraViewMode = "EXTRA_VIEW_MODE"

        private var Mode = 1
        private var ViewMode = 0

        private val DayScaleMode = 1
        private val WeekScaleMode = 2
        private val MonthScaleMode = 3

        private val HRColumns = arrayOf("Date\\Time", "Value")
        private val HRColumnsComplex = arrayOf("Date", "Min", "Average", "Maximum")
        private val MainCaloriesColumns = arrayOf("Date\\Time", "Value")
        private val MainCaloriesComplex = arrayOf("Date", "Calories")
        private val MainStepsColumns = arrayOf("Date\\Time", "Steps")
        private val MainStepsComplex = arrayOf("Date", "Value")
        private val MainColumns = arrayOf("Date\\Time", "Calories", "Steps")
        private val MainColumnsComplex = arrayOf("Date\\Time", "Calories", "Steps")
        private val MainSummaryColumns = arrayOf("Date", "Calories", "Steps", "Minimal HR", "Average HR", "Highest HR")
    }


}
