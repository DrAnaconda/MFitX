package anonymouls.dev.mgcex.app.data

import android.app.Activity
import android.graphics.Point
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import anonymouls.dev.mgcex.app.R
import com.evrencoskun.tableview.adapter.AbstractTableAdapter
import com.evrencoskun.tableview.adapter.recyclerview.holder.AbstractViewHolder

open class Cell(val data: String) {
    companion object {
        fun <T> listToCells(list: Array<String>, isHeader: Boolean): ArrayList<T> {
            val result = ArrayList<T>()
            for (x in list.indices) {
                if (!isHeader)
                    result.add(Cell(list[x].toString()) as T)
                else
                    result.add(ColumnHeader(list[x].toString()) as T)
            }
            return result
        }
    }
}

class ColumnHeader(data: String) : Cell(data)
class RowHeader(data: String) : Cell(data)

class MyTableViewAdapter(private val context: Activity) : AbstractTableAdapter<ColumnHeader?, RowHeader?, Cell?>() {

    var countRows = 0


    /**
     * This is sample CellViewHolder class
     * This viewHolder must be extended from AbstractViewHolder class instead of RecyclerView.ViewHolder.
     */
    internal inner class MyCellViewHolder(itemView: View) : AbstractViewHolder(itemView) {
        val cell_container: LinearLayout
        val cell_textview: TextView

        init {
            cell_container = itemView.findViewById(R.id.cell_container)
            cell_textview = itemView.findViewById(R.id.cell_data)
        }
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
        val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.table_view_cell_layout, parent, false)
        // Create a Custom ViewHolder for a Cell item.
        return MyCellViewHolder(layout)
    }

    override fun onBindCellViewHolder(holder: AbstractViewHolder, cellItemModel: Cell?, columnPosition: Int, rowPosition: Int) {
        val cell = cellItemModel as Cell

        // Get the holder to update cell item text
        val viewHolder = holder as MyCellViewHolder
        viewHolder.cell_textview.text = cell.data

        // If your TableView should have auto resize for cells & columns.
        // Then you should consider the below lines. Otherwise, you can ignore them.

        // It is necessary to remeasure itself.
        calculateSize(viewHolder.cell_textview, columnPosition, this.mColumnHeaderItems.size - 1)
    }

    override fun addRow(rowPosition: Int, rowHeaderItem: RowHeader?, cellItems: MutableList<Cell?>?) {
        super.addRow(rowPosition, rowHeaderItem, cellItems)
        countRows++
    }

    fun removeEverything() {
        try {
            if (countRows > 0) this.removeRowRange(0, countRows)
        } catch (e: Exception) {
        }
        countRows = 0
        this.setAllItems(null, null, null)
    }

    private fun calculateSize(holder: View, position: Int, otherColumns: Int) {
        var size: Point = Point(1, 1)
        context.windowManager.defaultDisplay.getSize(size)
        if (otherColumns > 2)
            size = Point((size.x * 0.95f).toInt(), (size.y * 0.95).toInt())
        if (position == 0)
            holder.layoutParams.width = (size.x / 2)
        else
            holder.layoutParams.width = (size.x / 2) / otherColumns
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
        val column_header_container: LinearLayout
        val cell_textview: TextView

        init {
            column_header_container = itemView.findViewById(R.id.column_header_container)
            cell_textview = itemView.findViewById(R.id.column_header_textView)
        }
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

        // Get Column Header xml Layout
        val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.table_view_column_header, parent, false)

        // Create a ColumnHeader ViewHolder
        return MyColumnHeaderViewHolder(layout)
    }

    override fun onBindColumnHeaderViewHolder(holder: AbstractViewHolder, columnHeaderItemModel: ColumnHeader?, columnPosition: Int) {
        val columnHeader = columnHeaderItemModel as ColumnHeader

        // Get the holder to update cell item text
        val columnHeaderViewHolder = holder as MyColumnHeaderViewHolder
        columnHeaderViewHolder.cell_textview.text = columnHeader.data

        calculateSize(columnHeaderViewHolder.column_header_container, columnPosition, this.mColumnHeaderItems.size - 1)
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
        val cell_textview: TextView

        init {
            cell_textview = itemView.findViewById(R.id.cell_data)
        }
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
        // The unique ID for this type of cell item
        // If you have different items for Cell View by X (Column) position,
        // then you should fill this method to be able create different
        // type of CellViewHolder on "onCreateCellViewHolder"
        return 0
    }
}