@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.mgcex.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import anonymouls.dev.mgcex.app.backend.Algorithm
import anonymouls.dev.mgcex.databaseProvider.*
import java.util.*
import kotlin.math.abs

object HRAnalyzer {

    private var lastAvgHR = -1
    var isShadowAnalyzerRunning = true

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

    fun physicalStressDetermining(delta: Int, minutesDelta: Int): HRRecordsTable.AnalyticTypes {
        var stepsPerMinute: Float = delta.toFloat() / minutesDelta.toFloat()
        if (minutesDelta == 0) stepsPerMinute = 0.0f
        return when {
            stepsPerMinute < 5 -> HRRecordsTable.AnalyticTypes.Steady
            stepsPerMinute < 10 -> HRRecordsTable.AnalyticTypes.LowPhysical
            stepsPerMinute < 15 -> HRRecordsTable.AnalyticTypes.MediumPhysical
            else -> HRRecordsTable.AnalyticTypes.PhysicalStress
        }
    }

    private fun getDeltaMinutes(calendarPrev: Calendar, calendarNext: Calendar): Int {
        val millis = calendarNext.timeInMillis - calendarPrev.timeInMillis
        val seconds = millis / 1000
        return seconds.toInt() / 60
    }

    fun analyzeShadowMainData(Operator: SQLiteDatabase) {
        val taskforce = AnalyzeSMD()
        taskforce.operator = Operator
        taskforce.execute()
        isShadowAnalyzerRunning = true
    }


    class AnalyzeSMD : AsyncTask<Void, Void, Void>() {
        lateinit var operator: SQLiteDatabase
        private var ready = true

        private fun analyzeActivity(deltaSteps: Int, deltaTime: Int, prevTime: Long, currentTime: Long) {
            val phStress = physicalStressDetermining(deltaSteps, deltaTime)
            val values = ContentValues()
            values.put(HRRecordsTable.ColumnsNames[3], HRRecordsTable.AnalyticTypes.valueOf(phStress.name).type)
            operator.update(DatabaseController.HRRecordsTableName, values,
                    " " + HRRecordsTable.ColumnsNames[1] + " BETWEEN ? AND ? ",
                    arrayOf(prevTime.toString(), currentTime.toString()))
        }

        private fun recordAnalyzeIntermediate(recordStart: Long, deltaMinutes: Int, deltaSteps: Int) {
            val speed: Double = abs(deltaSteps.toDouble()) / deltaMinutes
            AdvancedActivityTracker.insertRecord(recordStart, deltaMinutes, speed, operator)
        }

        override fun doInBackground(vararg params: Void?): Void? {
            if (operator == null) return null
            val curs = operator.query(DatabaseController.MainRecordsTableName + "COPY",
                    arrayOf(MainRecordsTable.ColumnNames[0], MainRecordsTable.ColumnNames[2], MainRecordsTable.ColumnNames[1]), null, null, null, null, "Date")
            if (curs?.count == 0) return null
            ready = false
            var prevSteps = -1
            var prevTime: Long = -1
            var prevID: Long = -1
            var errorCount = 0
            curs?.moveToFirst()
            do {
                try {
                    val currentSteps = curs.getInt(1)
                    val currentTime = curs.getLong(2)
                    val currentID = curs.getLong(0)
                    if (prevSteps < 0 || (prevSteps > 0 && currentSteps == 0)) {
                        prevSteps = currentSteps; prevTime = currentTime; prevID = currentID; continue; }
                    val deltaSteps = currentSteps - prevSteps
                    val deltaTime = getDeltaMinutes(CustomDatabaseUtils.LongToCalendar(prevTime, true), CustomDatabaseUtils.LongToCalendar(currentTime, true))

                    analyzeActivity(deltaSteps, deltaTime, prevTime, currentTime)
                    recordAnalyzeIntermediate(currentTime, deltaTime, deltaSteps)

                    prevSteps = currentSteps
                    prevTime = currentTime
                    operator.delete(DatabaseController.MainRecordsTableName + "COPY", " DATE = ?", arrayOf(prevID.toString()))
                    prevID = currentID
                } catch (ex: Exception) {
                    Thread.sleep(15000)
                    if (errorCount++ > 2) break
                }
            } while (curs.moveToNext())
            curs.close()
            operator.delete(DatabaseController.MainRecordsTableName + "COPY", " DATE = ?", arrayOf(prevID.toString()))
            return null
        }

        override fun onPostExecute(result: Void?) {
            isShadowAnalyzerRunning = false
            if (!ready) Algorithm.SelfPointer?.additionalStatus = "Data analyzer complete"
            super.onPostExecute(result)
        }
    }
}