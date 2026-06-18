package com.ruslan.apibalego

object ApiBalegoConstants {
    const val DATA_FOLDER = "apibalego_data"

    // Per-player persist-data key tracking which toasts a player has already seen
    const val CUSTOM_TOAST_MEMORY = "apibalego:customToastMemory"

    const val DATASYNC_MEMORY_DATA = "$DATA_FOLDER/datasync_memory"
    const val CMD_EVENTS_DATA = "$DATA_FOLDER/cmd_events"
}
