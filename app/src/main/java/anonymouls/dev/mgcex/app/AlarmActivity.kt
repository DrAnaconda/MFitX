package anonymouls.dev.mgcex.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.databaseProvider.AlarmsTable
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.util.Utils
import java.util.*


@ExperimentalStdlibApi
class AlarmActivity : Activity() {

    private var table: TableLayout? = null
    private var addAlarmContainer: ConstraintLayout? = null

    private var confirmBtn: MenuItem? = null
    private var addBtn: MenuItem? = null

    private var startHours: EditText? = null
    private var hours: EditText? = null
    private var startMinutes: EditText? = null
    private var minutes: EditText? = null
    private var monday: CheckBox? = null
    private var tuesday: CheckBox? = null
    private var wednesday: CheckBox? = null
    private var thursday: CheckBox? = null
    private var friday: CheckBox? = null
    private var saturday: CheckBox? = null
    private var sunday: CheckBox? = null
    private var enabled: Switch? = null
    private var syncable: Switch? = null
    private var deleteBtn: Button? = null
    private var currentProvider: AlarmProvider? = null

    private val alarmClick = { v: View ->
        val ATR = v as AdvancedTableRow
        setContentView(R.layout.activity_alarm_add)
        initView()
        startHours!!.setText(ATR.AP.HourStart.toString())
        hours!!.setText(ATR.AP.Hour.toString())
        startMinutes!!.setText(ATR.AP.MinuteStart.toString())
        minutes!!.setText(ATR.AP.Minute.toString())
        var DayMask = ATR.AP.DayMask
        if (DayMask != 128) {
            if (DayMask - 64 > -1) {
                sunday!!.isChecked = true
                DayMask -= 64
            }
            if (DayMask - 32 > -1) {
                saturday!!.isChecked = true
                DayMask -= 32
            }
            if (DayMask - 16 > -1) {
                friday!!.isChecked = true
                DayMask -= 16
            }
            if (DayMask - 8 > -1) {
                thursday!!.isChecked = true
                DayMask -= 8
            }
            if (DayMask - 4 > -1) {
                wednesday!!.isChecked = true
                DayMask -= 4
            }
            if (DayMask - 2 > -1) {
                tuesday!!.isChecked = true
                DayMask -= 2
            }
            if (DayMask - 1 > -1) {
                monday!!.isChecked = true
                DayMask -= 1
            }
        }
        enabled!!.isChecked = ATR.AP.IsEnabled
        addBtn!!.isVisible = false
        confirmBtn!!.isVisible = true
        currentProvider = ATR.AP
        deleteBtn!!.visibility = View.VISIBLE
    }
    private var alarmsCount = 0


    private fun createTargetTimePicker() {
        val targetTimeSet = OnTimeSetListener { view, hourOfDay, minute ->
            hours?.setText(Utils.subIntegerConversionCheck(hourOfDay.toString()))
            minutes?.setText(Utils.subIntegerConversionCheck(minute.toString()))
        }

        val timeHour = if (currentProvider != null) currentProvider?.Hour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeMinute = if (currentProvider != null) currentProvider?.Minute else Calendar.getInstance().get(Calendar.MINUTE)
        val tp = TimePickerDialog(this, targetTimeSet, timeHour!!, timeMinute!!, true)
        tp.show()
    }

    fun onClickUniversal(v: View) {
        when (v.id) {
            R.id.DeleteBtn -> {
                currentProvider!!.commitSuicide(DatabaseController.getDCObject(this).writableDatabase); onBackPressed()
            }
            R.id.TargetHour -> createTargetTimePicker()
            R.id.TargetMinute -> createTargetTimePicker()
        }
    }

    private fun initView() {
        deleteBtn = findViewById(R.id.DeleteBtn)
        startHours = findViewById(R.id.startHour)
        hours = findViewById(R.id.TargetHour)
        startMinutes = findViewById(R.id.startMinute)
        minutes = findViewById(R.id.TargetMinute)
        monday = findViewById(R.id.MondayBox)
        tuesday = findViewById(R.id.TuesdayBox)
        wednesday = findViewById(R.id.WednesdayBox)
        syncable = findViewById(R.id.IsSyncable)
        thursday = findViewById(R.id.ThursdayBox)
        friday = findViewById(R.id.FridayBox)
        saturday = findViewById(R.id.SaturdayBox)
        sunday = findViewById(R.id.SundayBox)
        enabled = findViewById(R.id.IsEnabledSwitch)
        table = findViewById(R.id.AlarmsTable)
        addAlarmContainer = findViewById(R.id.AddAlarmContainer)
    }

    @SuppressLint("SetTextI18n")
    private fun createAndAddView(AP: AlarmProvider) {
        val TR = AdvancedTableRow(this, AP)
        TR.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT)
        TR.setPadding(1, 8, 1, 8)

        val defaultParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT)
        defaultParams.gravity = Gravity.CENTER

        val txt = TextView(this)
        txt.layoutParams = defaultParams
        txt.gravity = Gravity.CENTER
        /*
        val Text = Utils.SubIntegerConversionCheck(Integer.toString(AP.HourStart)) +
                " : " + Utils.SubIntegerConversionCheck(Integer.toString(AP.MinuteStart)) +
                " -> " + Utils.SubIntegerConversionCheck(Integer.toString(AP.Hour)) +
                " : " + Utils.SubIntegerConversionCheck(Integer.toString(AP.Minute))
        */
        txt.text = Utils.subIntegerConversionCheck(AP.Hour.toString()) +
                " : " + Utils.subIntegerConversionCheck(AP.Minute.toString())
        TR.addView(txt)


        var sw = Switch(this)
        sw.isChecked = AP.IsSyncable
        sw.gravity = Gravity.CENTER
        sw.setOnClickListener { AP.IsSyncable = sw.isChecked }
        sw.layoutParams = defaultParams
        TR.addView(sw)

        sw = Switch(this)
        sw.isChecked = AP.IsEnabled
        sw.gravity = Gravity.CENTER
        sw.layoutParams = defaultParams
        sw.setOnClickListener { AP.IsEnabled = sw.isChecked }
        TR.addView(sw)
        TR.setOnClickListener(alarmClick)
        table!!.addView(TR)
    }

    private fun loadAlarms() {
        val allAlarms = AlarmsTable.extractRecords(DatabaseController.getDCObject(this).readableDatabase)
        createHeader()
        alarmsCount = allAlarms.count
        if (allAlarms.count > 0) {
            do {
                val AP = AlarmProvider.loadFromCursor(allAlarms)
                createAndAddView(AP)
            } while (allAlarms.moveToNext())
        }
        allAlarms.close()
    }

    private fun createHeader() {
        val TR = TableRow(this)
        TR.setBackgroundColor(Color.GRAY)
        TR.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT)

        val defaultParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT)
        defaultParams.gravity = Gravity.CENTER
        var TV = TextView(this)

        TV.setBackgroundColor(Color.GRAY)
        TV.text = getString(R.string.data_str)
        TV.gravity = Gravity.CENTER
        TR.addView(TV)


        TV = TextView(this)
        TV.text = getString(R.string.syncable_str)
        TV.gravity = Gravity.CENTER
        TV.setBackgroundColor(Color.GRAY)
        TR.addView(TV)

        TV = TextView(this)
        TV.gravity = Gravity.CENTER
        TV.text = getString(R.string.enabled_str)
        TV.setBackgroundColor(Color.GRAY)
        TR.addView(TV)

        table!!.addView(TR)
    }

//region Default Android

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)
        initView()
        loadAlarms()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        confirmBtn = menu.findItem(R.id.ConfirmMenuBtn)
        addBtn = menu.findItem(R.id.AddBtn)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.alarms_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onBackPressed() {
        if (confirmBtn!!.isVisible) {
            confirmBtn!!.isVisible = false
            addBtn!!.isVisible = true
            setContentView(R.layout.activity_alarm)
            initView()
            loadAlarms()
        } else {
            for (i in 0 until table!!.childCount) {
                try {
                    val ATR = table!!.getChildAt(i) as AdvancedTableRow
                    ATR.AP.saveAlarmRecord(DatabaseController.getDCObject(this).writableDatabase, this)
                } catch (Ex: Exception) {
                    // ignore
                }

            }
            finish()
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ConfirmMenuBtn -> {
                addBtn!!.isVisible = true
                confirmBtn!!.isVisible = false
                if (currentProvider == null) {
                    // stay away
                    currentProvider = AlarmProvider(Integer.parseInt(hours!!.text.toString()),
                            Integer.parseInt(minutes!!.text.toString()),
                            AlarmProvider.setDayMask(monday!!.isChecked, tuesday!!.isChecked,
                                    wednesday!!.isChecked, thursday!!.isChecked,
                                    friday!!.isChecked, saturday!!.isChecked, sunday!!.isChecked),
                            -1, enabled!!.isEnabled, -1, -1,
                            //Integer.parseInt(startHours!!.text.toString()),
                            //Integer.parseInt(startMinutes!!.text.toString()),
                            syncable!!.isChecked)
                    // stay away
                } else {
                    currentProvider!!.Hour = Integer.parseInt(hours!!.text.toString())
                    currentProvider!!.Minute = Integer.parseInt(minutes!!.text.toString())
                    currentProvider!!.HourStart = -1//Integer.parseInt(startHours!!.text.toString())
                    currentProvider!!.MinuteStart = -1//Integer.parseInt(startMinutes!!.text.toString())
                    currentProvider!!.DayMask = AlarmProvider.setDayMask(monday!!.isChecked,
                            tuesday!!.isChecked, wednesday!!.isChecked, thursday!!.isChecked,
                            friday!!.isChecked, saturday!!.isChecked, sunday!!.isChecked)
                }
                currentProvider!!.saveAlarmRecord(DatabaseController.getDCObject(this).writableDatabase, this)
                setContentView(R.layout.activity_alarm)
                initView()
                loadAlarms()
            }
            R.id.AddBtn -> {
                if (alarmsCount >= 5) {
                    Toast.makeText(this, getString(R.string.device_supports_five), Toast.LENGTH_LONG).show()
                } else {
                    addBtn!!.isVisible = false
                    confirmBtn!!.isVisible = true
                    setContentView(R.layout.activity_alarm_add)
                    initView()
                }
            }
            R.id.infoBtn -> {
                DeviceControllerActivity.ViewDialog(getString(R.string.alarms_info_message), DeviceControllerActivity.ViewDialog.DialogTask.Intent).showDialog(this)
            }

        }
        return super.onOptionsItemSelected(item)
    }

//endregion

    private inner class AdvancedTableRow(context: Context, var AP: AlarmProvider) : TableRow(context)
}