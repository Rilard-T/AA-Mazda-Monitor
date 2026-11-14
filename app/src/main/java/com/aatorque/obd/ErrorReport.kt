package com.aatorque.obd

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.aatorque.stats.BuildConfig
import com.aatorque.stats.R
import java.net.URLEncoder

/**
 * «Сообщить об ошибке»: копирует полный журнал в буфер и открывает на GitHub страницу нового обращения
 * с заполненным заголовком, версией, телефоном и последними строками журнала. Ничего не отправляется
 * автоматически: пользователь видит текст и сам нажимает «Submit new issue».
 */
object ErrorReport {
    private const val MAX_URL = 6000       // с запасом до предела длины адреса на GitHub
    private val MAC = Regex("\\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
    private val VIN = Regex("\\b[A-HJ-NPR-Z0-9]{17}\\b")

    fun start(activity: Activity, rawLines: List<String>) {
        val lines = rawLines.map { redact(it) }
        AlertDialog.Builder(activity)
            .setTitle(R.string.obd_report_title)
            .setMessage(R.string.obd_report_message)
            .setPositiveButton(R.string.obd_report_continue) { _, _ -> send(activity, lines) }
            .setNeutralButton(R.string.obd_report_file) { _, _ -> sendFile(activity) }
            .setNegativeButton(R.string.obd_report_cancel, null)
            .show()
    }

    private fun send(activity: Activity, lines: List<String>) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AA Mazda Monitor Log", lines.joinToString("\n")))
        Toast.makeText(activity, R.string.obd_report_copied, Toast.LENGTH_LONG).show()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buildUrl(activity, lines))))
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.obd_report_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendFile(activity: Activity) {
        try {
            val tree = (activity.application as com.aatorque.stats.App).logTree
            com.aatorque.stats.LogExport.share(
                activity, tree,
                activity.getString(R.string.obd_report_connection_line, ObdBridgeService.statusText(activity))
            )
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.obd_report_file_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Скрывает адреса Bluetooth и VIN. */
    fun redact(s: String): String = s.replace(MAC, "XX:XX:XX:XX:XX:XX").replace(VIN, "<VIN>")

    fun buildUrl(context: Context, lines: List<String>): String {
        val base = BuildConfig.REPO_URL + "/issues/new"
        val title = context.getString(R.string.obd_report_issue_title, BuildConfig.VERSION_NAME)
        var keep = lines.takeLast(80)
        while (true) {
            val url = base + "?title=" + enc(title) + "&body=" + enc(body(context, keep))
            if (url.length <= MAX_URL || keep.isEmpty()) return url
            keep = keep.drop(maxOf(1, keep.size / 5))      // отбрасываем самые старые строки
        }
    }

    private fun body(context: Context, log: List<String>): String = listOf(
        context.getString(R.string.obd_report_body_what),
        "",
        context.getString(R.string.obd_report_body_version, BuildConfig.VERSION_NAME),
        context.getString(R.string.obd_report_body_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
        context.getString(R.string.obd_report_body_phone, Build.MANUFACTURER, Build.MODEL),
        context.getString(R.string.obd_report_body_connection, ObdBridgeService.statusText(context)),
        "",
        context.getString(R.string.obd_report_body_log),
        "```",
        log.joinToString("\n"),
        "```",
        context.getString(R.string.obd_report_body_note),
    ).joinToString("\n")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
