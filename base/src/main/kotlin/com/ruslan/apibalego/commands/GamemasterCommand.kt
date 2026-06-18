package com.ruslan.apibalego.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.http.DataRemoteSync
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.*
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Utility commands for server admins that want to manage gamemaster mode.
 *
 * Consumer mods can hook extra reload behavior via [onReloadHooks].
 */
object GamemasterCommand {
    /** Consumer mods add reload behavior here (run on enable/url-change). */
    val onReloadHooks = mutableListOf<(MinecraftServer) -> Unit>()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, registryAccess: CommandBuildContext, environment: CommandSelection) {
        dispatcher.register(
            literal("gmaster").requires(hasPermission(LEVEL_GAMEMASTERS))
                .then(literal("help")
                    .executes { ctx -> printHelp(ctx.source) }
                )
                .then(literal("status")
                    .executes { ctx -> printStatus(ctx.source) }
                )
                .then(literal("reload")
                    .executes { ctx -> reload(ctx.source) }
                )
                .then(literal("enable")
                    .executes { ctx -> setEnabled(ctx.source, true) }
                )
                .then(literal("disable")
                    .executes { ctx -> setEnabled(ctx.source, false) }
                )
                .then(literal("url")
                    .then(argument("webUrl", StringArgumentType.string())
                        .executes { ctx -> setUrl(ctx.source, StringArgumentType.getString(ctx, "webUrl")) }
                    )
                )
        )
    }

    private fun printHelp(source: CommandSourceStack): Int {
        source.sendSuccess({ Component.translatable("apibalego.commands.gmaster.help") }, false)
        return 1
    }

    private fun printStatus(source: CommandSourceStack): Int {
        if (!ApiBalegoConfig.webDataSync) {
            source.sendSystemMessage(Component.translatable("apibalego.commands.gmaster.status_off"))
            return 0
        }
        val suffix = if (DataRemoteSync.lastSyncSuccessful) "ok" else "ko"
        source.sendSystemMessage(Component.translatable(
            "apibalego.commands.gmaster.status_on_$suffix",
            ApiBalegoConfig.dataSyncUrl, DataRemoteSync.lastSyncSuccessful
        ))
        source.sendSystemMessage(Component.translatable(
            "apibalego.commands.gmaster.status_time",
            formatTime(DataRemoteSync.lastSuccessfulUpdateTime), formatTime(DataRemoteSync.lastUpdateTime)
        ))

        return 1
    }

    private fun formatTime(time: LocalDateTime?) =
        time?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(it) } ?: "-"

    private fun reload(source: CommandSourceStack): Int {
        if (ApiBalegoConfig.webDataSync) {
            source.sendSystemMessage(Component.translatable("apibalego.commands.gmaster.reload_start"))
            try {
                DataRemoteSync.doSync(ApiBalegoConfig.dataSyncUrl, source.server).thenAccept {
                    if (it) {
                        source.sendSuccess(
                            { Component.translatable("apibalego.commands.gmaster.reload_success") }, true)
                    } else {
                        source.sendFailure(Component.translatable("apibalego.commands.gmaster.reload_failure"))
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                source.sendFailure(Component.translatable("apibalego.commands.gmaster.reload_failure"))
                return 0
            }
            return 1
        } else {
            source.sendFailure(Component.translatable("apibalego.commands.gmaster.disabled"))
            return 0
        }
    }

    private fun setEnabled(source: CommandSourceStack, value: Boolean): Int {
        val suffix = if (value) "on" else "off"
        if (ApiBalegoConfig.webDataSync != value) {
            ApiBalegoConfig.webDataSync = value
            ApiBalegoConfigHandler.saveConfig()
            runReloadHooks(source.server)
            source.sendSuccess({Component.translatable("apibalego.commands.gmaster.set_$suffix", ApiBalegoConfig.dataSyncUrl).withStyle(ChatFormatting.YELLOW)}, true)
        } else {
            source.sendSuccess({Component.translatable("apibalego.commands.gmaster.already_$suffix", ApiBalegoConfig.dataSyncUrl)}, true)
        }
        return 1
    }

    private fun setUrl(source: CommandSourceStack, url: String): Int {
        if (!isValidUrl(url)) {
            source.sendFailure(Component.translatable("apibalego.commands.gmaster.url_not_valid", url))
            return 0
        }
        ApiBalegoConfig.dataSyncUrl = url
        ApiBalegoConfigHandler.saveConfig()
        runReloadHooks(source.server)
        source.sendSuccess({ Component.translatable("apibalego.commands.gmaster.url_set", url).withStyle(ChatFormatting.YELLOW) }, true)
        return 1
    }

    private fun isValidUrl(urlString: String): Boolean {
        return try {
            val url = URI(urlString).toURL()
            (url.protocol == "http" || url.protocol == "https")
                    && url.host.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun runReloadHooks(server: MinecraftServer) {
        onReloadHooks.forEach { it(server) }
        DataRemoteSync.doSync(ApiBalegoConfig.dataSyncUrl, server)
    }
}
