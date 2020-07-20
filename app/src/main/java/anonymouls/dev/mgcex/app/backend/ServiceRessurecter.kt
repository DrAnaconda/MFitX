package anonymouls.dev.mgcex.app.backend

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import anonymouls.dev.mgcex.app.main.SettingsActivity
import anonymouls.dev.mgcex.util.Utils

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ServiceRessurecter : JobService() {

    @ExperimentalStdlibApi
    companion object {
        private const val JobID = 66

        fun startJob(context: Context) {
            val component = ComponentName(context, ServiceRessurecter::class.java)
            val pendingJob = JobInfo.Builder(JobID, component)
            pendingJob.setPeriodic(
                    Utils.getSharedPrefs(context).getInt(
                            SettingsActivity.disconnectedMonitoring, 5).toLong() * 1000 * 60)
        }
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        TODO("Not yet implemented")
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        TODO("Not yet implemented")
    }

}