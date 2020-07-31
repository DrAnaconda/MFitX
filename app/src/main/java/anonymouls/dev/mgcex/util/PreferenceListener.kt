package anonymouls.dev.mgcex.util

import android.content.Context
import android.content.SharedPreferences
import anonymouls.dev.mgcex.util.PreferenceListener.Companion.PrefsConsts.repeatsNumbers
import anonymouls.dev.mgcex.util.PreferenceListener.Companion.PrefsConsts.secondsNotify

@ExperimentalStdlibApi
class PreferenceListener(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener{

    init {
        repeatDelayNotification = Utils.getSharedPrefs(context).getString(secondsNotify, "5")!!.toInt()
        repeatNotifications = Utils.getSharedPrefs(context).getString(repeatsNumbers, "3")!!.toInt()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null || sharedPreferences == null) return
        else{
            when(key){
                repeatsNumbers -> { repeatNotifications = sharedPreferences.getString(key, "3")!!.toInt() }
                secondsNotify -> { repeatDelayNotification = sharedPreferences.getString(key, "5")!!.toInt() }
            }
        }
        //TODO("Not yet implemented")
    }

    @ExperimentalStdlibApi
    companion object{

        var illuminationEnabled = false
        var callsReceiving = false
        var stepsSize = 0.5f
        var ignoreLightPhase = false
        var bandID = ""
        var mainSyncInterval = 5
        var targetSteps = 5000

        var repeatNotifications = 3
        var repeatDelayNotification = 5

        object PrefsConsts {

            const val secondsNotify = "secondsRepeat"
            const val repeatsNumbers = "repeatsNumber"

            const val illuminationSetting = "illuminationSetting"
            const val receiveCallsSetting = "ReceiveCalls"
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
                pl = PreferenceListener(context)
                Utils.getSharedPrefs(context).registerOnSharedPreferenceChangeListener(pl)
            }
            return pl
        }
    }

}