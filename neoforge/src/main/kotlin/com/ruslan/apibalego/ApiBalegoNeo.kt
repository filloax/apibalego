package com.ruslan.apibalego

import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

@Mod(Apibalego.MOD_ID)
object ApiBalegoNeo {
    init {
        Apibalego.isNeoforge = true
        Apibalego.init()

        runForDist(
            clientTarget = {
                MOD_BUS.addListener<FMLClientSetupEvent> {
                    Apibalego.initClient()
                }
            },
            serverTarget = {},
        )

        Apibalego.LOGGER.info("Initialized NeoForge entry point")
    }
}
