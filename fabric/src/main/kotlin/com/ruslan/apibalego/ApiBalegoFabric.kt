package com.ruslan.apibalego

import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader

object ApiBalegoFabric : ModInitializer {
    override fun onInitialize() {
        ApibalegoMod.init(FabricLoader.getInstance().gameDir)

        ApibalegoMod.LOGGER.info("Initialized Fabric entry point")
    }
}
