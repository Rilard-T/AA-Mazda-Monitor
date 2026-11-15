package com.aatorque.obd

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.aatorque.stats.R
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Wi-Fi ELM327 (TCP). Сокет привязан именно к сети Wi-Fi, поэтому мобильный интернет продолжает работать. */
class WifiLink(context: Context, private val host: String, private val port: Int) : ObdLink {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun open() {
        close()
        val network = wifiNetwork()
        val s = network.socketFactory.createSocket()
        try {
            s.connect(InetSocketAddress(host, port), 5000)
        } catch (e: IOException) {
            runCatching { s.close() }
            release()
            throw e
        }
        s.tcpNoDelay = true
        socket = s
        input = s.getInputStream()
        output = s.getOutputStream()
    }

    /** Сеть адаптера без интернета: просим систему держать Wi-Fi и берём именно её. */
    private fun wifiNetwork(): Network {
        val latch = CountDownLatch(1)
        var found: Network? = null
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                found = network
                latch.countDown()
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.requestNetwork(request, cb)
        callback = cb
        val ok = latch.await(6, TimeUnit.SECONDS)
        val n = found
        if (!ok || n == null) {
            release()
            throw IOException(appContext.getString(R.string.obd_wifi_no_network))
        }
        return n
    }

    private fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
        release()
    }

    @Synchronized
    override fun command(cmd: String, timeoutMs: Long): String {
        val out = output ?: throw IOException(appContext.getString(R.string.obd_err_no_socket))
        val inp = input ?: throw IOException(appContext.getString(R.string.obd_err_no_socket))
        while (inp.available() > 0) inp.read()
        out.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArray(256)
        done@ while (true) {
            val n = inp.available()
            if (n > 0) {
                val r = inp.read(buf, 0, minOf(n, buf.size))
                if (r < 0) throw IOException(appContext.getString(R.string.obd_err_closed))
                for (i in 0 until r) {
                    val ch = (buf[i].toInt() and 0xFF).toChar()
                    if (ch == '>') break@done
                    sb.append(ch)
                }
            } else {
                if (System.currentTimeMillis() > deadline)
                    throw SocketTimeoutException(appContext.getString(R.string.obd_err_timeout, cmd))
                Thread.sleep(1)
            }
        }
        return clean(sb.toString(), cmd)
    }

    private fun clean(s: String, cmd: String): String =
        s.replace('\r', '\n').lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) }
            .joinToString("\n")
}
