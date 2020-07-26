package anonymouls.dev.mgcex.app.backend

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

import anonymouls.dev.mgcex.util.PreferenceListener
import anonymouls.dev.mgcex.util.Utils

@ExperimentalStdlibApi
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ServiceRessurecter : JobService() {

    @ExperimentalStdlibApi
    companion object {
        private const val JobID = 66
        private var isSchleduled = false

        fun startJob(context: Context) {
            synchronized(isSchleduled) {
                if (isSchleduled) return
                val component = ComponentName(context, ServiceRessurecter::class.java)
                val pendingJob = JobInfo.Builder(JobID, component)
                pendingJob.setPeriodic(
                        Utils.getSharedPrefs(context).getInt(
                                PreferenceListener.Companion.PrefsConsts.disconnectedMonitoring, 5).toLong() * 1000 * 60)

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.schedule(pendingJob.build())
                isSchleduled = true
            }
        }
        fun cancelJob(context: Context){
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JobID)
            isSchleduled = false
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        MultitaskListener.ressurectService(this)
        return true

    }

    override fun onStartJob(params: JobParameters?): Boolean {
        MultitaskListener.ressurectService(this)
        return true
    }


}