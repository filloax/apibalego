package com.ruslan.apibalego

import net.fabricmc.api.ModInitializer

object ApiBalegoFabric : ModInitializer {
    override fun onInitialize() {
        Apibalego.init()

        Apibalego.LOGGER.info("Initialized Fabric entry point")
    }
}
