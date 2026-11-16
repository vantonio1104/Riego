package cl.inacap.riegoiot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActionLog(
    val timestamp: String,
    val username: String,
    val actionText: String,
    val channel: String = "MQTT"
) {
    fun toFormattedText(): String {
        return "$username: $actionText"
    }

    fun toFormattedTimestamp(): String {
        return "$timestamp ($channel)"
    }

    fun toCsvLine(): String {
        return "$timestamp|$username|$actionText|$channel\n"
    }

    companion object {
        fun fromCsvLine(line: String): ActionLog? {
            val parts = line.split("|")
            if (parts.size >= 4) {
                return ActionLog(
                    timestamp = parts[0],
                    username = parts[1],
                    actionText = parts[2],
                    channel = parts[3]
                )
            }
            return null
        }

        fun currentTimestamp(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
