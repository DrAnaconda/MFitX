package anonymouls.dev.mgcex.app.main

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.app.backend.Algorithm


class MyViewModelFactory(private val activity: AppCompatActivity) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return DeviceControllerViewModel(activity) as T
    }

}

class DeviceControllerViewModel(activity: AppCompatActivity) : ViewModel() {

    val workInProgress = MutableLiveData(View.GONE)
    val _batteryHolder = MutableLiveData(savedBattery)
    val _lastHearthRateIncomed = MutableLiveData<Int>(savedHR)
    val _lastCcalsIncomed = MutableLiveData<Int>(savedCCals)
    val _lastStepsIncomed = MutableLiveData(savedSteps)
    val _currentStatus = MutableLiveData<String>(activity.getString(R.string.status_label))
    private var _hrVisibility = MutableLiveData<Int>(View.GONE)

    val currentSteps: LiveData<Int>
        get() {
            return _lastStepsIncomed
        }
    val currentHR: LiveData<Int>
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

    init {
        instance = this
        Algorithm.StatusCode.observe(activity, Observer {
            if (it.code < 3) _hrVisibility.postValue(View.GONE) else _hrVisibility.postValue(View.VISIBLE)
        })
        if (savedCCals != -1) _lastCcalsIncomed.postValue(savedCCals)
        if (savedSteps != -1) _lastStepsIncomed.postValue(savedSteps)
        if (savedBattery != -1) _batteryHolder.postValue(savedBattery)
        if (savedHR != -1) _lastHearthRateIncomed.postValue(savedHR)
    }

    override fun onCleared() {
        instance = null
        super.onCleared()
    }

    companion object {

        var instance: DeviceControllerViewModel? = null
        var savedCCals = -1
        var savedSteps = -1
        var savedHR = -1
        var savedBattery = -1
    }

}