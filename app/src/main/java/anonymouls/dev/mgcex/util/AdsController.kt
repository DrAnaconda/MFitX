package anonymouls.dev.mgcex.util

import android.app.Activity
import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import com.google.android.gms.ads.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*


object AdsController {
    // Test : ca-app-pub-3940256099942544/1033173712 Big xren`
    private var savedContext: Context? = null
    private var adTimer = Timer()
    private var adFree = true

    private fun adTimerTask(bigScreen: InterstitialAd) {
        if (bigScreen.isLoaded) {
            bigScreen.show()
            adTimer.cancel()
            adTimer.purge()
        }
    }


    fun initAdBanned(adObject: AdView, activity: Activity) {
        if (adFree) return
        GlobalScope.launch { MobileAds.initialize(activity) }
        savedContext = activity
        //adObject.adListener = defaultListener
        adObject.loadAd(AdRequest.Builder().build())
    }

    fun showUltraRare(activity: Activity): InterstitialAd? {
        if (adFree) return null
        if (Math.random().toInt() % 10 == 0) return null
        savedContext = activity
        //MobileAds.initialize(activity)
        val bigScreen = InterstitialAd(activity)
        bigScreen.adUnitId = RareBigScreenID
        //bigScreen.adUnitId = "ca-app-pub-3940256099942544/1033173712"
        //bigScreen.adListener = defaultListener
        bigScreen.loadAd(AdRequest.Builder().build())
        adTimer.schedule(object : TimerTask() {
            override fun run() {
                val mainHandler = Handler(Looper.getMainLooper())
                val runner = Runnable { adTimerTask(bigScreen) }
                mainHandler.post(runner)
            }
        }, 500, 1000)
        return bigScreen
    }

    fun createAdView(activity: Activity, viewToAdd: LinearLayout) {
        val loader = AsyncLoader(activity, viewToAdd, false)
        loader.execute()
    }

    fun cancelBigAD() {
        adTimer.cancel()
        adTimer.purge()
    }


    private class AsyncLoader(private val activity: Activity, private val viewToAdd: LinearLayout?, private val isFast: Boolean) : AsyncTask<Void, Void, Void>() {

        lateinit var ad: AdView

        override fun onPreExecute() {
            if (!isFast) {
                ad = AdView(activity)
                ad.adUnitId = BannerID
                ad.adSize = AdSize.LARGE_BANNER
                ad.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                ad.adListener = defaultListener
            }
            //MobileAds.initialize(activity)
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: Void?): Void? {
            initAdBanned(ad, activity)
            return null
        }

        override fun onPostExecute(result: Void?) {
            viewToAdd?.removeAllViews()
            viewToAdd?.addView(ad)
            super.onPostExecute(result)
        }
    }

    private val defaultListener = object : AdListener() {
        override fun onAdLoaded() {
            if (savedContext != null)
                Analytics.getInstance(savedContext)?.sendCustomEvent(AdLoadedEvent, null)
            else
                Analytics.getInstance(null)?.sendCustomEvent(AdLoadedEvent, null)
        }

        override fun onAdFailedToLoad(errorCode: Int) {
            if (savedContext != null)
                Analytics.getInstance(savedContext)?.sendCustomEvent(AdFailedLoadEvent, errorCode.toString())
            else
                Analytics.getInstance(null)?.sendCustomEvent(AdFailedLoadEvent, errorCode.toString())
        }

        override fun onAdOpened() {
            if (savedContext != null)
                Analytics.getInstance(savedContext)?.sendCustomEvent(AdOpenedEvent, null)
            else
                Analytics.getInstance(null)?.sendCustomEvent(AdOpenedEvent, null)
        }

        override fun onAdClicked() {
            if (savedContext != null)
                Analytics.getInstance(savedContext)?.sendCustomEvent(AdClickedEvent, null)
            else
                Analytics.getInstance(null)?.sendCustomEvent(AdClickedEvent, null)
        }

        override fun onAdLeftApplication() {
            if (savedContext != null)
                Analytics.getInstance(savedContext)?.sendCustomEvent(AdLeftEvent, null)
            else
                Analytics.getInstance(null)?.sendCustomEvent(AdLeftEvent, null)
        }

        override fun onAdClosed() {
            if (savedContext != null)
                Analytics.getInstance(savedContext)?.sendCustomEvent(AdClosedEvent, null)
            else
                Analytics.getInstance(null)?.sendCustomEvent(AdClosedEvent, null)
        }
    }

    private const val AppADID = "ca-app-pub-3631159279486321~5110209612"
    private const val BannerID = "ca-app-pub-3631159279486321/2484046271"
    private const val RareBigScreenID = "ca-app-pub-3631159279486321/6808156860"

    private const val AdLoadedEvent = "AdLoaded"
    private const val AdFailedLoadEvent = "AdLoadFailed"
    private const val AdOpenedEvent = "AdOpened"
    private const val AdClickedEvent = "AdClicked"
    private const val AdClosedEvent = "AdClosed"
    private const val AdLeftEvent = "AdLeft"
}