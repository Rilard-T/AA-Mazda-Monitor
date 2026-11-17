package com.aatorque.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.aatorque.stats.R
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Bluetooth Classic (SPP) канал к ELM327. Команды строго по очереди. */
class Elm327Link(context: Context, private val device: BluetoothDevice) : ObdLink {
    private val appContext = context.applicationContext
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    /** Байты от адаптера: читатель кладёт их сюда, команда ждёт с таймаутом вместо опроса available(). */
    private val rx = LinkedBlockingQueue<ByteArray>()
    private var reader: Thread? = null

    @SuppressLint("MissingPermission")
    override fun open() {
        close()
        runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }   // нужен BLUETOOTH_SCAN; поиск мы не запускаем, отказ не страшен
        // Клонам ELM327 подходят разные способы: обычный SPP, незащищённый SPP, канал 1 напрямую
        val ways = listOf<() -> BluetoothSocket>(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            {
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            },
        )
        var last: Exception? = null
        for ((i, make) in ways.withIndex()) {
            var s: BluetoothSocket? = null
            try {
                s = make()
                s.connect()
                socket = s
                input = s.inputStream
                output = s.outputStream
                startReader(s.inputStream)
                return
            } catch (e: Exception) {
                last = e
                runCatching { s?.close() }
                if (i < ways.size - 1) Thread.sleep(700)
            }
        }
        throw IOException(appContext.getString(R.string.obd_bt_channel_failed, last?.message ?: ""))
    }

    /** Один блокирующий поток на чтение: без него пришлось бы опрашивать available() каждую миллисекунду. */
    private fun startReader(inp: InputStream) {
        reader = Thread({
            val buf = ByteArray(512)
            try {
                while (true) {
                    val r = inp.read(buf)
                    if (r < 0) break
                    if (r > 0) rx.offer(buf.copyOf(r))
                }
            } catch (_: IOException) {
                // сокет закрыт — штатное завершение читателя
            }
        }, "elm327-reader").also { it.isDaemon = true; it.start() }
    }

    override fun close() {
        runCatching { socket?.close() }
        reader?.interrupt()
        reader = null
        socket = null; input = null; output = null
        rx.clear()
    }

    /** Отправляет команду и читает ответ до приглашения '>'. Возвращает строки ответа без эха. */
    @Synchronized
    override fun command(cmd: String, timeoutMs: Long): String {
        val out = output ?: throw IOException(appContext.getString(R.string.obd_err_no_socket))
        rx.clear()                                      // выбрасываем хвост прошлого ответа
        out.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw SocketTimeoutException(appContext.getString(R.string.obd_err_timeout, cmd))
            val chunk = rx.poll(minOf(remaining, 100L), TimeUnit.MILLISECONDS) ?: continue
            for (b in chunk) {
                val ch = (b.toInt() and 0xFF).toChar()
                if (ch == '>') return clean(sb.toString(), cmd)
                sb.append(ch)
            }
        }
    }

    private fun clean(s: String, cmd: String): String =
        s.replace('\r', '\n').lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) }
            .joinToString("\n")

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
