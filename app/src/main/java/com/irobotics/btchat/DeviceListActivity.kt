package com.irobotics.btchat

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Pehle se pair kiye hue devices ki list dikhata hai */
@SuppressLint("MissingPermission")
class DeviceListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADDRESS = "address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = ListView(this)
        setContentView(list)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val devices = adapter?.bondedDevices?.toList().orEmpty()

        if (devices.isEmpty()) {
            Toast.makeText(
                this,
                "Koi paired device nahi mila. Pehle phone ki Bluetooth settings mein doosre phone ko pair karo.",
                Toast.LENGTH_LONG
            ).show()
        }

        val labels = devices.map { "${it.name ?: "Unknown"}\n${it.address}" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.setOnItemClickListener { _, _, pos, _ ->
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ADDRESS, devices[pos].address))
            finish()
        }
    }
}
