package anonymouls.dev.mgcex.util

import android.app.Activity
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import anonymouls.dev.mgcex.app.backend.ApplicationStarter
import com.google.android.gms.ads.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


@ExperimentalStdlibApi
object AdsController {
    // Test : ca-app-pub-3940256099942544/1033173712 Big xren`
    private var adFree = true
    private var adHandler = Handler(Looper.getMainLooper())

    private fun adTimerTask(bigScreen: InterstitialAd) {
        if (bigScreen.isLoaded) {
            bigScreen.show()
            cancelBigAD()
        }
    }


    fun initAdBanned(adObject: AdView, activity: Activity) {
        if (adFree) {
            adObject.visibility = View.GONE
            return
        }
        GlobalScope.launch { MobileAds.initialize(activity) }
        ApplicationStarter.appContext = activity
        //adObject.adListener = defaultListener
        adObject.loadAd(AdRequest.Builder().build())
    }

    fun showUltraRare(activity: Activity): InterstitialAd? {
        if (adFree) return null
        if (Math.random().toInt() % 10 == 0) return null
        ApplicationStarter.appContext = activity
        //MobileAds.initialize(activity)
        val bigScreen = InterstitialAd(activity)
        bigScreen.adUnitId = RareBigScreenID
        //bigScreen.adUnitId = "ca-app-pub-3940256099942544/1033173712"
        //bigScreen.adListener = defaultListener
        bigScreen.loadAd(AdRequest.Builder().build())
        adHandler.postDelayed({ adTimerTask(bigScreen) }, 3500)
        return bigScreen
    }

    fun createAdView(activity: Activity, viewToAdd: LinearLayout) {
        val loader = AsyncLoader(activity, viewToAdd, false)
        loader.execute()
    }

    fun cancelBigAD() {
        adHandler.removeCallbacksAndMessages(null)
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
                Analytics.getInstance(ApplicationStarter.appContext).sendCustomEvent(AdLoadedEvent, null)
        }

        override fun onAdFailedToLoad(errorCode: Int) {
                Analytics.getInstance(ApplicationStarter.appContext).sendCustomEvent(AdFailedLoadEvent, errorCode.toString())
        }

        override fun onAdOpened() {
                Analytics.getInstance(ApplicationStarter.appContext).sendCustomEvent(AdOpenedEvent, null)
        }

        override fun onAdClicked() {
                Analytics.getInstance(ApplicationStarter.appContext).sendCustomEvent(AdClickedEvent, null)
        }

        override fun onAdLeftApplication() {
                Analytics.getInstance(ApplicationStarter.appContext).sendCustomEvent(AdLeftEvent, null)
        }

        override fun onAdClosed() {
                Analytics.getInstance(ApplicationStarter.appContext).sendCustomEvent(AdClosedEvent, null)
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