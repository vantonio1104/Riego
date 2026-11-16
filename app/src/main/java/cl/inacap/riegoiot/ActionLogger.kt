package cl.inacap.riegoiot

import android.content.Context
import java.io.File

object ActionLogger {
    private const val FILE_NAME = "actions.txt"

    fun logAction(context: Context, username: String, actionText: String, channel: String = "MQTT") {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val log = ActionLog(
                timestamp = ActionLog.currentTimestamp(),
                username = username,
                actionText = actionText,
                channel = channel
            )
            file.appendText(log.toCsvLine())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecentActions(context: Context, limit: Int = 20): List<ActionLog> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()

            file.readLines()
                .mapNotNull { ActionLog.fromCsvLine(it) }
                .takeLast(limit)
                .reversed()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
