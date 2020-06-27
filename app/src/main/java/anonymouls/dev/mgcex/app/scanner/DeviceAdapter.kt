package anonymouls.dev.mgcex.app.scanner

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import anonymouls.dev.mgcex.app.R


class DeviceAdapter(context: Context, private val mResId: Int, var mList: MutableList<BluetoothDevice>)
    : ArrayAdapter<BluetoothDevice>(context, mResId, mList) {
    private val mInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var newConvertView = convertView
        val item = getItem(position)

        if (newConvertView == null) {
            newConvertView = mInflater.inflate(mResId, null)
        }
        val name = newConvertView!!.findViewById<View>(R.id.device_name) as TextView
        name.text = item!!.name
        val address = newConvertView.findViewById<View>(R.id.device_address) as TextView
        address.text = item.address

        return newConvertView
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
