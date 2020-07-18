package anonymouls.dev.mgcex.app.backend

import android.os.AsyncTask
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import anonymouls.dev.mgcex.util.Utils
import java.util.*
import kotlin.collections.ArrayDeque

@ExperimentalStdlibApi
class InsertTask(private var ci: CommandInterpreter) : AsyncTask<Void, Void, Void>() {

    @ExperimentalStdlibApi
    val dataToHandle = ArrayDeque<SimpleRecord>()
    lateinit var thread: Thread

    @ExperimentalStdlibApi
    override fun doInBackground(vararg params: Void?): Void? {
        Thread.currentThread().name = "Database Inserter"
        thread = Thread.currentThread()
        while (true) {
            if (dataToHandle.size > 0) _insertedRunning.postValue(true)
            while (dataToHandle.size > 0) {
                try {
                    val sm = dataToHandle.removeFirst()
                    ci.commandAction(sm.Data, UUID.fromString(sm.characteristic))
                    if (dataToHandle.size > 500)
                        _insertedRunning.postValue(true)
                    else
                        _insertedRunning.postValue(false)

                } catch (ex: Exception) {
                    ex.message
                }
                if (dataToHandle.size > 2500) dataToHandle.clear()
            }
            _insertedRunning.postValue(false)
            Utils.safeThreadSleep(Integer.MAX_VALUE.toLong(), false)
        }
    }

    companion object {
        private var _insertedRunning = MutableLiveData<Boolean>(false)

        val insertedRunning: LiveData<Boolean>
            get() {
                return _insertedRunning
            }

    }
}