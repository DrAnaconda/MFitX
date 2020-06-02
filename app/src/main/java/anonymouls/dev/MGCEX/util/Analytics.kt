package anonymouls.dev.MGCEX.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.lang.Exception


class Analytics(context: Context) {

    private val fireInstance: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val fireCrash: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    private val isAllowed: Boolean? = Utils.SharedPrefs?.getBoolean(HelpData, true) // TODO false
    private val userID = Utils.SharedPrefs?.getString(UserID, "")

    private fun checkEnabled(): Boolean{
        fireInstance.setUserId(userID)
        fireCrash.setUserId(userID!!)

        fireCrash.setCrashlyticsCollectionEnabled(isAllowed!!)
        fireInstance.setAnalyticsCollectionEnabled(isAllowed!!)
        return !(isAllowed == null || !isAllowed!!)
    }
    fun sendHRData(minHR: Int, avgHR: Int, maxHR: Int){
        if (!checkEnabled()) return
        val bundle = Bundle()
        bundle.putInt("MinHR", minHR)
        bundle.putInt("AvgHR", avgHR)
        bundle.putInt("MaxHR", maxHR)
        fireInstance.logEvent("Stats_Opened", bundle)
    }
    fun sendCustomEvent(event: String, param: String?){
        if (!checkEnabled()) return
        var bundle = Bundle()
        if (param != null){
            bundle.putString("Param", param)
            fireInstance.logEvent(event, bundle)
        } else
            fireInstance.logEvent(event, null)
    }
    fun recordCrash(ex: Throwable){
        if (!checkEnabled()) return
        fireCrash.sendUnsentReports()
        fireCrash.recordException(ex)
    }


    companion object{

        private var instance: Analytics? = null

        const val HelpData = "HelpDataSetting"
        const val UserID = "UserID"

        fun getInstance(context: Context?): Analytics? {
            if (instance == null && context != null){
                instance = Analytics(context)
            } else if (instance == null && context == null) return null
            return instance
        }
    }
}