package anonymouls.dev.mgcex.app.main

import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.app.backend.CommandCallbacks.Companion.SavedValues.savedBattery
import anonymouls.dev.mgcex.app.backend.CommandCallbacks.Companion.SavedValues.savedCCals
import anonymouls.dev.mgcex.app.backend.CommandCallbacks.Companion.SavedValues.savedHR
import anonymouls.dev.mgcex.app.backend.CommandCallbacks.Companion.SavedValues.savedStatus
import anonymouls.dev.mgcex.app.backend.CommandCallbacks.Companion.SavedValues.savedSteps
import anonymouls.dev.mgcex.app.backend.CommandInterpreter
import anonymouls.dev.mgcex.app.backend.InsertTask
import anonymouls.dev.mgcex.databaseProvider.HRRecord


class MyViewModelFactory(private val activity: AppCompatActivity) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return DeviceControllerViewModel(activity) as T
    }

}

class DeviceControllerViewModel(private val activity: AppCompatActivity) : ViewModel() {

    //region live models

    val workInProgress = MutableLiveData(View.GONE)
    val _batteryHolder = MutableLiveData(savedBattery)
    val _lastHearthRateIncomed = MutableLiveData<HRRecord>(savedHR)
    val _lastCcalsIncomed = MutableLiveData<Int>(savedCCals)
    val _lastStepsIncomed = MutableLiveData(savedSteps)
    val _currentStatus = MutableLiveData<String>(activity.getString(R.string.status_label))
    private var _hrVisibility = MutableLiveData<Int>(View.GONE)

    val currentSteps: LiveData<Int>
        get() {
            return _lastStepsIncomed
        }
    val currentHR: LiveData<anonymouls.dev.mgcex.databaseProvider.HRRecord>
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

    fun reInit() {


        if (savedStatus.length == 0) savedStatus = activity.getString(R.string.status_label)
        Algorithm.StatusCode.observe(activity, Observer {
            if (it.code < Algorithm.StatusCodes.GattReady.code) {
                _hrVisibility.postValue(View.GONE)
                if (it.code > Algorithm.StatusCodes.BluetoothDisabled.code)
                    workInProgress.postValue(View.VISIBLE)
                else
                    workInProgress.postValue(View.GONE)
            } else {
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

        currentBattery.observe(activity, Observer {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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


        if (Algorithm.StatusCode.value!!.code >= Algorithm.StatusCodes.GattReady.code
                && ci.hRRealTimeControlSupport)
            _hrVisibility.postValue(View.VISIBLE)
        else
            _hrVisibility.postValue(View.GONE)
        if (savedCCals != -1) _lastCcalsIncomed.postValue(savedCCals)
        if (savedSteps != -1) _lastStepsIncomed.postValue(savedSteps)
        if (savedBattery != -1) _batteryHolder.postValue(savedBattery)
        if (savedHR.hr > -1) _lastHearthRateIncomed.postValue(savedHR)
        if (savedStatus.length > 0) _currentStatus.postValue(savedStatus)
    }

    companion object {
        var instance: DeviceControllerViewModel? = null
    }

}