package anonymouls.dev.mgcex.app.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.databaseProvider.DatabaseController
import anonymouls.dev.mgcex.databaseProvider.HRRecordsTable
import anonymouls.dev.mgcex.databaseProvider.MainRecordsTable
import anonymouls.dev.mgcex.util.FireAnalytics
import anonymouls.dev.mgcex.util.HRAnalyzer
import anonymouls.dev.mgcex.util.Utils
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

@ExperimentalStdlibApi
class MultitaskFragment : Fragment() {

    private val job = CoroutineScope(Job())
    lateinit var savedContext: Activity

    private val generatedViews: Queue<View?> = LinkedList()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        job.launch(Dispatchers.Default) { synchronized(generatedViews) { loadData(); } }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        savedContext = this.requireActivity()
        job.launch(Dispatchers.Default) { dequeViews() }
        super.onActivityCreated(savedInstanceState)
    }

    @SuppressLint("ResourceType")
    private fun dequeViews() {
        synchronized(generatedViews) {
            val container = savedContext.findViewById<LinearLayout>(R.id.mainContainer)
            do {
                if (generatedViews.size == 0) continue
                val insert = generatedViews.remove()
                if (insert != null) {
                    savedContext.runOnUiThread { container.addView(insert) }
                } else {
                    savedContext.runOnUiThread {
                        LayoutInflater.from(this.requireActivity())
                                .inflate(R.xml.horizontal_divider, container)
                    }
                }
            } while (generatedViews.size > 0)
        }
    }

    private fun loadData() {
        createTextView(getString(R.string.overall_stats), true)
        initDataBlock(null, null)
        createTextView(getString(R.string.today_report), true)

        var from = Calendar.getInstance()
        val to = Calendar.getInstance()

        from.set(Calendar.HOUR_OF_DAY, 0)
        to.set(Calendar.HOUR_OF_DAY, 23)
        to.set(Calendar.MINUTE, 59)
        val data = initDataBlock(from, to)
        if (this::savedContext.isInitialized)
            FireAnalytics.getInstance(savedContext).sendHRData(data.MinHR, data.AvgHR, data.MaxHR)

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
        val tt = TextView(this.requireContext())
        tt.text = headerText
        val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (bold) {
            tt.setTypeface(null, Typeface.BOLD)
            layoutParams.setMargins(0, 8, 0, 0)
        } else layoutParams.setMargins(0, 4, 0, 4)
        tt.layoutParams = layoutParams
        generatedViews.add(tt)
    }

    @SuppressLint("SetTextI18n", "ResourceType")
    private fun initDataBlock(fromPeriod: Calendar?, toPeriod: Calendar?): HRRecordsTable.HRReport {

        //LayoutInflater.from(this.requireActivity()).inflate(R.xml.horizontal_divider, container)
        generatedViews.add(null)

        val overallMainReport = MainRecordsTable.generateReport(fromPeriod, toPeriod,
                DatabaseController.getDCObject(this.requireActivity()).readableDatabase, this.requireActivity())
        val overallHRReport = HRRecordsTable.generateReport(fromPeriod, toPeriod,
                DatabaseController.getDCObject(this.requireActivity()).readableDatabase)
        var overallAnalytics = ""

        if (overallMainReport.stepsCount == 0)
            createTextView(getString(R.string.no_activity_data), false)
        else {
            createTextView(getString(R.string.passed) + Utils.bigNumberToString(overallMainReport.passedKm.toInt(), 1000) + getString(R.string.kilos_with)
                    + Utils.bigNumberToString(overallMainReport.stepsCount, 1) + getString(R.string.steps), false)
            createTextView(getString(R.string.burned_calories) + Utils.bigNumberToString(overallMainReport.caloriesCount, 1), false)
            createTextView(getString(R.string.based_on) +
                    Utils.bigNumberToString(overallMainReport.recordsCount, 1) + getString(R.string.activity_records),
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_multitask, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance() = MultitaskFragment()
    }
}