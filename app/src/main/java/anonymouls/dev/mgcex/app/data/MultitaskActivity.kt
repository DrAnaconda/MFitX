package anonymouls.dev.mgcex.app.data

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.HRRecordsTable
import anonymouls.dev.mgcex.databaseProvider.MainRecordsTable
import anonymouls.dev.mgcex.util.AdsController
import anonymouls.dev.mgcex.util.Analytics
import anonymouls.dev.mgcex.util.HRAnalyzer
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private var container: LinearLayout? = null

@ExperimentalStdlibApi
class MultitaskActivity : Activity() {

    companion object {
        const val TaskTypeIntent = "TaskType"
        const val TextIntent = "Text"
    }

    enum class TaskTypes(val type: Int) { OverallStats(0), SimpleText(1) }

    private lateinit var taskType: TaskTypes
    private var text: String? = null

//region default android

    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        AdsController.cancelBigAD()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdsController.showUltraRare(this)
        setContentView(R.layout.activity_report_activity)
        val bundle = intent.extras
        initViews()
        val task = bundle!!.getInt(TaskTypeIntent)
        when (task) {
            0 -> this.taskType = TaskTypes.OverallStats
            1 -> this.taskType = TaskTypes.SimpleText
            else -> TaskTypes.OverallStats
        }
        loaderDecider(bundle)
    }

//endregion

    private fun loaderDecider(bundle: Bundle?) {
        when (taskType) {
            TaskTypes.OverallStats -> GlobalScope.launch(Dispatchers.IO) { fillOverallStatsData() }
            TaskTypes.SimpleText -> {
                this.text = bundle?.getString(TextIntent, "")
                loadText()
            }
            //else -> finish()
        }
    }


    //region Stats Task
    private fun initViews() {
        container = findViewById(R.id.mainContainer)
    }

    private fun fillOverallStatsData() {
        createTextView(getString(R.string.overall_stats), true)
        initDataBlock(null, null)
        createTextView(getString(R.string.today_report), true)

        var from = Calendar.getInstance()
        val to = Calendar.getInstance()

        from.set(Calendar.HOUR_OF_DAY, 0)
        to.set(Calendar.HOUR_OF_DAY, 23)
        to.set(Calendar.MINUTE, 59)
        val data = initDataBlock(from, to)
        Analytics.getInstance(this)?.sendHRData(data.MinHR, data.AvgHR, data.MaxHR)

        val SDF = SimpleDateFormat(Utils.SDFPatterns.OverallStats.pattern, Locale.getDefault())
        from.add(Calendar.DAY_OF_MONTH, -7)
        createTextView(getString(R.string.last_week_stats) + SDF.format(from.time) + " — " + SDF.format(to.time) + " )", true)
        initDataBlock(from, to)

        from = Calendar.getInstance()
        from.set(Calendar.HOUR_OF_DAY, 0)
        from.set(Calendar.DAY_OF_MONTH, 1)
        from.add(Calendar.MONTH, -1)
        createTextView(getString(R.string.last_month_stats) + SDF.format(from.time) + " — " + SDF.format(to.time) + " )", true)
        initDataBlock(from, to)

    }

    private fun createTextView(headerText: String, bold: Boolean) {
        val tt = TextView(this)
        tt.text = headerText
        val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (bold) {
            tt.setTypeface(null, Typeface.BOLD)
            layoutParams.setMargins(0, 8, 0, 0)
        } else layoutParams.setMargins(0, 4, 0, 4)
        tt.layoutParams = layoutParams
        runOnUiThread { container!!.addView(tt) }
    }

    @SuppressLint("SetTextI18n")
    private fun initDataBlock(fromPeriod: Calendar?, toPeriod: Calendar?): HRRecordsTable.HRReport {

        runOnUiThread { LayoutInflater.from(this).inflate(R.xml.horizontal_divider, container!!) }

        val overallMainReport = MainRecordsTable.generateReport(fromPeriod, toPeriod, DatabaseController.getDCObject(this).readableDatabase, this)
        val overallHRReport = HRRecordsTable.generateReport(fromPeriod, toPeriod, DatabaseController.getDCObject(this).readableDatabase)
        var overallAnalytics = ""

        if (overallMainReport.stepsCount == 0)
            createTextView(getString(R.string.no_activity_data), false)
        else {
            createTextView(getString(R.string.passed) + Utils.bigNumberToString(overallMainReport.passedKm.toInt(), 1000) + getString(R.string.kilos_with)
                    + Utils.bigNumberToString(overallMainReport.stepsCount, 1) + getString(R.string.steps), false)
            createTextView(getString(R.string.burned_calories) + Utils.bigNumberToString(overallMainReport.caloriesCount, 1), false)
            createTextView(getString(R.string.based_on) + Utils.bigNumberToString(overallMainReport.recordsCount, 1) + getString(R.string.activity_records),
                    false)
        }

        if (overallHRReport.recordsCount == 0) {
            createTextView(getString(R.string.no_hearth_rate_data), false)
        } else {
            var hrAnalytics = getString(R.string.min_HR) + overallHRReport.MinHR + getString(R.string.avg_HR) + overallHRReport.AvgHR + getString(R.string.max_HR) + overallHRReport.MaxHR + "\n"
            hrAnalytics += getString(R.string.anomalies) + overallHRReport.anomaliesPercent + "%\n"
            hrAnalytics += getString(R.string.based_on) + Utils.bigNumberToString(overallHRReport.recordsCount, 1) + getString(R.string.HR_records)
            createTextView(hrAnalytics, false)

            when (HRAnalyzer.determineHRMainType(overallHRReport.AvgHR)) {
                HRAnalyzer.MainHRTypes.Tahicardio -> overallAnalytics += getString(R.string.pathological_tachycardia) + "\n"
                HRAnalyzer.MainHRTypes.Bradicardio -> overallAnalytics += getString(R.string.confident_bradycardia) + "\n"
                else -> {
                }
            }
        }

        if (overallAnalytics.isNotEmpty()) {
            createTextView(overallAnalytics.substring(0, overallAnalytics.length - 1), false)
        }
        return overallHRReport
    }
//endregion

    //region display text
    private fun loadText() {
        container?.removeAllViews()
        val text = TextView(this)
        text.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        text.text = this.text
        text.isVerticalScrollBarEnabled = true
        text.movementMethod = ScrollingMovementMethod()
        text.textSize = 24.0f
        text.setPadding(1, 4, 1, 16)
        container?.addView(text)
    }
//endregion

}
