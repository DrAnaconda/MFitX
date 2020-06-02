package anonymouls.dev.MGCEX.App

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView


class DeviceAdapter(context: Context, private val mResId: Int, var mList: MutableList<BluetoothDevice>)
    : ArrayAdapter<BluetoothDevice>(context, mResId, mList) {
    private val mInflater: LayoutInflater

    init {
        mInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val item = getItem(position)

        if (convertView == null) {
            convertView = mInflater.inflate(mResId, null)
        }
        val name = convertView!!.findViewById<View>(R.id.device_name) as TextView
        name.text = item!!.name
        val address = convertView.findViewById<View>(R.id.device_address) as TextView
        address.text = item.address

        return convertView
    }

    fun update(newDevice: BluetoothDevice?) {
        if (newDevice == null || newDevice.address == null) {
            return
        }

        var contains = false
        for (device in mList) {
            if (mList.contains(newDevice)) {
                contains = true
                break
            }
        }
        if (!contains) {
            // add new BluetoothDevice
            mList.add(newDevice)
        }
        notifyDataSetChanged()
    }
}
