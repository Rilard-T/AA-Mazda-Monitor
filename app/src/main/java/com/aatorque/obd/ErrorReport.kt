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
            .setTitle("Сообщить об ошибке")
            .setMessage(
                "Полный журнал будет скопирован в буфер обмена. Откроется страница GitHub с готовым обращением: " +
                    "версия приложения, модель телефона, статус подключения и последние строки журнала. " +
                    "Адреса устройств скрыты. Проверьте текст и нажмите «Submit new issue», при необходимости " +
                    "вставьте полный журнал в комментарий. " +
                    "Кнопка «Файл .txt» сохраняет весь журнал в файл и открывает меню «Поделиться»."
            )
            .setPositiveButton("Продолжить") { _, _ -> send(activity, lines) }
            .setNeutralButton("Файл .txt") { _, _ -> sendFile(activity) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun send(activity: Activity, lines: List<String>) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AA Mazda Monitor Log", lines.joinToString("\n")))
        Toast.makeText(activity, "Журнал скопирован. Проверьте обращение и отправьте его.", Toast.LENGTH_LONG).show()
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buildUrl(lines))))
        } catch (e: Exception) {
            Toast.makeText(activity, "Не удалось открыть браузер", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendFile(activity: Activity) {
        try {
            val tree = (activity.application as com.aatorque.stats.App).logTree
            com.aatorque.stats.LogExport.share(activity, tree, "Подключение: ${ObdBridgeService.status}")
        } catch (e: Exception) {
            Toast.makeText(activity, "Не удалось создать файл журнала", Toast.LENGTH_LONG).show()
        }
    }

    /** Скрывает адреса Bluetooth и VIN. */
    fun redact(s: String): String = s.replace(MAC, "XX:XX:XX:XX:XX:XX").replace(VIN, "<VIN>")

    fun buildUrl(lines: List<String>): String {
        val base = BuildConfig.REPO_URL + "/issues/new"
        val title = "Ошибка в приложении, версия " + BuildConfig.VERSION_NAME
        var keep = lines.takeLast(80)
        while (true) {
            val url = base + "?title=" + enc(title) + "&body=" + enc(body(keep))
            if (url.length <= MAX_URL || keep.isEmpty()) return url
            keep = keep.drop(maxOf(1, keep.size / 5))      // отбрасываем самые старые строки
        }
    }

    private fun body(log: List<String>): String =
        "**Что произошло**\n(опишите проблему и что вы делали)\n\n" +
            "**Версия:** ${BuildConfig.VERSION_NAME}\n" +
            "**Android:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "**Телефон:** ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "**Подключение:** ${ObdBridgeService.status}\n\n" +
            "**Журнал (последние строки)**\n```\n" + log.joinToString("\n") + "\n```\n" +
            "Полный журнал скопирован в буфер обмена, вставьте его в комментарий."

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
