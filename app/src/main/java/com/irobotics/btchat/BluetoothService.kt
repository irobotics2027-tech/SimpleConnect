package com.irobotics.btchat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic (RFCOMM) se do phones ko jodta hai.
 *
 * Protocol (har packet):
 *   1 byte  type   -> 1 = text, 2 = image
 *   4 bytes length -> data kitne bytes ka hai
 *   N bytes data   -> text (UTF-8) ya JPEG image
 */
@SuppressLint("MissingPermission")
class BluetoothService(
    private val adapter: BluetoothAdapter,
    private val listener: Listener
) {
    interface Listener {
        fun onStateChanged(state: State, deviceName: String?)
        fun onTextReceived(text: String)
        fun onImageReceived(bytes: ByteArray)
        fun onProgress(percent: Int, sending: Boolean)
        fun onError(message: String)
    }

    enum class State { IDLE, LISTENING, CONNECTING, CONNECTED }

    companion object {
        // Dono phones par same UUID hona zaroori hai
        val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        const val NAME = "BTChat"
        const val TYPE_TEXT: Byte = 1
        const val TYPE_IMAGE: Byte = 2
        const val MAX_SIZE = 20 * 1024 * 1024 // 20 MB
        private const val CHUNK = 8 * 1024
    }

    private val main = Handler(Looper.getMainLooper())
    private var acceptThread: Thread? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var output: DataOutputStream? = null
    private val writeLock = Any()

    @Volatile var state: State = State.IDLE
        private set

    private fun setState(s: State, name: String? = null) {
        state = s
        main.post { listener.onStateChanged(s, name) }
    }

    private fun error(msg: String) = main.post { listener.onError(msg) }

    /** Doosre phone ke connect hone ka intezaar karo */
    fun startListening() {
        stop()
        setState(State.LISTENING)
        acceptThread = Thread {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(NAME, APP_UUID)
                val s = serverSocket?.accept() // yahan ruk jata hai jab tak koi connect na kare
                serverSocket?.close()
                if (s != null) connected(s)
            } catch (e: IOException) {
                if (state == State.LISTENING) {
                    error("Listening band ho gaya: ${e.message}")
                    setState(State.IDLE)
                }
            }
        }.also { it.start() }
    }

    /** Kisi paired device se connect karo */
    fun connect(device: BluetoothDevice) {
        stop()
        setState(State.CONNECTING, device.name)
        Thread {
            adapter.cancelDiscovery()
            try {
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                connected(s)
            } catch (e: IOException) {
                error("Connect nahi ho paya. Doosre phone par 'Wait' dabaya hai?")
                setState(State.IDLE)
            }
        }.start()
    }

    private fun connected(s: BluetoothSocket) {
        socket = s
        output = DataOutputStream(s.outputStream)
        setState(State.CONNECTED, s.remoteDevice?.name ?: "Device")
        Thread { readLoop(s) }.start()
    }

    private fun readLoop(s: BluetoothSocket) {
        val input = DataInputStream(s.inputStream)
        try {
            while (true) {
                val type = input.readByte()
                val len = input.readInt()
                if (len < 0 || len > MAX_SIZE) throw IOException("Galat data size")
                val data = ByteArray(len)
                var read = 0
                var lastPct = -1
                while (read < len) {
                    val n = input.read(data, read, minOf(CHUNK, len - read))
                    if (n < 0) throw IOException("Connection toot gaya")
                    read += n
                    if (type == TYPE_IMAGE) {
                        val pct = (read * 100L / len).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            main.post { listener.onProgress(pct, false) }
                        }
                    }
                }
                when (type) {
                    TYPE_TEXT -> {
                        val text = String(data, Charsets.UTF_8)
                        main.post { listener.onTextReceived(text) }
                    }
                    TYPE_IMAGE -> main.post { listener.onImageReceived(data) }
                }
            }
        } catch (e: IOException) {
            if (state == State.CONNECTED) {
                error("Connection toot gaya")
                closeSocket()
                setState(State.IDLE)
            }
        }
    }

    /** Background thread se call karo */
    fun send(type: Byte, data: ByteArray): Boolean {
        val out = output ?: return false
        return try {
            synchronized(writeLock) {
                out.writeByte(type.toInt())
                out.writeInt(data.size)
                var sent = 0
                var lastPct = -1
                while (sent < data.size) {
                    val n = minOf(CHUNK, data.size - sent)
                    out.write(data, sent, n)
                    sent += n
                    if (type == TYPE_IMAGE) {
                        val pct = (sent * 100L / data.size).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            main.post { listener.onProgress(pct, true) }
                        }
                    }
                }
                out.flush()
            }
            true
        } catch (e: IOException) {
            error("Bhej nahi paya: ${e.message}")
            false
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        output = null
    }

    fun stop() {
        val wasActive = state != State.IDLE
        state = State.IDLE
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        acceptThread = null
        closeSocket()
        if (wasActive) setState(State.IDLE)
    }
}
