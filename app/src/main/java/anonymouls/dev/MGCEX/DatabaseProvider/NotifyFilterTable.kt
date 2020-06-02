package anonymouls.dev.MGCEX.DatabaseProvider

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.ContactsContract
import anonymouls.dev.MGCEX.DatabaseProvider.NotifyFilterTable.ColumnNames
import java.lang.Exception

object NotifyFilterTable {
    private val ColumnNames = arrayOf("ID", "Package", "Enabled")

    fun GetCreateTableCommand(): String {
        return "create table " + DatabaseController.NotifyFilterTableName +
                "(" + ColumnNames[0] + " integer primary key," +
                ColumnNames[1] + " string unique," +
                ColumnNames[2] + " boolean);"
    }

    fun InsertRecord(Package: String, IsEnabled: Boolean, Operator: SQLiteDatabase) {
        if (Package.length < 6) return
        val CV = ContentValues()
        CV.put(ColumnNames[1], Package)
        CV.put(ColumnNames[2], IsEnabled)
        try {
            Operator.insert(DatabaseController.NotifyFilterTableName, null, CV)
        }catch (ex: Exception){
            CV.put(ColumnNames[2], IsEnabled)
            Operator.update(DatabaseController.NotifyFilterTableName, CV, ColumnNames[1]+" = '"+Package+"'", null)
        }
    }

    fun UpdateRecord(Package: String, IsEnable: Boolean, Operator: SQLiteDatabase) {
        val CV = ContentValues()
        CV.put(ColumnNames[2], IsEnable)
        Operator.update(DatabaseController.NotifyFilterTableName, CV, ColumnNames[1]
                + " = \"" + Package + "\"", null)
    }

    fun dropRecord(Package: String, Operator:  SQLiteDatabase){
        Operator.delete(DatabaseController.NotifyFilterTableName, ColumnNames[1]+" = '"+Package+"'", null )
    }

    fun ExtractRecords(Operator: SQLiteDatabase): Cursor? {
        val Req = Operator.query(DatabaseController.NotifyFilterTableName,
                arrayOf(ColumnNames[1], ColumnNames[2]), null, null, null, null, null)
        if (Req != null && Req.count > 0) Req.moveToFirst()
        return Req
    }

    fun IsEnabled(Package: String, Operator: SQLiteDatabase): Boolean {
        val Req = Operator.query(DatabaseController.NotifyFilterTableName,
                arrayOf(ColumnNames[2]),
                ColumnNames[1] + " = \"" + Package + "\"", null, null, null, null, null)
        if (Req != null && Req.count > 0) {
            Req.moveToFirst()
            return if (Req.getInt(0) == 1)
                true
            else
                false
        } else {
            return false
        }
    }
}
