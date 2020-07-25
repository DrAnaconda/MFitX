package anonymouls.dev.mgcex.util

import android.content.Context
import android.os.Bundle
import anonymouls.dev.mgcex.app.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

@ExperimentalStdlibApi
class FireAnalytics(context: Context) {

    private lateinit var fireInstance: FirebaseAnalytics
    private lateinit var fireCrash: FirebaseCrashlytics

    private var isAllowed: Boolean = true
    private lateinit var userID: String

    init {
        GlobalScope.launch(Dispatchers.Default) {
            userID = if (Utils.getSharedPrefs(context).contains(UserID))
                Utils.getSharedPrefs(context).getString(UserID, "").toString()
            else
                UUID.randomUUID().toString()

            isAllowed = Utils.getSharedPrefs(context).getBoolean(HelpData, true)
            fireInstance = FirebaseAnalytics.getInstance(context)
            fireCrash = FirebaseCrashlytics.getInstance()
            checkEnabled()
        }
    }

    private fun checkEnabled(): Boolean {
        if (BuildConfig.DEBUG){
            fireCrash.setCrashlyticsCollectionEnabled(false)
            fireInstance.setAnalyticsCollectionEnabled(false)
            return false
        }
        fireInstance.setUserId(userID)
        if (userID != null && userID.isNotEmpty()) fireCrash.setUserId(userID)
        return if (isAllowed != null) {
            fireCrash.setCrashlyticsCollectionEnabled(isAllowed)
            fireInstance.setAnalyticsCollectionEnabled(isAllowed)
            isAllowed
        } else false
    }

    fun sendHRData(minHR: Int, avgHR: Int, maxHR: Int) {
        if (!checkEnabled()) return
        val bundle = Bundle()
        bundle.putInt("MinHR", minHR)
        bundle.putInt("AvgHR", avgHR)
        bundle.putInt("MaxHR", maxHR)
        fireInstance.logEvent("Stats_Opened", bundle)
    }

    fun sendCustomEvent(event: String, param: String?) {
        var newEvent = event
        newEvent = newEvent.replace('.', '_')
        if (!checkEnabled()) return
        val bundle = Bundle()
        if (param != null) {
            bundle.putString("Param", param)
            fireInstance.logEvent(newEvent, bundle)
        } else
            fireInstance.logEvent(newEvent, null)
    }


    companion object {

        private lateinit var instance: FireAnalytics

        const val HelpData = "HelpDataSetting"
        const val UserID = "UserID"

        fun getInstance(context: Context): FireAnalytics {
            if (!this::instance.isInitialized) {
                instance = FireAnalytics(context)
            }
            return instance
        }
    }
}