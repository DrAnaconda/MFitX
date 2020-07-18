package anonymouls.dev.mgcex.app.main

import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandCallbacks
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.backend.InsertTask
import anonymouls.dev.mgcex.databaseProvider.HRRecord
import java.util.*


@ExperimentalStdlibApi
class MyViewModelFactory(private val activity: AppCompatActivity) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return DeviceControllerViewModel(activity) as T
    }

}

@ExperimentalStdlibApi
class DeviceControllerViewModel(private val activity: AppCompatActivity) : ViewModel() {

    //region live models

    val workInProgress = MutableLiveData(View.GONE)
    val _batteryHolder = MutableLiveData(-1)
    val _lastHearthRateIncomed = MutableLiveData<HRRecord>(HRRecord(Calendar.getInstance(), -1))
    val _lastCcalsIncomed = MutableLiveData<Int>(-1)
    val _lastStepsIncomed = MutableLiveData(-1)
    val _currentStatus = MutableLiveData<String>(activity.getString(R.string.status_label))
    private var _hrVisibility = MutableLiveData<Int>(View.GONE)

    val currentSteps: LiveData<Int>
        get() {
            return _lastStepsIncomed
        }
    val currentHR: LiveData<HRRecord>
        get() {
            return _lastHearthRateIncomed
        }
    val currentBattery: LiveData<Int>
        get() {
            return _batteryHolder
        }
    val currentCcals: LiveData<Int>
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
    private var ci = CommandInterpreter.getInterpreter(activity)

    init {
        instance = this
        reInit()
    }

    override fun onCleared() {
        instance = null
        super.onCleared()
    }

    private fun createStatusObserver() {
        Algorithm.StatusCode.observe(activity, Observer {
            if (it.code < Algorithm.StatusCodes.GattReady.code) {
                _hrVisibility.postValue(View.GONE)
                workInProgress.postValue(View.VISIBLE)
            } else {
                workInProgress.postValue(View.GONE)
                if (ci.hRRealTimeControlSupport)
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
                    activity.findViewById<TextView>(R.id.BatteryStatus).visibility = View.VISIBLE
                    activity.findViewById<TextView>(R.id.BatteryStatus).text = it.toString()
                } else
                    activity.findViewById<TextView>(R.id.BatteryStatus).visibility = View.INVISIBLE
                when {
                    it < 5 -> {
                    }
                    it < 33 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)
                                .setImageDrawable(activity.getDrawable(R.drawable.chargelow_icon))
                    it < 66 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)
                                .setImageDrawable(activity.getDrawable(R.drawable.chargemed_icon))
                    else -> activity.findViewById<ImageView>(R.id.batteryIcon)
                            .setImageDrawable(activity.getDrawable(R.drawable.chargefull_icon))
                }
            } else {
                when {
                    it < 5 -> {
                    }
                    it < 33 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon).setImageResource(R.drawable.chargelow_icon)
                    it < 66 ->
                        activity.findViewById<ImageView>(R.id.batteryIcon)
                                .setImageResource((R.drawable.chargemed_icon))
                    else -> activity.findViewById<ImageView>(R.id.batteryIcon)
                            .setImageResource((R.drawable.chargefull_icon))
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
                activity.findViewById<TextView>(id).visibility = View.VISIBLE
                activity.findViewById<TextView>(id).text = string
            } else
                activity.findViewById<TextView>(id).visibility = View.INVISIBLE
        })
    }

    private fun createValuesObserver() {
        createTextObserverUniversal(R.id.HRValue, currentHR)
        createTextObserverUniversal(R.id.StepsValue, currentSteps)
        createTextObserverUniversal(R.id.CaloriesValue, currentCcals)
    }

    fun reInit() {
        createValuesObserver()
        createBatteryObserver()
        createStatusObserver()

        Algorithm.SelfPointer?.thread?.interrupt()

        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.GattReady.code
                && ci.hRRealTimeControlSupport)
            _hrVisibility.postValue(View.VISIBLE)
        else
            _hrVisibility.postValue(View.GONE)



        if (CommandCallbacks.getCallback(activity).savedCCals != 0)
            _lastCcalsIncomed.postValue(CommandCallbacks.getCallback(activity).savedCCals)
        if (CommandCallbacks.getCallback(activity).savedSteps != 0)
            _lastStepsIncomed.postValue(CommandCallbacks.getCallback(activity).savedSteps)
        if (CommandCallbacks.getCallback(activity).savedBattery != 0)
            _batteryHolder.postValue(CommandCallbacks.getCallback(activity).savedBattery)
        if (CommandCallbacks.getCallback(activity).savedHR.hr > -1)
            _lastHearthRateIncomed.postValue(CommandCallbacks.getCallback(activity).savedHR)
        if (CommandCallbacks.getCallback(activity).savedStatus.length > 0)
            _currentStatus.postValue(CommandCallbacks.getCallback(activity).savedStatus)
    }

    companion object {
        var instance: DeviceControllerViewModel? = null
    }

}