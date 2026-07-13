package com.ruslan.apibalego

import com.ruslan.apibalego.client.pack.PreloadPackSyncClient
import com.ruslan.apibalego.pack.PreloadPackSync
import net.minecraft.server.packs.PackType
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.AddPackFindersEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

@Mod(ApibalegoMod.MOD_ID)
object ApiBalegoNeo {
    init {
        ApibalegoMod.isNeoforge = true
        ApibalegoMod.init(FMLPaths.GAMEDIR.get())

        MOD_BUS.addListener<AddPackFindersEvent> { event ->
            if (event.packType == PackType.SERVER_DATA) {
                PreloadPackSync.preloadServerDatapacks()
                event.addRepositorySource(PreloadPackSync.serverRepositorySource())
            }
        }

        runForDist(
            clientTarget = {
                MOD_BUS.addListener<FMLClientSetupEvent> {
                    ApibalegoMod.initClient()
                }
                MOD_BUS.addListener<AddPackFindersEvent> { event ->
                    if (event.packType == PackType.CLIENT_RESOURCES) {
                        // Fires before FMLClientSetupEvent (i.e. before Apibalego.initClient()),
                        // so the client handler-type registry isn't populated yet - force it here,
                        // same reasoning as Fabric's ClientPackRepositoryMixin.
                        ApibalegoMod.preInitClient(FMLPaths.GAMEDIR.get())
                        PreloadPackSyncClient.preloadClientResourcePacks()
                        event.addRepositorySource(PreloadPackSyncClient.clientRepositorySource())
                    }
                }
            },
            serverTarget = {},
        )

        ApibalegoMod.LOGGER.info("Initialized NeoForge entry point")
    }
}
