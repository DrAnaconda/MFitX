package anonymouls.dev.mgcex.app.data

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.databinding.ActivityDataViewBinding
import anonymouls.dev.mgcex.app.main.DeviceControllerActivity
import anonymouls.dev.mgcex.util.AdsController
import java.util.*

class DataView : AppCompatActivity() {

    enum class DataTypes { HR, Steps, Calories }
    enum class Scalings { Day, Week, Month }

    private lateinit var complexColumnsUniversal: Array<String>
    private lateinit var hrColumns: Array<String>
    private lateinit var mainCaloriesColumns: Array<String>
    private lateinit var mainStepsColumns: Array<String>
    private lateinit var mainColumns: Array<String>
    private val customAdapter = MyTableViewAdapter(this)

    private val customViewModel by lazy {
        ViewModelProviders.of(this).get(DataViewModel::class.java)
    }

    private var requestBtn: MenuItem? = null
    private var dataIntent: String? = null

    private var scale = Scalings.Day


    private fun initBindings() {
        val binding: ActivityDataViewBinding = DataBindingUtil.setContentView(this, R.layout.activity_data_view)
        binding.viewmodel = customViewModel
        binding.lifecycleOwner = this
        binding.mainTable.setAdapter(customAdapter)
        binding.mainTable.rowHeaderWidth = 0
        binding.mainTable.setHasFixedWidth(true)

        AdsController.initAdBanned(binding.dataAD, this)

        customViewModel.data.observe(this, androidx.lifecycle.Observer {
            while (it.size > 0) {
                try {
                    customAdapter.addRow(customAdapter.countRows++, null, it.remove().getCellsList() as MutableList<Cell?>)
                } catch (ex: Exception) {
                    continue
                }
            }
        })
    }

    private fun init() {
        complexColumnsUniversal = arrayOf(getString(R.string.date_str),
                getString(R.string.min_str), getString(R.string.avg_str), getString(R.string.max_str))
        hrColumns = arrayOf(getString(R.string.DateTime), getString(R.string.value_str))
        mainCaloriesColumns = arrayOf(getString(R.string.DateTime), getString(R.string.calories_str))
        mainStepsColumns = arrayOf(getString(R.string.DateTime), getString(R.string.steps_str))
        mainColumns = arrayOf(getString(R.string.DateTime), getString(R.string.calories_str), getString(R.string.steps_str))
        initBindings()
        ///graph add?
    }

    private fun headerChooser(): Array<String> {
        return if (scale == Scalings.Day) {
            when (dataIntent) {
                "HR" -> hrColumns
                "CALORIES" -> mainCaloriesColumns
                "STEPS" -> mainStepsColumns
                "MAIN" -> mainColumns
                else -> complexColumnsUniversal
            }
        } else {
            complexColumnsUniversal
        }
    }

    private fun executeTableCreation(From: Calendar, To: Calendar, DataType: DataTypes) {
        customAdapter.removeEverything()
        customAdapter.setColumnHeaderItems(Cell.listToCells<ColumnHeader>(headerChooser(), true) as List<ColumnHeader?>?)
        customViewModel.fetchDataStageA(this, From, To, scale, DataType)
    }

    private fun loadData(From: Calendar, To: Calendar) {
        when (dataIntent) {
            "HR" -> executeTableCreation(From, To, DataTypes.HR)
            "STEPS" -> executeTableCreation(From, To, DataTypes.Steps)
            "CALORIES" -> executeTableCreation(From, To, DataTypes.Calories)
        }
    }

    private fun stageD(dateA: Calendar, dateB: Calendar, scaling: Scalings) {
        this.scale = scaling
        var startDate: Calendar? = null
        var endDate: Calendar? = null
        if (dateA.time < dateB.time) {
            startDate = dateA
            endDate = dateB
        } else {
            startDate = dateB
            endDate = dateA
        }
        endDate.set(Calendar.HOUR_OF_DAY, 23); endDate.set(Calendar.MINUTE, 59); endDate.set(Calendar.SECOND, 59)
        loadData(startDate, endDate)
    }

    private fun stageC(lastResult: Calendar, scaling: Scalings) {
        val targetTimeSet = DatePickerDialog.OnDateSetListener { DatePicker, year, month, day ->
            val buffer = Calendar.getInstance()
            buffer.set(year, month, day, 0, 0)
            stageD(lastResult, buffer, scaling)
        }
        DatePickerDialog(this, targetTimeSet, Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun stageB() {
        val scaling = when (DeviceControllerActivity.ViewDialog.LastDialogLink!!.findViewById<RadioGroup>(R.id.ScaleGroup).checkedRadioButtonId) {
            R.id.DayScale -> Scalings.Day
            R.id.WeekScale -> Scalings.Week
            R.id.MonthScale -> Scalings.Month
            else -> Scalings.Day
        }
        val targetTimeSet = DatePickerDialog.OnDateSetListener { DatePicker, year, month, day ->
            val buffer = Calendar.getInstance()
            buffer.set(year, month, day, 0, 0)
            if (scaling != Scalings.Day) // TODO: For test this can be commented
                stageC(buffer, scaling)
            else
                stageD(buffer, buffer, scaling)
        }
        DatePickerDialog(this, targetTimeSet, Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun stageA() {
        val reaction: View.OnClickListener = View.OnClickListener {
            stageB()
        }
        DeviceControllerActivity.ViewDialog("", DeviceControllerActivity.ViewDialog.DialogTask.About).showSelectorDialog(this, reaction)
    }


    fun onCancelClick(v: View?) {
        customViewModel.cancelled = true
    }


//region Default Android

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AdsController.showUltraRare(this)
        init()
        val extras = intent.extras
        dataIntent = extras!!.get(ExtraDataToLoad) as String
        ViewMode = extras.get(ExtraViewMode) as Int
        val todayStart = Calendar.getInstance()
        todayStart.set(Calendar.HOUR_OF_DAY, 0)
        todayStart.set(Calendar.MINUTE, 0)
        todayStart.set(Calendar.SECOND, 0)
        val todayEnd = Calendar.getInstance()
        todayEnd.set(Calendar.HOUR_OF_DAY, 23)
        todayEnd.set(Calendar.MINUTE, 59)
        todayEnd.set(Calendar.SECOND, 59)
        loadData(todayStart, todayEnd)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        requestBtn = menu.findItem(R.id.RequestHistory)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.data_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.RequestHistory -> stageA()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }

    override fun onLowMemory() {
        Toast.makeText(this, "Too much memory consumed, stopping", Toast.LENGTH_LONG).show()
        onCancelClick(null)
        super.onLowMemory()
    }

    override fun onDestroy() {
        AdsController.cancelBigAD()
        super.onDestroy()
    }

//endregion

    companion object {

        var ExtraDataToLoad = "EXTRA_DATA"
        var ExtraViewMode = "EXTRA_VIEW_MODE"

        private var ViewMode = 0

    }
}

// TODO Today IS NOT prev day+today, check it
// TODO Mutable<Record> is not cool, need upgrade to <Mutable<RecordList>> or something? Quene?