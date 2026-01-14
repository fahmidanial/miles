package com.milestracker.app

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = Date(timestamp)
        return sdf.format(date)
    }
}
