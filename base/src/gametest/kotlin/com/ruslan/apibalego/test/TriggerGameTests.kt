package com.ruslan.apibalego.test

import com.filloax.fxlib.api.entity.getPersistData
import com.ruslan.apibalego.ApiBalegoConstants
import com.ruslan.apibalego.api.Apibalego
import com.ruslan.apibalego.config.ApiBalegoConfig
import com.ruslan.apibalego.handlers.RemoteCommandExecHandler
import com.ruslan.apibalego.handlers.ToastHandler
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.ApiEntryType
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.http.ID_API_HANDLER_COMMAND
import com.ruslan.apibalego.http.ID_API_HANDLER_TOAST
import com.ruslan.apibalego.http.ID_API_HANDLER_TRIGGER
import com.ruslan.apibalego.trigger.AdvancementTriggerHandler
import com.ruslan.apibalego.trigger.ID_TRIGGER_RESPAWN
import com.ruslan.apibalego.trigger.RespawnTriggerHandler
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.GameTestSequence
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.stats.Stats
import kotlin.jvm.optionals.getOrNull

/**
 * Trigger and player-scoped entry steps, chained into [ApibalegoGameTests.combinedHandlerLifecycleTest].
 */
object TriggerGameTests {
    private fun commandDetails(command: String) = buildJsonObject { put("command", JsonPrimitive(command)) }

    @Suppress("UNCHECKED_CAST")
    private fun commandEntry(id: String, command: String) = ApiEntry(
        type = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_COMMAND) as ApiEntryType<RemoteCommandExecHandler.CommandDetails>,
        details = RemoteCommandExecHandler.CommandDetails(command),
        id = id,
    )

    /**
     * Player-scoped commands run as the player (@s) with the server's permissions, once per player
     * and entry id; player-scoped toasts are tracked in the player's memory.
     */
    fun playerScopedStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        // Config is toggled inside the sequence, as previous steps restore it while running
        var prevEnabled = ApiBalegoConfig.remoteCommandExecution
        val player = helper.makeMockServerPlayerInLevelAlt()
        val other = helper.makeMockServerPlayerInLevelAlt()
        val tag = "gt_player_cmd"
        val entry = commandEntry("gt-player-cmd-${System.nanoTime()}", "tag @s add $tag")

        return sequence
            .thenExecute {
                prevEnabled = ApiBalegoConfig.remoteCommandExecution
                ApiBalegoConfig.remoteCommandExecution = true
                RemoteCommandExecHandler.handleApiUpdatePlayer(player, listOf(entry))
            }
            .thenExecute {
                helper.assertTrue(tag in player.entityTags(), "player-scoped command did not run as the player")
                helper.assertFalse(tag in other.entityTags(), "player-scoped command must not affect other players")
                helper.assertTrue(
                    player.getPersistData().getCompound(ApiBalegoConstants.REMOTE_COMMAND_MEMORY).get().contains(entry.id),
                    "player-scoped command not tracked in player memory",
                )
                player.removeTag(tag)
                RemoteCommandExecHandler.handleApiUpdatePlayer(player, listOf(entry))
            }
            .thenExecute {
                helper.assertFalse(tag in player.entityTags(), "same player-scoped command id must not run twice")
                RemoteCommandExecHandler.handleApiUpdatePlayer(other, listOf(entry))
            }
            .thenExecute {
                helper.assertTrue(tag in other.entityTags(), "same entry id must still run for a different player")

                @Suppress("UNCHECKED_CAST")
                val toastType = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_TOAST) as ApiEntryType<ToastHandler.ToastData>
                val toast = ToastHandler.ToastData(title = Component.literal("Player Toast"))
                val toastId = "gt-player-toast-${System.nanoTime()}"
                ToastHandler.handleApiUpdatePlayer(player, listOf(ApiEntry(type = toastType, details = toast, id = toastId)))

                val key = toast.title.string + toastId
                helper.assertTrue(
                    player.getPersistData().getCompound(ApiBalegoConstants.CUSTOM_TOAST_MEMORY).get().contains(key),
                    "player-scoped toast not marked as seen",
                )
                helper.assertFalse(
                    other.getPersistData().getCompound(ApiBalegoConstants.CUSTOM_TOAST_MEMORY).getOrNull()?.contains(key) == true,
                    "player-scoped toast must not be sent to other players",
                )
                ApiBalegoConfig.remoteCommandExecution = prevEnabled
            }
    }

    /** Run keys of the built-in trigger types follow death count and advancement progress. */
    fun builtinTriggersStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        val player = helper.makeMockServerPlayerInLevelAlt()
        val server = helper.level.server
        val stoneId = Identifier.withDefaultNamespace("story/mine_stone")
        val stone = AdvancementTriggerHandler.AdvancementTriggerDetails(stoneId)
        val unknown = AdvancementTriggerHandler.AdvancementTriggerDetails(Identifier.fromNamespaceAndPath("gametest", "unknown"))

        return sequence.thenExecute {
            val deaths = player.stats.getValue(Stats.CUSTOM.get(Stats.DEATHS))
            helper.assertTrue(
                RespawnTriggerHandler.runKey(player, null) == deaths.toString(),
                "respawn run key should be the death count",
            )
            player.awardStat(Stats.DEATHS)
            helper.assertTrue(
                RespawnTriggerHandler.runKey(player, null) == (deaths + 1).toString(),
                "respawn run key should change with the death count",
            )

            helper.assertTrue(AdvancementTriggerHandler.runKey(player, stone) == null, "advancement not done yet, run key must be null")
            helper.assertTrue(AdvancementTriggerHandler.runKey(player, unknown) == null, "unknown advancement must not trigger")
            player.advancements.award(server.advancements.get(stoneId)!!, "get_stone")
            helper.assertTrue(AdvancementTriggerHandler.runKey(player, stone) != null, "advancement done, run key must be set")
        }
    }

    private fun triggerEntry(id: String, triggerType: Identifier, triggerDetails: JsonElement?, command: String) = ApiEntryRaw(
        type = ApiEntryRegistry.lookupRaw(ID_API_HANDLER_TRIGGER),
        details = buildJsonObject {
            put("triggerType", JsonPrimitive(triggerType.toString()))
            triggerDetails?.let { put("triggerDetails", it) }
            put("events", buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive(ID_API_HANDLER_COMMAND.toString()))
                    put("details", commandDetails(command))
                })
            })
        },
        id = id,
    )

    // Private as only the data sync should call it, reflection avoids exposing it for tests
    private fun gamemasterUpdate(entries: List<ApiEntryRaw>, server: MinecraftServer) {
        GamemasterApi::class.java.getDeclaredMethod("handleUpdate", List::class.java, MinecraftServer::class.java)
            .apply { isAccessible = true }
            .invoke(GamemasterApi, entries, server)
    }

    /**
     * End to end trigger flow through the gamemaster entries: a respawn trigger runs on the first life
     * and again on each new life (but not twice in the same life), an advancement trigger runs once
     * the advancement is awarded (via the mixin).
     *
     * Dispatches every entry type, so must run after the steps relying on structure/datapack state.
     */
    fun triggerFlowStep(helper: GameTestHelper, sequence: GameTestSequence): GameTestSequence {
        val server = helper.level.server
        var prevEnabled = ApiBalegoConfig.remoteCommandExecution
        val player = helper.makeMockServerPlayerInLevelAlt()
        val lifeTag = "gt_trigger_life"
        val advTag = "gt_trigger_adv"
        val ironId = Identifier.withDefaultNamespace("story/smelt_iron")

        return sequence
            .thenExecute {
                prevEnabled = ApiBalegoConfig.remoteCommandExecution
                ApiBalegoConfig.remoteCommandExecution = true
                gamemasterUpdate(listOf(
                    triggerEntry("gt-trigger-life-${System.nanoTime()}", ID_TRIGGER_RESPAWN, null, "tag @s add $lifeTag"),
                    triggerEntry(
                        "gt-trigger-adv-${System.nanoTime()}",
                        Identifier.fromNamespaceAndPath("apibalego", "advancement"),
                        buildJsonObject { put("advancement", JsonPrimitive(ironId.toString())) },
                        "tag @s add $advTag",
                    ),
                ), server)
            }
            .thenWaitUntil {
                helper.assertTrue(lifeTag in player.entityTags(), "respawn trigger should run on the first life")
            }
            .thenExecute {
                helper.assertFalse(advTag in player.entityTags(), "advancement trigger must not run before the advancement")
                player.advancements.award(server.advancements.get(ironId)!!, "iron")
            }
            .thenWaitUntil {
                helper.assertTrue(advTag in player.entityTags(), "advancement trigger should run once awarded")
            }
            .thenExecute {
                player.removeTag(lifeTag)
                Apibalego.runTrigger(player, ID_TRIGGER_RESPAWN)
            }
            .thenExecuteAfter(5) {
                helper.assertFalse(lifeTag in player.entityTags(), "respawn trigger must not run again in the same life")
                player.awardStat(Stats.DEATHS)
                Apibalego.runTrigger(player, ID_TRIGGER_RESPAWN)
            }
            .thenWaitUntil {
                helper.assertTrue(lifeTag in player.entityTags(), "respawn trigger should run again on a new life")
            }
            .thenExecute {
                gamemasterUpdate(listOf(), server)
                ApiBalegoConfig.remoteCommandExecution = prevEnabled
            }
    }
}
