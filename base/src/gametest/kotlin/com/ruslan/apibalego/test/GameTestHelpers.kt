package com.ruslan.apibalego.test

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.server.level.ServerPlayer

// In case it gets deprecated later (assuming I'll ever update this past 26.2 lmao)
fun GameTestHelper.makeMockServerPlayerInLevelAlt(): ServerPlayer {
    @Suppress("DEPRECATION")
    return makeMockServerPlayerInLevel()
}