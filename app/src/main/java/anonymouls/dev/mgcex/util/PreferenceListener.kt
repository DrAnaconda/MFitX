package anonymouls.dev.mgcex.util

import android.content.Context
import android.content.SharedPreferences

class PreferenceListener : SharedPreferences.OnSharedPreferenceChangeListener{

    var illuminationEnabled = false
    var callsReceiving = false
    var notifyRepeating = 3
    var stepsSize = 0.5f
    var ignoreLightPhase = false
    var bandID = ""
    var mainSyncInterval = 5
    var targetSteps = 5000

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        //TODO("Not yet implemented")
    }

    @ExperimentalStdlibApi
    companion object{

        object PrefsConsts {

            const val illuminationSetting = "illuminationSetting"
            const val receiveCallsSetting = "ReceiveCalls"
            const val secondsNotify = "secondsRepeat"
            const val repeatsNumbers = "repeatsNumber"
            const val stepsSize = "Step_Size"
            const val lightSleepIgnore = "LightIgnore"
            const val bandIDConst = "BandID"
            const val mainSyncMinutes = "AutoSyncInterval"
            const val targetSteps = "TargetStepsSetting"
            const val longSittingSetting = "LongSittingReminder"
            const val vibrationSetting = "VibrationSetting"
            const val bandAddress = "BandAddress"
            const val batteryThreshold = "BST"
            const val batterySaverEnabled = "BSE"
            const val permitWakeLock = "PWL"
            const val disconnectedMonitoring = "DMT"

            const val hrMonitoringEnabled = "HRMEn"
            const val hrMeasureInterval = "HRMI"
            const val hrMeasureStart = "HRMS"
            const val hrMeasureEnd = "HRMEnd"
        }

        private lateinit var pl: PreferenceListener

        fun getPreferenceListener(context: Context): PreferenceListener{
            if (!this::pl.isInitialized){
                pl = PreferenceListener()
                Utils.getSharedPrefs(context).registerOnSharedPreferenceChangeListener(pl)
            }
            return pl
        }
    }

}