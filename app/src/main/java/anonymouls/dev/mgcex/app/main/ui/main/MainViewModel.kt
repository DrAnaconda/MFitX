package anonymouls.dev.mgcex.app.main.ui.main

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import anonymouls.dev.mgcex.app.AlarmActivity
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandCallbacks
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.backend.InsertTask
import anonymouls.dev.mgcex.app.data.DataFragment
import anonymouls.dev.mgcex.app.main.MultitaskFragment
import anonymouls.dev.mgcex.app.main.SettingsFragment
import anonymouls.dev.mgcex.databaseProvider.HRRecord
import anonymouls.dev.mgcex.util.PreferenceListener
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import kotlin.system.exitProcess


@ExperimentalStdlibApi
class MyViewModelFactory(private val activity: FragmentActivity) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainViewModel(activity) as T
    }

}

@ExperimentalStdlibApi
class MainViewModel(private val activity: FragmentActivity) : ViewModel(), CommandInterpreter.CommandReaction {
    val workInProgress = MutableLiveData(View.GONE)
    val _batteryHolder = MutableLiveData(-1)
    val _lastHearthRateIncomed = MutableLiveData<HRRecord>(HRRecord(Calendar.getInstance(), -1))
    val _lastCcalsIncomed = MutableLiveData<Int>(-1)
    val _lastStepsIncomed = MutableLiveData(-1)
    val _currentStatus = MutableLiveData<String>(activity.getString(R.string.status_label))
    val _hrVisibility = MutableLiveData<Int>(View.GONE)

    private val currentSteps: LiveData<Int>
        get() {
            return _lastStepsIncomed
        }
    val currentHR: LiveData<HRRecord>
        get() {
            return _lastHearthRateIncomed
        }
    private val currentBattery: LiveData<Int>
        get() {
            return _batteryHolder
        }
    private val currentCcals: LiveData<Int>
        get() {
            return _lastCcalsIncomed
        }
    val currentStatus: LiveData<String>
        get() {
            return _currentStatus
        }
    val progressVisibility: LiveData<Int>
        get() {
            return workInProgress
        }
    val hrVisibility: LiveData<Int>
        get() {
            return _hrVisibility
        }
    //endregion

    private var firstLaunch = true

    init {
        publicModel = this
        GlobalScope.launch(Dispatchers.Default) { reInit() }
    }

    //region Observes

    private fun createStatusObserver() {
        Algorithm.StatusCode.observe(activity, Observer {
            if (it.code < Algorithm.StatusCodes.GattReady.code) {
                _hrVisibility.postValue(View.GONE)
                workInProgress.postValue(View.VISIBLE)
            } else {
                workInProgress.postValue(View.GONE)
                if (CommandInterpreter.getInterpreter(activity).hRRealTimeControlSupport)
                    _hrVisibility.postValue(View.VISIBLE)
                else
                    _hrVisibility.postValue(View.GONE)
                if (firstLaunch) firstLaunch = false
            }
        })

        Algorithm.currentAlgoStatus.observe(activity, Observer {
            _currentStatus.postValue(it)
        })
        InsertTask.insertedRunning.observe(activity, Observer {
            if (it) workInProgress.postValue(View.VISIBLE) else workInProgress.postValue(View.GONE)
        })
    }

    private fun createBatteryObserver() {
        currentBattery.observe(activity, Observer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (it > 5) {
                    activity.findViewById<TextView>(R.id.BatteryStatus)?.visibility = View.VISIBLE
                    activity.findViewById<TextView>(R.id.BatteryStatus)?.text = it.toString()
                } else
                    activity.findViewById<TextView>(R.id.BatteryStatus)?.visibility = View.INVISIBLE
                when {
                    it < 5 -> {}
                    it < 33 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)?.setImageDrawable(activity.getDrawable(R.drawable.chargelow_icon))
                    it < 66 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)?.setImageDrawable(activity.getDrawable(R.drawable.chargemed_icon))
                    else -> activity.findViewById<ImageView>(R.id.batteryIcon)?.setImageDrawable(activity.getDrawable(R.drawable.chargefull_icon))
                }
            } else {
                when {
                    it < 5 -> {}
                    it < 33 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)
                                ?.setImageResource(R.drawable.chargelow_icon)
                    it < 66 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)
                                ?.setImageResource((R.drawable.chargemed_icon))
                    else -> activity.findViewById<ImageView>(R.id.batteryIcon)
                            ?.setImageResource((R.drawable.chargefull_icon))
                }

            }
        })
    }

    private fun <T> createTextObserverUniversal(id: Int, dataToObserve: LiveData<T>) {
        dataToObserve.observe(activity, Observer {
            var string = ""
            string = when (it) {
                is HRRecord -> it.hr.toString()
                is Int -> it.toString()
                else -> it as String
            }
            if (Integer.parseInt(string) > 0) {
                activity.findViewById<TextView>(id)?.visibility = View.VISIBLE
                activity.findViewById<TextView>(id)?.text = string
            } else
                activity.findViewById<TextView>(id)?.visibility = View.INVISIBLE
        })
    }

    private fun createValuesObserver() {
        createTextObserverUniversal(R.id.HRValue, currentHR)
        createTextObserverUniversal(R.id.StepsValue, currentSteps)
        createTextObserverUniversal(R.id.CaloriesValue, currentCcals)
    }

    //endregion

    private fun demoMode(): Boolean {
        return if (!Utils.getSharedPrefs(activity).contains(PreferenceListener.Companion.PrefsConsts.bandAddress)) {
            _currentStatus.postValue(activity.getString(R.string.demo_mode))
            activity.runOnUiThread { _currentStatus.value = activity.getString(R.string.demo_mode) }
            _hrVisibility.postValue(View.GONE)
            workInProgress.postValue(View.GONE)
            true
        } else
            false
    }

    private fun launchDataGraph(Data: DataFragment.DataTypes) {
        val transaction = activity.supportFragmentManager.beginTransaction()
        transaction.addToBackStack(null)
        transaction.replace(R.id.container, DataFragment.newInstance(Data))
        transaction.commit()
    }

    private fun launchActivity(newIntent: Intent) {
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(newIntent)
    }

    private fun changeFragment(frag: Fragment) {
        val transaction = activity.supportFragmentManager.beginTransaction()
        transaction.replace(R.id.container, frag)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun onClickHandler(view: View) {
        val ID = view.id
        when (ID) {
            R.id.realtimeHRSync -> {
                Algorithm.SelfPointer?.ci?.hRRealTimeControl((view as Switch).isChecked)
            }
            R.id.ExitBtnContainer -> {
                Algorithm.IsActive = false
                Algorithm.SelfPointer?.stopSelf()
                activity.stopService(Intent(activity, Algorithm::class.java))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.finishAndRemoveTask()
                } else activity.finish()
                exitProcess(0)
            }
            R.id.SyncNowContainer -> {
                if (this.workInProgress.value!! != View.GONE) {
                    Toast.makeText(activity, activity.getString(R.string.wait_untill_complete), Toast.LENGTH_LONG).show()
                } else {
                    Algorithm.SelfPointer?.bluetoothRejected = false; Algorithm.SelfPointer?.bluetoothRequested = false
                    Algorithm.SelfPointer?.thread?.interrupt()
                }
            }
            R.id.HRContainer -> launchDataGraph(DataFragment.DataTypes.HR)
            R.id.StepsContainer -> launchDataGraph(DataFragment.DataTypes.Steps)
            R.id.CaloriesContainer -> launchDataGraph(DataFragment.DataTypes.Calories)
            R.id.SettingContainer -> changeFragment(SettingsFragment())
            R.id.ReportContainer -> changeFragment(MultitaskFragment())
            R.id.AlarmContainer -> if (Algorithm.IsAlarmingTriggered) {
                Algorithm.IsAlarmingTriggered = false
                Algorithm.IsAlarmWaiting = false
                Algorithm.IsAlarmKilled = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    view.background = activity.getDrawable(R.drawable.custom_border)
                } else {
                    view.background = ContextCompat.getDrawable(activity, R.drawable.custom_border)
                }
                Algorithm.SelfPointer?.ci?.stopLongAlarm()
            } else {
                launchActivity(Intent(activity, AlarmActivity::class.java))
            }
            //R.id.InfoContainer -> // TODO About dialog
            // TODO one day R.id.SleepContainer ->
            R.id.BatteryContainer -> Toast.makeText(activity, activity.getString(R.string.battery_health_not_ready), Toast.LENGTH_LONG).show()
        }
    }

    fun removeObservers(owner: LifecycleOwner){
        currentStatus.removeObservers(owner)
        currentHR.removeObservers(owner)
        currentBattery.removeObservers(owner)
        currentCcals.removeObservers(owner)
        currentSteps.removeObservers(owner)
        hrVisibility.removeObservers(owner)
        workInProgress.removeObservers(owner)
    }
    fun reInit() {
        if (demoMode()) return
        activity.runOnUiThread {
            createValuesObserver()
            createBatteryObserver()
            createStatusObserver()
        }

        Algorithm.SelfPointer?.thread?.interrupt()

        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.GattReady.code
                && CommandInterpreter.getInterpreter(activity).hRRealTimeControlSupport)
            _hrVisibility.postValue(View.VISIBLE)
        else
            _hrVisibility.postValue(View.GONE)
    }
    fun restore(){
        GlobalScope.launch(Dispatchers.Default) {
            if (CommandCallbacks.getCallback(activity).savedCCals != 0)
                _lastCcalsIncomed.postValue(CommandCallbacks.getCallback(activity).savedCCals)
            if (CommandCallbacks.getCallback(activity).savedSteps != 0)
                _lastStepsIncomed.postValue(CommandCallbacks.getCallback(activity).savedSteps)
            if (CommandCallbacks.getCallback(activity).savedBattery != 0)
                _batteryHolder.postValue(CommandCallbacks.getCallback(activity).savedBattery)
            if (CommandCallbacks.getCallback(activity).savedHR.hr > -1)
                _lastHearthRateIncomed.postValue(CommandCallbacks.getCallback(activity).savedHR)
            if (CommandCallbacks.getCallback(activity).savedStatus.isNotEmpty())
                _currentStatus.postValue(CommandCallbacks.getCallback(activity).savedStatus)
        }
    }

    //region Command Reaction

    override fun mainInfo(Steps: Int, Calories: Int) {
        _lastStepsIncomed.postValue(Steps); _lastCcalsIncomed.postValue(Calories)
    }

    override fun batteryInfo(Charge: Int) {
        _batteryHolder.postValue(Charge)
    }

    override fun hrIncome(Time: Calendar, HRValue: Int) {
        _lastHearthRateIncomed.postValue(HRRecord(Time, HRValue))
    }

    override fun hrHistoryRecord(Time: Calendar, HRValue: Int) {
        hrIncome(Time, HRValue)
    }

    override fun mainHistoryRecord(Time: Calendar, Steps: Int, Calories: Int) {
        //ignore
    }

    override fun sleepHistoryRecord(Time: Calendar, Duration: Int, Type: Int) {
        //ignore
    }

    //endregion

    companion object{
        var  publicModel: MainViewModel? = null
    }
}