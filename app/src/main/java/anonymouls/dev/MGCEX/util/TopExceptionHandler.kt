package anonymouls.dev.MGCEX.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class TopExceptionHandler(context: Context) : Thread.UncaughtExceptionHandler {

    private var defaultUEH: Thread.UncaughtExceptionHandler? = null
    private var context: Context? = null

    init {
        //this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        //this.context = context
        //Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        var output = "------------------    STACK TRACE    ------------------\n"
        output += "Thread: "+t.name+"\n"
        val stackElements =  e.stackTrace
        for(element in stackElements){
            output += "---------------------------------\n"
            output += "File name: "+element.fileName+"\n"
            output += "Class Name: "+element.className+" || Method name:"+element.methodName+": "+element.lineNumber+" line \n"
        }
        output += "Message: "+e.message+"\n"
        output += "------------------    CAUSE    ------------------\n"
        if (e.cause != null){
            val causeElements = e.cause!!.stackTrace
            for(element in causeElements){
                output += "---------------------------------\n"
                output += "File name: "+element.fileName+"\n"
                output += "Class Name: "+element.className+" || Method name:"+element.methodName+": "+element.lineNumber+" line \n"
            }
        }
        val sdf = SimpleDateFormat("dMYYYYHHmmss", Locale.getDefault())
        var path = File(Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" +
                File.separator + "dev.anonymouls.MGCEX");
        if (!path.exists()) path.mkdirs()
        try {
            val outputStreamWriter = OutputStreamWriter(FileOutputStream(
                    Environment.getExternalStorageDirectory().toString() + File.separator + "Android" + File.separator + "Data" +
                    File.separator + "dev.anonymouls.MGCEX" + File.separator
                    + t.name + sdf.format(Calendar.getInstance().time) + ".log", false))
            outputStreamWriter.write(output)
            outputStreamWriter.flush()
            outputStreamWriter.close()
        }catch (ex: Exception){
            ex.message
        }
        Analytics.getInstance(null)!!.recordCrash(e)

        defaultUEH!!.uncaughtException(t, e)
    }
}