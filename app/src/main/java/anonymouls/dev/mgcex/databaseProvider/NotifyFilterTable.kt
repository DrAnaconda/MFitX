package anonymouls.dev.mgcex.databaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@ExperimentalStdlibApi
object NotifyFilterTable {
    private val ColumnNames = arrayOf("ID", "Package", "Enabled")

    fun getCreateTableCommand(): String {
        return "create table if not exists " + DatabaseController.NotifyFilterTableName +
                "(" + ColumnNames[0] + " integer primary key," +
                ColumnNames[1] + " string unique," +
                ColumnNames[2] + " boolean);"
    }

    fun insertRecord(Package: String, IsEnabled: Boolean, Operator: SQLiteDatabase) {
        GlobalScope.launch(Dispatchers.IO) {
            if (Package.length > 6) {
                val CV = ContentValues()
                CV.put(ColumnNames[1], Package)
                CV.put(ColumnNames[2], IsEnabled)
                if (!isEnabled(Package, Operator)){
                    Operator.insert(DatabaseController.NotifyFilterTableName, null, CV)
                } else {
                    Operator.delete(DatabaseController.NotifyFilterTableName, "${ColumnNames[1]} = \"$Package\"" ,null)
                }
            }
        }
    }

    fun dropRecord(Package: String, Operator: SQLiteDatabase) {
        Operator.delete(DatabaseController.NotifyFilterTableName, ColumnNames[1] + " = '" + Package + "'", null)
    }

    fun extractRecords(Operator: SQLiteDatabase): Cursor? {
        val req = Operator.query(DatabaseController.NotifyFilterTableName,
                arrayOf(ColumnNames[1], ColumnNames[2]), null, null, null, null, null)
        if (req != null && req.count > 0) req.moveToFirst()
        return req
    }

    fun isEnabled(Package: String, Operator: SQLiteDatabase): Boolean {
        val req = Operator.query(DatabaseController.NotifyFilterTableName,
                arrayOf(ColumnNames[2]),
                ColumnNames[1] + " = \"" + Package + "\"", null, null, null, null, null)
        return if (req != null && req.count > 0) {
            req.moveToFirst()
            if (req.getInt(0) == 1) {
                req.close()
                true
            } else {
                req.close()
                false
            }
        } else {
            req?.close()
            false
        }
    }
}
