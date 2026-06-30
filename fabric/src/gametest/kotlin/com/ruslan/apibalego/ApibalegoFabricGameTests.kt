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
    @GameTest
    fun configLoaded(helper: GameTestHelper) = ApibalegoGameTests.configLoaded(helper)

    @GameTest
    fun apiEventDispatchActive(helper: GameTestHelper) = ApibalegoGameTests.apiEventDispatchActive(helper)

    @GameTest
    fun apiEventDispatchInactiveSkipped(helper: GameTestHelper) = ApibalegoGameTests.apiEventDispatchInactiveSkipped(helper)

    @GameTest
    fun liveUpdatesBuiltinsRegistered(helper: GameTestHelper) = ApibalegoGameTests.liveUpdatesBuiltinsRegistered(helper)

    @GameTest
    fun dispatchJoinActive(helper: GameTestHelper) = ApibalegoGameTests.dispatchJoinActive(helper)

    @GameTest
    fun dispatchJoinInactiveSkipped(helper: GameTestHelper) = ApibalegoGameTests.dispatchJoinInactiveSkipped(helper)

    @GameTest
    fun toastJoinMarksAsSeen(helper: GameTestHelper) = ApibalegoGameTests.toastJoinMarksAsSeen(helper)

    @GameTest
    fun commandDispatchRunsCommand(helper: GameTestHelper) = ApibalegoGameTests.commandDispatchRunsCommand(helper)

    @GameTest
    fun commandDispatchIdempotent(helper: GameTestHelper) = ApibalegoGameTests.commandDispatchIdempotent(helper)

    @GameTest
    fun structureHandlerSkipsInactive(helper: GameTestHelper) = ApibalegoGameTests.structureHandlerSkipsInactive(helper)

    @GameTest
    fun structureHandlerSkipsUnknownStructure(helper: GameTestHelper) = ApibalegoGameTests.structureHandlerSkipsUnknownStructure(helper)

    @GameTest
    fun structureHandlerRegistersAndClearsStructure(helper: GameTestHelper) = ApibalegoGameTests.structureHandlerRegistersAndClearsStructure(helper)
}
