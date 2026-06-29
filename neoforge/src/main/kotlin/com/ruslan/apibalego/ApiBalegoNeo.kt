package com.ruslan.apibalego

import net.neoforged.fml.common.Mod

@Mod(Apibalego.MOD_ID)
object ApiBalegoNeo {
    init {
        Apibalego.isNeoforge = true
        Apibalego.init()

        Apibalego.LOGGER.info("Initialized NeoForge entry point")
    }
}
