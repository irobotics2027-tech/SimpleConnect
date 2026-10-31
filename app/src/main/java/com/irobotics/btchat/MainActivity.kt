package com.irobotics.btchat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), BluetoothService.Listener {

    private var btAdapter: BluetoothAdapter? = null
    private var service: BluetoothService? = null

    private lateinit var statusText: TextView
    private lateinit var input: EditText
    private lateinit var progress: ProgressBar
    private lateinit var list: RecyclerView
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: MessageAdapter

    // ---------- Permissions ----------
    private val neededPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasPermissions() = neededPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermissions()) ensureBluetoothOn()
            else toast("Bluetooth permission ke bina app kaam nahi karega")
        }

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (btAdapter?.isEnabled != true) toast("Bluetooth ON karna zaroori hai")
        }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val pickDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val address = res.data?.getStringExtra(DeviceListActivity.EXTRA_ADDRESS)
            if (res.resultCode == Activity.RESULT_OK && address != null) {
                btAdapter?.getRemoteDevice(address)?.let { service?.connect(it) }
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) sendImage(uri)
        }

    // ---------- Lifecycle ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        input = findViewById(R.id.inputText)
        progress = findViewById(R.id.progress)
        list = findViewById(R.id.messageList)

        adapter = MessageAdapter(messages) { showImage(it) }
        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        list.adapter = adapter

        btAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (btAdapter == null) {
            toast("Is phone mein Bluetooth nahi hai")
            finish()
            return
        }
        service = BluetoothService(btAdapter!!, this)

        findViewById<Button>(R.id.btnListen).setOnClickListener {
            if (!ready()) return@setOnClickListener
            // Doosre phones ko 2 minute ke liye dikhai do
            discoverableLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            )
            service?.startListening()
        }
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            if (!ready()) return@setOnClickListener
            pickDeviceLauncher.launch(Intent(this, DeviceListActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener { sendText() }
        findViewById<ImageButton>(R.id.btnImage).setOnClickListener {
            if (service?.state != BluetoothService.State.CONNECTED) {
                toast("Pehle connect karo")
            } else {
                pickImageLauncher.launch("image/*")
            }
        }

        if (!hasPermissions()) permissionLauncher.launch(neededPermissions)
        else ensureBluetoothOn()

        addSystem("Ek phone par 'Wait' dabao, doosre par 'Connect' dabakar pehla phone chuno.")
    }

    override fun onDestroy() {
        super.onDestroy()
        service?.stop()
    }

    private fun ready(): Boolean {
        if (!hasPermissions()) {
            permissionLauncher.launch(neededPermissions); return false
        }
        if (btAdapter?.isEnabled != true) {
            ensureBluetoothOn(); return false
        }
        return true
    }

    private fun ensureBluetoothOn() {
        if (btAdapter?.isEnabled != true) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    // ---------- Sending ----------
    private fun sendText() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (service?.state != BluetoothService.State.CONNECTED) {
            toast("Pehle connect karo"); return
        }
        input.setText("")
        adapter.add(ChatMessage(fromMe = true, text = text))
        scrollDown()
        Thread { service?.send(BluetoothService.TYPE_TEXT, text.toByteArray(Charsets.UTF_8)) }.start()
    }

    private fun sendImage(uri: Uri) {
        progress.visibility = View.VISIBLE
        progress.progress = 0
        Thread {
            try {
                val bmp = loadScaled(uri, 1600)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                val bytes = out.toByteArray()
                runOnUiThread {
                    adapter.add(ChatMessage(fromMe = true, image = bmp))
                    scrollDown()
                }
                val ok = service?.send(BluetoothService.TYPE_IMAGE, bytes) == true
                runOnUiThread {
                    progress.visibility = View.GONE
                    if (!ok) toast("Photo nahi gayi")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    toast("Photo load nahi hui: ${e.message}")
                }
            }
        }.start()
    }

    /** Badi photo ko chhota karke bhejta hai taaki jaldi jaye */
    private fun loadScaled(uri: Uri, maxSide: Int): Bitmap {
        val bmp: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
                val w = info.size.width; val h = info.size.height
                val scale = maxSide.toFloat() / maxOf(w, h)
                if (scale < 1f) dec.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > maxSide) sample *= 2
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts2) }
                ?: throw IllegalStateException("decode failed")
        }
        return bmp
    }

    // ---------- BluetoothService.Listener ----------
    override fun onStateChanged(state: BluetoothService.State, deviceName: String?) {
        statusText.text = when (state) {
            BluetoothService.State.IDLE -> "Connected nahi hai"
            BluetoothService.State.LISTENING -> "Intezaar ho raha hai..."
            BluetoothService.State.CONNECTING -> "Connect ho raha hai: $deviceName"
            BluetoothService.State.CONNECTED -> "Connected: $deviceName"
        }
        if (state == BluetoothService.State.CONNECTED) addSystem("$deviceName se jud gaye")
    }

    override fun onTextReceived(text: String) {
        adapter.add(ChatMessage(fromMe = false, text = text))
        scrollDown()
    }

    override fun onImageReceived(bytes: ByteArray) {
        progress.visibility = View.GONE
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp == null) {
            toast("Photo kharab aayi"); return
        }
        adapter.add(ChatMessage(fromMe = false, image = bmp))
        scrollDown()
    }

    override fun onProgress(percent: Int, sending: Boolean) {
        progress.visibility = if (percent in 0..99) View.VISIBLE else View.GONE
        progress.progress = percent
    }

    override fun onError(message: String) {
        progress.visibility = View.GONE
        addSystem(message)
    }

    // ---------- Helpers ----------
    private fun showImage(bmp: Bitmap) {
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
        }
        AlertDialog.Builder(this)
            .setView(iv)
            .setPositiveButton("Gallery mein save karo") { _, _ -> saveToGallery(bmp) }
            .setNegativeButton("Band karo", null)
            .show()
    }

    private fun saveToGallery(bmp: Bitmap) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "BTChat_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BTChat")
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException()
            contentResolver.openOutputStream(uri).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, it!!)
            }
            toast("Gallery mein save ho gayi (Pictures/BTChat)")
        } catch (e: Exception) {
            toast("Save nahi hui")
        }
    }

    private fun addSystem(text: String) {
        adapter.add(ChatMessage(fromMe = false, text = text, isSystem = true))
        scrollDown()
    }

    private fun scrollDown() {
        if (messages.isNotEmpty()) list.scrollToPosition(messages.size - 1)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
