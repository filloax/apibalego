package com.ruslan.apibalego

import com.ruslan.apibalego.test.ApibalegoGameTests
import com.ruslan.apibalego.utils.resLoc
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.TestFunctionLoader
import net.minecraft.resources.ResourceKey
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent
import java.util.function.BiConsumer
import java.util.function.Consumer

/**
 * NeoForge gametest registration.
 */
@EventBusSubscriber(modid = Apibalego.MOD_ID)
object ApibalegoNeoGameTests {
    private val TESTS: List<Pair<String, Consumer<GameTestHelper>>> = listOf(
        "config_loaded" to Consumer { ApibalegoGameTests.configLoaded(it) },
        "api_event_dispatch_active" to Consumer { ApibalegoGameTests.apiEventDispatchActive(it) },
        "api_event_dispatch_inactive_skipped" to Consumer { ApibalegoGameTests.apiEventDispatchInactiveSkipped(it) },
        "live_updates_builtins_registered" to Consumer { ApibalegoGameTests.liveUpdatesBuiltinsRegistered(it) },
        "dispatch_join_active" to Consumer { ApibalegoGameTests.dispatchJoinActive(it) },
        "dispatch_join_inactive_skipped" to Consumer { ApibalegoGameTests.dispatchJoinInactiveSkipped(it) },
        "toast_packet_builds" to Consumer { ApibalegoGameTests.toastPacketBuilds(it) },
    )

    private fun functionKey(name: String): ResourceKey<Consumer<GameTestHelper>> =
        ResourceKey.create(Registries.TEST_FUNCTION, resLoc(name))

    /** Register the test function bodies into TEST_FUNCTION (at mod construction). */
    @JvmStatic
    @SubscribeEvent
    fun registerFunctions(event: FMLConstructModEvent) {
        TestFunctionLoader.registerLoader(object : TestFunctionLoader() {
            override fun load(output: BiConsumer<ResourceKey<Consumer<GameTestHelper>>, Consumer<GameTestHelper>>) {
                TESTS.forEach { (name, fn) -> output.accept(functionKey(name), fn) }
            }
        })
    }
}
