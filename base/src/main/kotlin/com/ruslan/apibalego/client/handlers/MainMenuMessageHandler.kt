package com.ruslan.apibalego.client.handlers

import com.filloax.fxlib.api.json.SimpleComponentSerializer
import com.ruslan.apibalego.client.http.ClientApiEntry
import com.ruslan.apibalego.client.http.ClientApiEntryHandler
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.SplashRenderer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/**
 * Replaces the main menu splash message with one from the gamemaster-provided pool.
 */
object MainMenuMessageHandler : ClientApiEntryHandler<MainMenuMessageHandler.MenuMessages> {
    // Matches SplashManager.DEFAULT_STYLE
    private val SPLASH_STYLE: Style = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00))

    @Serializable
    data class MenuMessages(
        val messages: List<@Serializable(with = SimpleComponentSerializer::class) Component> = listOf(),
    )

    @Volatile
    private var messagePool: List<Component> = listOf()

    override fun handleApiUpdate(client: Minecraft, entries: Collection<ClientApiEntry<MenuMessages>>) {
        messagePool = entries.filter { it.active }.flatMap { it.details!!.messages }
    }

    /** Null = allow vanilla */
    @JvmStatic
    fun getCustomSplash(): SplashRenderer? {
        val pool = messagePool
        if (pool.isEmpty()) return null
        return SplashRenderer(pool.random().copy().withStyle(SPLASH_STYLE))
    }
}
