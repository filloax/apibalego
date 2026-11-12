package com.ruslan.apibalego

object ApiBalegoConstants {
    const val DATA_FOLDER = "apibalego_data"

    // Per-player persist-data key tracking which toasts a player has already seen
    const val CUSTOM_TOAST_MEMORY = ApibalegoMod.MOD_ID + ":customToastMemory"
    // Per-player persist-data key tracking which player-scoped remote commands already ran
    const val REMOTE_COMMAND_MEMORY = ApibalegoMod.MOD_ID + ":remoteCommandMemory"
    // Per-player persist-data key tracking trigger runs
    const val TRIGGER_MEMORY = ApibalegoMod.MOD_ID + ":triggerMemory"

    const val APIBALEGO_PERSISTENT_DATA = "$DATA_FOLDER/main"
}
