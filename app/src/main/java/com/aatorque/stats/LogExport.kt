package com.aatorque.stats

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExport {
    private fun mask(s: String) = com.aatorque.obd.ErrorReport.redact(s)

    fun buildText(tree: CacheLogTree, extra: String = "", createdLabel: String = ""): String {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.append("AA Mazda Monitor ").append(BuildConfig.VERSION_NAME).append('\n')
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append(createdLabel).append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
        if (extra.isNotBlank()) sb.append(mask(extra)).append('\n')
        sb.append("----\n")
        for (d in tree.snapshot()) {
            sb.append(ts.format(Date(d.time))).append(' ')
                .append(tree.levelName(d.priority)).append(' ')
                .append(d.tag ?: "").append(": ")
                .append(mask(d.message)).append('\n')
            d.throwable?.let { sb.append(mask(it.stackTraceToString())).append('\n') }
        }
        return sb.toString()
    }

    fun share(context: Context, tree: CacheLogTree, extra: String = "") {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "aamazda-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val file = File(dir, name)
        file.writeText(buildText(tree, extra, context.getString(R.string.log_export_created)))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".logfileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.log_export_chooser))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
