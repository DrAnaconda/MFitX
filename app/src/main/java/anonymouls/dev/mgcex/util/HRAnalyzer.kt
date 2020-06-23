@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.mgcex.util

import android.content.Context
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.HRRecordsTable

object HRAnalyzer {

    private var lastAvgHR = -1
    var isShadowAnalyzerRunning = MutableLiveData<Boolean>(false)

    enum class MainHRTypes { Bradicardio, Normal, Tahicardio }

    fun determineHRMainType(avgHR: Int): MainHRTypes {
        return when {
            avgHR > 90 -> MainHRTypes.Tahicardio
            avgHR < 60 -> MainHRTypes.Bradicardio
            else -> MainHRTypes.Normal
        }
    }

    fun isAnomaly(HR: Int, avgHR: Int, context: Context): Boolean {
        var avgHR = avgHR
        if (avgHR <= 0 || lastAvgHR <= 0) {
            avgHR = HRRecordsTable.getOverallAverage(DatabaseController.getDCObject(context).writableDatabase)
            lastAvgHR = avgHR
        } else avgHR = lastAvgHR
        val lowerDelta: Float = avgHR - (avgHR * 0.2f)
        val upperDelta: Float = avgHR + (avgHR * 0.2f)
        return HR > upperDelta
    }

    fun physicalStressDetermining(deltaSteps: Double): HRRecordsTable.AnalyticTypes {
        return when {
            deltaSteps < 30 -> HRRecordsTable.AnalyticTypes.Steady
            deltaSteps < 80 -> HRRecordsTable.AnalyticTypes.LowPhysical
            deltaSteps < 110 -> HRRecordsTable.AnalyticTypes.MediumPhysical
            else -> HRRecordsTable.AnalyticTypes.PhysicalStress
        }
    }
}