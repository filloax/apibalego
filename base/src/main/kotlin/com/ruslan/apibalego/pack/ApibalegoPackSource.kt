package com.ruslan.apibalego.pack

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.repository.PackSource

/**
 * Custom [PackSource] for gamemaster-synced packs, so they're labeled distinctly in the pack list UI.
 */
object ApibalegoPackSource {
    val INSTANCE: PackSource = PackSource.create(
        { component -> Component.translatable("pack.nameAndSource", component, Component.literal("gamemaster")).withStyle(ChatFormatting.AQUA) },
        true,
    )
}
