package com.ruslan.apibalego

import com.ruslan.apibalego.test.ApibalegoGameTests
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper

/**
 * Fabric gametest entry point. Registered via the `fabric-gametest` entrypoint in fabric.mod.json;
 * Fabric scans this class for [GameTest]-annotated methods. The default structure
 * (`fabric-gametest-api-v1:empty`) is provided by fabric-api, so no template ships here.
 *
 * Run with: `./gradlew :fabric:runGametest` (see build.gradle.kts).
 */
object ApibalegoFabricGameTests {
    // generous budget: chains config/join/command/structure/datapack checks as one continuous
    // sequence (see ApibalegoGameTests.combinedHandlerLifecycleTest), which can run long under a
    // loaded gametest server ("Can't keep up!" throttling)
    @GameTest(maxTicks = 1200)
    fun combinedHandlerLifecycleTest(helper: GameTestHelper) = ApibalegoGameTests.combinedHandlerLifecycleTest(helper)
}
