package anonymouls.dev.mgcex.app.data

import android.app.Activity
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.SwitchCompat
import anonymouls.dev.mgcex.app.R
import anonymouls.dev.mgcex.databaseProvider.NotifyFilterTable
import com.evrencoskun.tableview.adapter.AbstractTableAdapter
import com.evrencoskun.tableview.adapter.recyclerview.holder.AbstractViewHolder

abstract class Cell

open class TextCell(val data: String):Cell() {
    companion object {
        fun <T> listTextRowToCells(list: Array<String>, isHeader: Boolean): ArrayList<T> {
            val result = ArrayList<T>()
            for (x in list.indices) {
                if (!isHeader)
                    result.add(TextCell(list[x].toString()) as T)
                else
                    result.add(ColumnHeader(list[x].toString()) as T)
            }
            return result
        }
    }
}

open class ImageCell(val image: Drawable): Cell()
open class SwitchCell(val isActive: Boolean): Cell()

@ExperimentalStdlibApi
open class ApplicationRow(private val pack: String,
                          val image: Drawable,
                          val name: String,
                          var isActive: Boolean) : Cell(), IExtractable {
    override fun getCellsList(): MutableList<Cell> {
        val result = ArrayList<Cell>()
        result.add(ImageCell(image))
        result.add(TextCell(name))
        result.add(SwitchCell(isActive))
        return result
    }
    fun updateInfo(isActive: Boolean, db: SQLiteDatabase){
        this.isActive = isActive
        NotifyFilterTable.insertRecord(this.pack, this.isActive, db)
    }
}


class ColumnHeader(data: String) : TextCell(data)
class RowHeader(data: String) : TextCell(data)

class DoubleTaskTableViewAdapter(private val context: Activity,
                                 private val DataType: DataTypes) : AbstractTableAdapter<ColumnHeader?, RowHeader?, Cell?>() {

    enum class DataTypes(val code: Int) {TextOnly(0), ApplicationMode(1)}

    var countRows = 0
    lateinit var callback: ((v: View, row: Int, column: Int) -> Any)


    /**
     * This is sample CellViewHolder class
     * This viewHolder must be extended from AbstractViewHolder class instead of RecyclerView.ViewHolder.
     */
    internal inner class MyCellViewHolder(itemView: View) : AbstractViewHolder(itemView) {
        val cell_container: LinearLayout = itemView.findViewById(R.id.cell_container)
        val cell_textview: TextView? = itemView.findViewById(R.id.cell_data)
        val cell_image: ImageView? = itemView.findViewById(R.id.cell_image)
        val cell_switch: SwitchCompat? = itemView.findViewById(R.id.cell_switch)
    }

    /**
     * This is where you create your custom Cell ViewHolder. This method is called when Cell
     * RecyclerView of the TableView needs a new RecyclerView.ViewHolder of the given type to
     * represent an item.
     *
     * @param viewType : This value comes from #getCellItemViewType method to support different type
     * of viewHolder as a Cell item.
     *
     * @see .getCellItemViewType
     */
    override fun onCreateCellViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {
        // Get cell xml layout
        var layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.table_view_simple_cell, parent, false)
        when(DataType){
            DataTypes.ApplicationMode->{
                when(viewType){
                    0-> layout = LayoutInflater.from(parent.context)
                            .inflate(R.layout.table_view_image_cell, parent, false)
                    2-> layout = LayoutInflater.from(parent.context)
                            .inflate(R.layout.table_view_switch_cell, parent, false)
                }
            }
            else -> {} // ignore
        }
        // Create a Custom ViewHolder for a Cell item.
        return MyCellViewHolder(layout)
    }
    private fun bindNightCompat(viewHolder: MyCellViewHolder){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val typedValue = TypedValue()
            val theme = context.theme
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            @ColorInt val color = typedValue.data
            when (context.resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
                Configuration.UI_MODE_NIGHT_YES -> {
                    viewHolder.setBackgroundColor(color)
                    viewHolder.cell_container.setBackgroundColor(color)
                    viewHolder.cell_textview?.setTextColor(context.getColor(android.R.color.white))
                    viewHolder.cell_switch?.setBackgroundColor(color)
                    viewHolder.cell_image?.setBackgroundColor(color)
                }
            }
        }
    }
    private fun initCallback(view: View, col: Int, row: Int){
        if (this::callback.isInitialized)
            this.callback.invoke(view, row, col)
    }

    override fun onBindCellViewHolder(holder: AbstractViewHolder, cellItemModel: Cell?, columnPosition: Int, rowPosition: Int) {
        val viewHolder = holder as MyCellViewHolder
        bindNightCompat(viewHolder)
        when(cellItemModel){
            is TextCell -> viewHolder.cell_textview?.text = cellItemModel.data
            is ImageCell -> viewHolder.cell_image?.setImageDrawable(cellItemModel.image)
            is SwitchCell -> {
                viewHolder.cell_switch?.isChecked = cellItemModel.isActive
                viewHolder.cell_switch?.setOnClickListener { v ->  initCallback(v, columnPosition, rowPosition) } // TODO
            }
        }

        // If your TableView should have auto resize for cells & columns.
        // Then you should consider the below lines. Otherwise, you can ignore them.

        // It is necessary to remeasure itself.
        if (DataType != DataTypes.ApplicationMode)
            calculateSizeFirstBiggerOrSmaller(viewHolder.cell_container, columnPosition, this.mColumnHeaderItems.size - 1, true)
        else
            calculateSizeForApplication(viewHolder.cell_container, columnPosition)
    }

    fun removeEverything() {
        try {
            if (countRows > 0) this.removeRowRange(0, countRows)
        } catch (e: Exception) {
        }
        countRows = 0
        this.setAllItems(null, null, null)
    }


    private fun calculateSizeForApplication(holder: View, position: Int){
        val size: Point = Point(1, 1)
        context.windowManager.defaultDisplay.getSize(size)
        if (position == 0 || position == 2)
                holder.layoutParams.width = (size.x / 5)
        else
                holder.layoutParams.width = size.x - ((size.x / 5)*2)

        holder.requestLayout()
    }
    private fun calculateSizeFirstBiggerOrSmaller(holder: View, position: Int, otherColumns: Int,
            firstBigger: Boolean) {
        val size: Point = Point(1, 1)
        context.windowManager.defaultDisplay.getSize(size)
        if (position == 0) {
            if (firstBigger)
                holder.layoutParams.width = (size.x / 2)
            else
                holder.layoutParams.width = (size.x / 6)
        }else {
            if (firstBigger)
                holder.layoutParams.width = ((size.x / 2) / otherColumns)
            else
                holder.layoutParams.width = (size.x - (size.x / 6)) / otherColumns
        }
        holder.requestLayout()
    }

    /**
     * That is where you set Cell View Model data to your custom Cell ViewHolder. This method is
     * Called by Cell RecyclerView of the TableView to display the data at the specified position.
     * This method gives you everything you need about a cell item.
     *
     * @param holder       : This is one of your cell ViewHolders that was created on
     * ```onCreateCellViewHolder``` method. In this example, we have created
     * "MyCellViewHolder" holder.
     * @param cellItemModel     : This is the cell view model located on this X and Y position. In this
     * example, the model class is "Cell".
     * @param columnPosition : This is the X (Column) position of the cell item.
     * @param rowPosition : This is the Y (Row) position of the cell item.
     *
     * @see .onCreateCellViewHolder
     */

    /**
     * This is sample ColumnHeaderViewHolder class.
     * This viewHolder must be extended from AbstractViewHolder class instead of RecyclerView.ViewHolder.
     */
    internal inner class MyColumnHeaderViewHolder(itemView: View) : AbstractViewHolder(itemView) {
        val column_header_container: LinearLayout = itemView.findViewById(R.id.column_header_container)
        val cell_textview: TextView = itemView.findViewById(R.id.column_header_textView)

    }

    /**
     * This is where you create your custom Column Header ViewHolder. This method is called when
     * Column Header RecyclerView of the TableView needs a new RecyclerView.ViewHolder of the given
     * type to represent an item.
     *
     * @param viewType : This value comes from "getColumnHeaderItemViewType" method to support
     * different type of viewHolder as a Column Header item.
     *
     * @see .getColumnHeaderItemViewType
     */
    override fun onCreateColumnHeaderViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {
        val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.table_view_column_header, parent, false)
        if (DataType == DataTypes.ApplicationMode){
            layout.visibility = View.GONE
            parent.visibility = View.GONE
        }
        // Get Column Header xml Layout
        // Create a ColumnHeader ViewHolder
        return MyColumnHeaderViewHolder(layout)
    }

    override fun onBindColumnHeaderViewHolder(holder: AbstractViewHolder, columnHeaderItemModel: ColumnHeader?, columnPosition: Int) {
        val columnHeader = columnHeaderItemModel as ColumnHeader

        // Get the holder to update cell item text
        val columnHeaderViewHolder = holder as MyColumnHeaderViewHolder
        columnHeaderViewHolder.cell_textview.text = columnHeader.data

        if (DataType != DataTypes.ApplicationMode)
            calculateSizeFirstBiggerOrSmaller(columnHeaderViewHolder.column_header_container, columnPosition, this.mColumnHeaderItems.size - 1, true)
        else {
            columnHeaderViewHolder.column_header_container.visibility = View.GONE
            calculateSizeForApplication(columnHeaderViewHolder.column_header_container, columnPosition)
        }
    }

    /**
     * That is where you set Column Header View Model data to your custom Column Header ViewHolder.
     * This method is Called by ColumnHeader RecyclerView of the TableView to display the data at
     * the specified position. This method gives you everything you need about a column header
     * item.
     *
     * @param holder   : This is one of your column header ViewHolders that was created on
     * ```onCreateColumnHeaderViewHolder``` method. In this example we have created
     * "MyColumnHeaderViewHolder" holder.
     * @param columnHeaderItemModel : This is the column header view model located on this X position. In this
     * example, the model class is "ColumnHeader".
     * @param position : This is the X (Column) position of the column header item.
     *
     * @see .onCreateColumnHeaderViewHolder
     */

    /**
     * This is sample RowHeaderViewHolder class.
     * This viewHolder must be extended from AbstractViewHolder class instead of RecyclerView.ViewHolder.
     */
    internal inner class MyRowHeaderViewHolder(itemView: View) : AbstractViewHolder(itemView) {
        val cell_textview: TextView = itemView.findViewById(R.id.cell_data)
    }

    /**
     * This is where you create your custom Row Header ViewHolder. This method is called when
     * Row Header RecyclerView of the TableView needs a new RecyclerView.ViewHolder of the given
     * type to represent an item.
     *
     * @param viewType : This value comes from "getRowHeaderItemViewType" method to support
     * different type of viewHolder as a row Header item.
     *
     * @see .getRowHeaderItemViewType
     */
    override fun onCreateRowHeaderViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {

        // Get Row Header xml Layout
        val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.table_view_row_header_layout, parent, false)
        // Create a Row Header ViewHolder

        return MyRowHeaderViewHolder(layout)
    }

    override fun onBindRowHeaderViewHolder(holder: AbstractViewHolder, rowHeaderItemModel: RowHeader?, rowPosition: Int) {
        val rowHeader = rowHeaderItemModel as RowHeader

        val rowHeaderViewHolder = holder as MyRowHeaderViewHolder
        rowHeaderViewHolder.cell_textview.text = rowHeader.data
        holder.itemView.layoutParams.width = 0
    }

    /**
     * That is where you set Row Header View Model data to your custom Row Header ViewHolder. This
     * method is Called by RowHeader RecyclerView of the TableView to display the data at the
     * specified position. This method gives you everything you need about a row header item.
     *
     * @param holder   : This is one of your row header ViewHolders that was created on
     * ```onCreateRowHeaderViewHolder``` method. In this example, we have created
     * "MyRowHeaderViewHolder" holder.
     * @param rowHeaderItemModel : This is the row header view model located on this Y position. In this
     * example, the model class is "RowHeader".
     * @param position : This is the Y (row) position of the row header item.
     *
     * @see .onCreateRowHeaderViewHolder
     */

    override fun onCreateCornerView(parent: ViewGroup): View {
        // Get Corner xml layout
        return LayoutInflater.from(parent.context)
                .inflate(R.layout.table_view_corner_layout, parent, false)
    }

    override fun getColumnHeaderItemViewType(columnPosition: Int): Int {
        // The unique ID for this type of column header item
        // If you have different items for Cell View by X (Column) position,
        // then you should fill this method to be able create different
        // type of ColumnViewHolder on "onCreateColumnViewHolder"
        return 0
    }

    override fun getRowHeaderItemViewType(rowPosition: Int): Int {
        // The unique ID for this type of row header item
        // If you have different items for Row Header View by Y (Row) position,
        // then you should fill this method to be able create different
        // type of RowHeaderViewHolder on "onCreateRowHeaderViewHolder"
        return 0
    }

    override fun getCellItemViewType(columnPosition: Int): Int {
        return when(DataType){
            DataTypes.ApplicationMode -> columnPosition
            else -> 0
        }
    }

    companion object{
        fun emptyHeader(rows: Int): MutableList<ColumnHeader>{
            val result = ArrayList<ColumnHeader>()
            for(x in 0 until rows) result.add(ColumnHeader(""))
            return result
        }
    }
}