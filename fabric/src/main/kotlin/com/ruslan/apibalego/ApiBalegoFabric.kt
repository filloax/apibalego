package com.ruslan.apibalego

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader

object ApiBalegoFabric : ModInitializer {
    override fun onInitialize() {
        Apibalego.init(FabricLoader.getInstance().gameDir)

        Apibalego.LOGGER.info("Initialized Fabric entry point")
    }
}
