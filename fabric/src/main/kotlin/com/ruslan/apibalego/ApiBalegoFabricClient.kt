package com.ruslan.apibalego

import net.fabricmc.api.ClientModInitializer

object ApiBalegoFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        Apibalego.initClient()

        Apibalego.LOGGER.info("Initialized Fabric client entry point")
    }
}
