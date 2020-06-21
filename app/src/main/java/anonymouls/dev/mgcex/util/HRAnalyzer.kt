@file:Suppress("NAME_SHADOWING")

package anonymouls.dev.mgcex.util

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.databaseProvider.*
import java.util.*
import kotlin.math.abs

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

    fun analyzeShadowMainData(operator: SQLiteDatabase) {
        val curs = operator.query(MainRecordsTable.TableName + "COPY",
                arrayOf(MainRecordsTable.ColumnNames[0], MainRecordsTable.ColumnNames[2], MainRecordsTable.ColumnNames[1], MainRecordsTable.ColumnNamesCloneAdditional[0]),
                MainRecordsTable.ColumnNamesCloneAdditional[0] + " = 0",
                null, null, null, "Date")
        if (curs.count == 0) {
            curs.close(); return
        }
        var prevSteps = -1
        var prevTime: Long = -1
        var prevID: Long = -1
        var errorCount = 0
        curs?.moveToFirst()
        isShadowAnalyzerRunning.postValue(true)
        do {
            try {
                val currentSteps = curs.getInt(1)
                val currentTime = curs.getLong(2)
                val currentID = curs.getLong(0)
                if (prevSteps < 0 || (prevSteps > 0 && currentSteps == 0)) {
                    prevSteps = currentSteps; prevTime = currentTime; prevID = currentID; continue; }
                val deltaSteps = currentSteps - prevSteps
                val deltaTime = getDeltaMinutes(CustomDatabaseUtils.LongToCalendar(prevTime, true),
                        CustomDatabaseUtils.LongToCalendar(currentTime, true))

                analyzeActivity(deltaSteps, deltaTime, prevTime, currentTime, operator)
                recordAnalyzeIntermediate(currentTime, deltaTime, deltaSteps, operator)

                prevSteps = currentSteps
                prevTime = currentTime
                //operator.delete(MainRecordsTable.TableName + "COPY", " DATE = ?", arrayOf(prevID.toString()))
                val content = ContentValues(); content.put(MainRecordsTable.ColumnNamesCloneAdditional[0], true)
                operator.update(MainRecordsTable.TableName + "COPY", content, "ID = $currentID", null)
                prevID = currentID
            } catch (ex: Exception) {
                Thread.sleep(15000)
                if (errorCount++ > 2) break
            }
        } while (curs.moveToNext())
        curs.close()
        //operator.delete(MainRecordsTable.TableName + "COPY", " DATE = ?", arrayOf(prevID.toString()))
        val content = ContentValues(); content.put(MainRecordsTable.ColumnNamesCloneAdditional[0], true)
        operator.update(MainRecordsTable.TableName + "COPY", content, "ID = $prevID", null)
        isShadowAnalyzerRunning.postValue(false)
    }


    private fun analyzeActivity(deltaSteps: Int, deltaTime: Int, prevTime: Long, currentTime: Long, operator: SQLiteDatabase) {
        val phStress = physicalStressDetermining(deltaSteps, deltaTime)
        val values = ContentValues()
        values.put(HRRecordsTable.ColumnsNames[3], HRRecordsTable.AnalyticTypes.valueOf(phStress.name).type)
        operator.update(DatabaseController.HRRecordsTableName, values,
                " " + HRRecordsTable.ColumnsNames[1] + " BETWEEN ? AND ? ",
                arrayOf(prevTime.toString(), currentTime.toString()))
    }

    private fun recordAnalyzeIntermediate(recordStart: Long, deltaMinutes: Int, deltaSteps: Int, operator: SQLiteDatabase) {
        val speed: Double = abs(deltaSteps.toDouble()) / deltaMinutes
        AdvancedActivityTracker.insertRecord(recordStart, deltaMinutes, speed, operator)
    }
}