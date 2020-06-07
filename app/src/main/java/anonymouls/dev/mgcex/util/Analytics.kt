package anonymouls.dev.mgcex.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics


class Analytics(context: Context) {

    private val fireInstance: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val fireCrash: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    private val isAllowed: Boolean? = Utils.SharedPrefs?.getBoolean(HelpData, false)
    private val userID = Utils.SharedPrefs?.getString(UserID, "")

    private fun checkEnabled(): Boolean {
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

        private var instance: Analytics? = null

        const val HelpData = "HelpDataSetting"
        const val UserID = "UserID"

        fun getInstance(context: Context?): Analytics? {
            if (instance == null && context != null) {
                instance = Analytics(context)
            } else if (instance == null && context == null) return null
            return instance
        }
    }
}