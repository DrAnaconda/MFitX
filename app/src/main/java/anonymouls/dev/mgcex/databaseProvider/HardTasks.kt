package anonymouls.dev.mgcex.databaseProvider

import anonymouls.dev.mgcex.util.Utils
import java.util.*

@ExperimentalStdlibApi
object MainCopyAnalyzer{


    fun launchDeltaActivityWithClone(){
        val topRecordUnAnalyzedRecord = MainRecordsTable.getTopUnAnalyzed() ?: return
        val endCalendar = topRecordUnAnalyzedRecord.RTime
        endCalendar.add(Calendar.MINUTE, -30)
        val input = MainRecordsTable.getUnAnalyzedInInterval(CustomDatabaseUtils.calendarToLong(endCalendar, true))
        if (input.size > 10){
            var prev: MainRecordsTable.MainRecord = input.poll()!!
            do {
                val current = input.poll()
                if (current.RTime.get(Calendar.DAY_OF_YEAR) == prev.RTime.get(Calendar.DAY_OF_YEAR)){
                    val deltaMin = Utils.getDeltaCalendar(current.RTime, prev.RTime, Calendar.MINUTE)
                    val deltaSteps = current.Steps-prev.Steps
                    val stepsMin = if (deltaMin == 0) deltaSteps.toDouble()/1 else deltaSteps/deltaMin.toDouble()
                    HRRecordsTable.updateAnalyticalViaMainInfo(deltaMin, stepsMin,
                            CustomDatabaseUtils.calendarToLong(prev.RTime, true))
                }
                MainRecordsTable.markAsAnalyzed(prev.RTime, current.RTime)
                prev = current
            } while (input.size > 0)
        }
    }
}

// Todo Reject sleep data to hell. Try ML and trees for detecting sleep activity