package com.endermythex.enderrmodded.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/** Copie le journal dans le presse-papiers. */
fun copyLogs(context: Context, title: String, lines: List<String>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Journal EnderrModded", "$title\n" + lines.joinToString("\n")))
    Toast.makeText(context, "Journal copié", Toast.LENGTH_SHORT).show()
}
