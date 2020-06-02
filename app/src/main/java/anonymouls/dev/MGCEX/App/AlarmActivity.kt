package anonymouls.dev.MGCEX.App

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast

import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.Random

import anonymouls.dev.MGCEX.DatabaseProvider.AlarmsTable
import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController

import android.graphics.Color.GRAY
import android.view.*

class AlarmActivity : Activity() {

    private var Table: TableLayout? = null
    private var AddAlarmContainer: ConstraintLayout? = null

    private var ConfirmBtn: MenuItem? = null
    private var AddBtn: MenuItem? = null

    private var StartHours: EditText? = null
    private var Hours: EditText? = null
    private var StartMinutes: EditText? = null
    private var Minutes: EditText? = null
    private var Monday: CheckBox? = null
    private var Tuesday: CheckBox? = null
    private var Wednesday: CheckBox? = null
    private var Thursday: CheckBox? = null
    private var Friday: CheckBox? = null
    private var Saturday: CheckBox? = null
    private var Sunday: CheckBox? = null
    private var Enabled: Switch? = null
    private var Syncable: Switch? = null
    private var DeleteBtn: Button? = null
    private var CurrentProvider: AlarmProvider? = null

    private val AlarmClick = { v:View ->
        val ATR = v as AdvancedTableRow
        setContentView(R.layout.activity_alarm_add)
        InitView()
        StartHours!!.setText(Integer.toString(ATR.AP.HourStart))
        Hours!!.setText(Integer.toString(ATR.AP.Hour))
        StartMinutes!!.setText(Integer.toString(ATR.AP.MinuteStart))
        Minutes!!.setText(Integer.toString(ATR.AP.Minute))
        var DayMask = ATR.AP.DayMask
        if (DayMask != 128) {
            if (DayMask - 64 > -1) {
                Sunday!!.isChecked = true
                DayMask -= 64
            }
            if (DayMask - 32 > -1) {
                Saturday!!.isChecked = true
                DayMask -= 32
            }
            if (DayMask - 16 > -1) {
                Friday!!.isChecked = true
                DayMask -= 16
            }
            if (DayMask - 8 > -1) {
                Thursday!!.isChecked = true
                DayMask -= 8
            }
            if (DayMask - 4 > -1) {
                Wednesday!!.isChecked = true
                DayMask -= 4
            }
            if (DayMask - 2 > -1) {
                Tuesday!!.isChecked = true
                DayMask -= 2
            }
            if (DayMask - 1 > -1) {
                Monday!!.isChecked = true
                DayMask -= 1
            }
        }
        Enabled!!.isChecked = ATR.AP.IsEnabled
        AddBtn!!.isVisible = false
        ConfirmBtn!!.isVisible = true
        CurrentProvider = ATR.AP
        DeleteBtn!!.visibility = View.VISIBLE
    }
    private fun InitView() {
        DeleteBtn = findViewById(R.id.DeleteBtn)
        StartHours = findViewById(R.id.startHour)
        Hours = findViewById(R.id.TargetHour)
        StartMinutes = findViewById(R.id.startMinute)
        Minutes = findViewById(R.id.TargetMinute)
        Monday = findViewById(R.id.MondayBox)
        Tuesday = findViewById(R.id.TuesdayBox)
        Wednesday = findViewById(R.id.WednesdayBox)
        Syncable = findViewById(R.id.IsSyncable)
        Thursday = findViewById(R.id.ThursdayBox)
        Friday = findViewById(R.id.FridayBox)
        Saturday = findViewById(R.id.SaturdayBox)
        Sunday = findViewById(R.id.SundayBox)
        Enabled = findViewById(R.id.IsEnabledSwitch)
        Table = findViewById(R.id.AlarmsTable)
        AddAlarmContainer = findViewById(R.id.AddAlarmContainer)
    }
    private fun CreateAndAddView(AP: AlarmProvider) {
        val TR = AdvancedTableRow(this, AP)
        TR.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT)
        TR.setPadding(5, 5, 5, 5)
        val Txt = TextView(this)
        Txt.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
        /*
        val Text = CommandInterpreter.SubIntegerConversionCheck(Integer.toString(AP.HourStart)) +
                " : " + CommandInterpreter.SubIntegerConversionCheck(Integer.toString(AP.MinuteStart)) +
                " -> " + CommandInterpreter.SubIntegerConversionCheck(Integer.toString(AP.Hour)) +
                " : " + CommandInterpreter.SubIntegerConversionCheck(Integer.toString(AP.Minute))
        */
        val Text = CommandInterpreter.SubIntegerConversionCheck(Integer.toString(AP.Hour)) +
                " : " + CommandInterpreter.SubIntegerConversionCheck(Integer.toString(AP.Minute))

        Txt.text = Text
        TR.addView(Txt)
        var Sw = Switch(this)
        Sw.isChecked = AP.IsSyncable
        Sw.gravity = Gravity.CENTER
        Sw.setOnClickListener { AP.IsSyncable = Sw.isChecked }
        TR.addView(Sw)
        Sw = Switch(this)
        Sw.isChecked = AP.IsEnabled
        Sw.gravity = Gravity.CENTER
        Sw.setOnClickListener { AP.IsEnabled = Sw.isChecked }
        TR.addView(Sw)
        TR.setOnClickListener(AlarmClick)
        Table!!.addView(TR)
    }
    private fun LoadAlarms() {
        val AllAlarms = AlarmsTable.ExtractRecords(DatabaseController.getDCObject(this).currentDataBase!!)
        CreateHeader()
        if (AllAlarms.count > 0) {
            do {
                val AP = AlarmProvider.LoadFromCursor(AllAlarms)
                CreateAndAddView(AP)
            } while (AllAlarms.moveToNext())
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)
        InitView()
        LoadAlarms()
    }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConfirmBtn = menu.findItem(R.id.ConfirmMenuBtn)
        AddBtn = menu.findItem(R.id.AddBtn)
        return super.onPrepareOptionsMenu(menu)
    }
    private fun CreateHeader() {
        val TR = TableRow(this)
        var TV = TextView(this)
        TV.setBackgroundColor(Color.GRAY)
        TV.text = "Data"
        TV.gravity = Gravity.CENTER
        TR.addView(TV)


        TV = TextView(this)
        TV.text = "Is Syncable"
        TV.gravity = Gravity.CENTER
        TV.setBackgroundColor(Color.GRAY)
        TR.addView(TV)

        TV = TextView(this)
        TV.gravity = Gravity.CENTER
        TV.text = "Is Enabled"
        TV.setBackgroundColor(Color.GRAY)
        TR.addView(TV)

        Table!!.addView(TR)
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.alarms_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onBackPressed() {
        if (ConfirmBtn!!.isVisible) {
            ConfirmBtn!!.isVisible = false
            AddBtn!!.isVisible = true
            setContentView(R.layout.activity_alarm)
            InitView()
            LoadAlarms()
        } else {
            for (i in 0 until Table!!.childCount) {
                try {
                    val ATR = Table!!.getChildAt(i) as AdvancedTableRow
                        ATR.AP.SaveAlarmRecord(DatabaseController.getDCObject(this).currentDataBase!!)
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
                AddBtn!!.isVisible = true
                ConfirmBtn!!.isVisible = false
                if (CurrentProvider == null) {
                    // stay away
                    CurrentProvider = AlarmProvider(Integer.parseInt(Hours!!.text.toString()),
                            Integer.parseInt(Minutes!!.text.toString()),
                            AlarmProvider.SetDayMask(Monday!!.isChecked, Tuesday!!.isChecked,
                                    Wednesday!!.isChecked, Thursday!!.isChecked,
                                    Friday!!.isChecked, Saturday!!.isChecked, Sunday!!.isChecked),
                            -1, Enabled!!.isEnabled,
                            Integer.parseInt(StartHours!!.text.toString()),
                            Integer.parseInt(StartMinutes!!.text.toString()),
                            Syncable!!.isChecked)
                    // stay away
                } else {
                    CurrentProvider!!.Hour = Integer.parseInt(Hours!!.text.toString())
                    CurrentProvider!!.Minute = Integer.parseInt(Minutes!!.text.toString())
                    CurrentProvider!!.HourStart = Integer.parseInt(StartHours!!.text.toString())
                    CurrentProvider!!.MinuteStart = Integer.parseInt(StartMinutes!!.text.toString())
                    CurrentProvider!!.DayMask = AlarmProvider.SetDayMask(Monday!!.isChecked,
                            Tuesday!!.isChecked, Wednesday!!.isChecked, Thursday!!.isChecked,
                            Friday!!.isChecked, Saturday!!.isChecked, Sunday!!.isChecked)
                }
                CurrentProvider!!.SaveAlarmRecord(DatabaseController.getDCObject(this).currentDataBase!!)
                setContentView(R.layout.activity_alarm)
                InitView()
                LoadAlarms()
            }
            R.id.AddBtn -> {
                AddBtn!!.isVisible = false
                ConfirmBtn!!.isVisible = true
                setContentView(R.layout.activity_alarm_add)
                InitView()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun deleteBtnClick() {
        CurrentProvider!!.CommitSuicide(DatabaseController.getDCObject(this).currentDataBase!!)
    }

    private inner class AdvancedTableRow(context: Context, var AP: AlarmProvider) : TableRow(context)
}