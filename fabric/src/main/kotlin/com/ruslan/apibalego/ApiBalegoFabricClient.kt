package com.ruslan.apibalego

import net.fabricmc.api.ClientModInitializer

object ApiBalegoFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        ApibalegoMod.initClient()

        ApibalegoMod.LOGGER.info("Initialized Fabric client entry point")
    }
}
