package com.ruslan.apibalego

object ApiBalegoConstants {
    const val DATA_FOLDER = "apibalego_data"

    // Per-player persist-data key tracking which toasts a player has already seen
    const val CUSTOM_TOAST_MEMORY = ApibalegoMod.MOD_ID + ":customToastMemory"

    const val APIBALEGO_PERSISTENT_DATA = "$DATA_FOLDER/main"
}
