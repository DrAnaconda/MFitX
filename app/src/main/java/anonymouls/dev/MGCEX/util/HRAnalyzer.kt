@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.MGCEX.util

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import anonymouls.dev.MGCEX.App.Algorithm
import anonymouls.dev.MGCEX.DatabaseProvider.CustomDatabaseUtils
import anonymouls.dev.MGCEX.DatabaseProvider.DatabaseController
import anonymouls.dev.MGCEX.DatabaseProvider.HRRecordsTable
import anonymouls.dev.MGCEX.DatabaseProvider.MainRecordsTable
import java.util.*

object HRAnalyzer{

    var isShadowAnalyzerRunning = true
    enum class MainHRTypes { Bradicardio, Normal, Tahicardio }

        fun determineHRMainType(avgHR: Int): MainHRTypes{
            return when {
                avgHR > 90 -> MainHRTypes.Tahicardio
                avgHR < 60 -> MainHRTypes.Bradicardio
                else -> MainHRTypes.Normal
            }
        }
        fun isAnomaly(HR: Int, avgHR: Int): Boolean{
            var avgHR = avgHR
            if (avgHR <= 0){
                avgHR = HRRecordsTable.getOverallAverage(DatabaseController.DCObject!!.writableDatabase)
            }
            val lowerDelta: Float = avgHR-(avgHR*0.2f)
            val upperDelta: Float = avgHR+(avgHR*0.2f)
            return HR > upperDelta || HR < lowerDelta
        }
        fun physicalStressDetermining(delta: Int, minutesDelta: Int): HRRecordsTable.AnalyticTypes{
            var stepsPerMinute: Float = delta.toFloat()/minutesDelta.toFloat()
            if (minutesDelta == 0) stepsPerMinute = 0.0f
            return when {
                stepsPerMinute < 5 -> HRRecordsTable.AnalyticTypes.Steady
                stepsPerMinute < 10 -> HRRecordsTable.AnalyticTypes.LowPhysical
                stepsPerMinute < 15 -> HRRecordsTable.AnalyticTypes.MediumPhysical
                else -> HRRecordsTable.AnalyticTypes.PhysicalStress
            }
        }

        private fun getDeltaMinutes(calendarPrev: Calendar, calendarNext: Calendar): Int{
            val millis = calendarNext.timeInMillis-calendarPrev.timeInMillis
            val seconds = millis/1000
            return seconds.toInt()/60
        }
        fun analyzeShadowMainData(Operator: SQLiteDatabase){
            val taskforce = AnalyzeSMD()
            taskforce.Operator = Operator
            taskforce.execute()
            isShadowAnalyzerRunning = true
        }


    class AnalyzeSMD: AsyncTask<Void, Void, Void>() {
        var Operator: SQLiteDatabase? = null;
        private var ready = true

        override fun doInBackground(vararg params: Void?): Void? {
            val curs = Operator!!.query(DatabaseController.MainRecordsTableName+"COPY",
                    arrayOf(MainRecordsTable.ColumnNames[0],MainRecordsTable.ColumnNames[2],MainRecordsTable.ColumnNames[1]), null, null, null,null, "Date")
            if (curs.count == 0) return null
            ready = false
            var prevSteps = -1
            var prevTime: Long = -1
            var prevID: Long = -1
            curs.moveToFirst()
            do{
                val currentSteps = curs.getInt(1)
                val currentTime = curs.getLong(2)
                val currentID = curs.getLong(0)
                if (prevSteps < 0 || (prevSteps > 0 && currentSteps == 0)){  prevSteps = currentSteps; prevTime = currentTime; prevID = currentID; continue; }
                val deltaSteps = currentSteps-prevSteps
                val deltaTime = getDeltaMinutes(CustomDatabaseUtils.LongToCalendar(prevTime, true), CustomDatabaseUtils.LongToCalendar(currentTime, true))
                val phStress = physicalStressDetermining(deltaSteps, deltaTime)
                val values = ContentValues()
                values.put(HRRecordsTable.ColumnsNames[3], HRRecordsTable.AnalyticTypes.valueOf(phStress.name).type)
                val test = Operator!!.update(DatabaseController.HRRecordsTableName, values,
                        " "+HRRecordsTable.ColumnsNames[1]+" BETWEEN ? AND ? ",
                        arrayOf(prevTime.toString(), currentTime.toString()))
                prevSteps = currentSteps
                prevTime = currentTime
                Operator!!.delete(DatabaseController.MainRecordsTableName+"COPY", " DATE = ?", arrayOf(prevID.toString()))
                prevID = currentID
            }while (curs.moveToNext())
            curs.close()
            Operator!!.delete(DatabaseController.MainRecordsTableName+"COPY", " DATE = ?", arrayOf(prevID.toString()))
            return null
        }

        override fun onPostExecute(result: Void?) {
            isShadowAnalyzerRunning = false
            if (!ready) Algorithm.SelfPointer?.additionalStatus = "Data analyzer complete"
            super.onPostExecute(result)
        }
    }
}