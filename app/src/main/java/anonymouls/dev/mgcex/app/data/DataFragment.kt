package anonymouls.dev.mgcex.app.data

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.databinding.FragmentDataBinding
import anonymouls.dev.mgcex.util.AdsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

@ExperimentalStdlibApi
class DataFragment(private val DataType: DataTypes) : Fragment() {

    enum class DataTypes { HR, Steps, Calories, Main, Applications }
    enum class Scalings { Day, Week, Month }

    companion object {
        fun newInstance(DataType: DataTypes) = DataFragment(DataType)
    }

    private lateinit var viewModel: DataViewModel
    private lateinit var binding: FragmentDataBinding
    private lateinit var menu: Menu

    private lateinit var complexColumnsUniversal: Array<String>
    private lateinit var hrColumns: Array<String>
    private lateinit var mainCaloriesColumns: Array<String>
    private lateinit var mainStepsColumns: Array<String>
    private lateinit var mainColumns: Array<String>
    private lateinit var customAdapter: DoubleTaskTableViewAdapter

    private lateinit var requestBtn: MenuItem
    private lateinit var manualHRRequestBtn: MenuItem

    //region Logic

    private fun init() {
        complexColumnsUniversal = arrayOf(getString(R.string.date_str),
                getString(R.string.min_str), getString(R.string.avg_str), getString(R.string.max_str))
        hrColumns = arrayOf(getString(R.string.DateTime), getString(R.string.value_str))
        mainCaloriesColumns = arrayOf(getString(R.string.DateTime), getString(R.string.calories_str))
        mainStepsColumns = arrayOf(getString(R.string.DateTime), getString(R.string.steps_str))
        mainColumns = arrayOf(getString(R.string.DateTime), getString(R.string.calories_str), getString(R.string.steps_str))
        val todayStart = Calendar.getInstance()
        todayStart.set(Calendar.HOUR_OF_DAY, 0)
        todayStart.set(Calendar.MINUTE, 0)
        todayStart.set(Calendar.SECOND, 0)
        val todayEnd = Calendar.getInstance()
        todayEnd.set(Calendar.HOUR_OF_DAY, 23)
        todayEnd.set(Calendar.MINUTE, 59)
        todayEnd.set(Calendar.SECOND, 59)
        GlobalScope.launch(Dispatchers.Default) { loadData(todayStart, todayEnd, Scalings.Day) }
    }



    private fun loadData(From: Calendar, To: Calendar, scale: Scalings) {
        when (DataType) {
            DataTypes.HR -> executeTableCreation(From, To, DataTypes.HR, scale)
            DataTypes.Steps -> executeTableCreation(From, To, DataTypes.Steps, scale)
            DataTypes.Calories -> executeTableCreation(From, To, DataTypes.Calories, scale)
            DataTypes.Applications -> viewModel.load(this.requireActivity(), customAdapter)
            else -> throw NotImplementedError("WTF, man")
        }
    }

    //region Stats

    private fun headerChooser(scale: Scalings): Array<String> {
        return if (scale == Scalings.Day) {
            when (DataType) {
                DataTypes.HR -> hrColumns
                DataTypes.Calories -> mainCaloriesColumns
                DataTypes.Steps -> mainStepsColumns
                DataTypes.Main -> mainColumns
                else -> complexColumnsUniversal
            }
        } else {
            complexColumnsUniversal
        }
    }

    private fun executeTableCreation(From: Calendar, To: Calendar, DataType: DataTypes, scale: Scalings) {
        customAdapter.removeEverything()
        customAdapter.setColumnHeaderItems(TextCell.listTextRowToCells<ColumnHeader>(headerChooser(scale), true) as List<ColumnHeader?>?)
        GlobalScope.launch { viewModel.fetchDataStageA(From, To, scale, DataType, customAdapter, requireActivity()) }
    }

    private fun stageD(dateA: Calendar, dateB: Calendar, scaling: Scalings) {
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
        loadData(startDate, endDate, scaling)
    }

    private fun stageC(lastResult: Calendar, scaling: Scalings) {
        val targetTimeSet = DatePickerDialog.OnDateSetListener { DatePicker, year, month, day ->
            val buffer = Calendar.getInstance()
            buffer.set(year, month, day, 0, 0)
            stageD(lastResult, buffer, scaling)
        }
        DatePickerDialog(requireContext(), targetTimeSet, Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun stageB(scale: Scalings) {
        val targetTimeSet = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val buffer = Calendar.getInstance()
            buffer.set(year, month, day, 0, 0)
            stageC(buffer, scale)
        }
        DatePickerDialog(requireContext(), targetTimeSet, Calendar.getInstance().get(Calendar.YEAR),
                Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun stageA() {
        generateSelectorDialog()
    }

    private fun generateSelectorDialog() {
        val builder = AlertDialog.Builder(requireContext())
        var scale = Scalings.Day
        builder.setTitle(R.string.choose_period)
        builder.setSingleChoiceItems(R.array.data_periods, 0) { dialog, item ->
            when (item) {
                0 -> scale = Scalings.Day
                1 -> scale = Scalings.Week
                2 -> scale = Scalings.Month
            }
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.setPositiveButton(android.R.string.ok) { dialog, _ ->
            stageB(scale)
            dialog.cancel()
        }
        builder.create().show()
    }

    //endregion

    private fun requestCurrentHR() {
        if (this.viewModel.loading.value!! == View.VISIBLE) {
            Toast.makeText(requireContext(), getString(R.string.wait_untill_complete), Toast.LENGTH_LONG).show()
        } else {
            this.viewModel.fetchCurrentHR(requireActivity() as AppCompatActivity)
        }
    }

    private fun initBindings() {
        viewModel = ViewModelProviders.of(this).get(DataViewModel::class.java)
        binding.viewmodel = viewModel
        binding.lifecycleOwner = this
        binding.mainTable.rowHeaderWidth = 0
        binding.mainTable.setHasFixedWidth(true)
        customAdapter = when(DataType){
            DataTypes.Applications -> DoubleTaskTableViewAdapter(requireActivity(), DoubleTaskTableViewAdapter.DataTypes.ApplicationMode)
            else -> DoubleTaskTableViewAdapter(requireActivity(), DoubleTaskTableViewAdapter.DataTypes.TextOnly)
        }
        binding.mainTable.setAdapter(customAdapter)
        AdsController.initAdBanned(binding.dataAD, requireActivity())
    }

    //endregion

    //region Default Android

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (this.DataType != DataTypes.Applications)
            setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        initBindings()
        init()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        this.menu = menu
        requestBtn = menu.findItem(R.id.RequestHistory)
        manualHRRequestBtn = menu.findItem(R.id.manualRequestHR)
        manualHRRequestBtn.isVisible = !CommandInterpreter.getInterpreter(requireContext()).hRRealTimeControlSupport
        super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.requireActivity().menuInflater.inflate(R.menu.data_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.RequestHistory -> stageA()
            R.id.manualRequestHR -> requestCurrentHR()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onLowMemory() {
        Toast.makeText(requireContext(), "Too much memory consumed, stopping", Toast.LENGTH_LONG).show()
        if (this::viewModel.isInitialized) viewModel.onCancelClick(null)
        super.onLowMemory()
    }

    override fun onDestroy() {
        AdsController.cancelBigAD()
        if (this::viewModel.isInitialized) viewModel.onCancelClick(null)
        if (this::menu.isInitialized) this.menu.clear()
        super.onDestroy()
    }

    //endregion

}