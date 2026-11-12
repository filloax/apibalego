package com.ruslan.apibalego.trigger

import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import com.ruslan.apibalego.http.ApiEntryRaw
import com.ruslan.apibalego.http.ApiEntryRegistry
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.http.ID_API_HANDLER_TRIGGER
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

/**
 * Processing of Trigger API entries: triggers are expanded into normal entries if conditions
 * are met (triggers are triggered)
 */
object TriggerHandler {
    private const val ID_PREFIX = "trigger"

    fun isTrigger(entry: ApiEntryRaw) = entry.type.key == ID_API_HANDLER_TRIGGER

    /**
     * Run in mixin, mod event, etc. to run trigger logic. MUST run after the
     * state that the trigger uses to get its runKey is set
     */
    @JvmStatic
    fun runTrigger(player: ServerPlayer, triggerId: Identifier) {
        val triggerType = TriggerRegistry.lookupRaw(triggerId)
        val entries = parseTriggers(GamemasterApi.activeEntries).filter { it.details?.triggerType == triggerType }
        if (entries.isEmpty()) return

        // reload server gm data
        refreshForPlayer(player, entries)
    }

    /**
     * Get triggered triggers for player, and refresh api entries
     * with their events
     */
    fun refreshForPlayer(player: ServerPlayer, entries: List<ApiEntry<TriggerRaw>>) {
        val triggeredEventTypes = resolveTriggerEntries(entries, listOf(player)).map { it.type }.toSet()
        if (triggeredEventTypes.isNotEmpty())
            GamemasterApi.redispatch(player.level().server, triggeredEventTypes)
    }

    /**
     * Get triggered triggers for player, and refresh api entries
     * with their events
     */
    fun refreshForPlayer(player: ServerPlayer, rawEntries: Collection<ApiEntryRaw>) =
        refreshForPlayer(player, parseTriggers(rawEntries))

    /**
     * Expand triggers into api entries for all events of triggered triggers.
     * Must run on the server thread.
     */
    fun resolveTriggerEntries(entries: List<ApiEntry<TriggerRaw>>, players: Collection<ServerPlayer>): List<ApiEntryRaw> {
        return entries.flatMap { entry ->
            val trigger = resolve(entry) ?: return@flatMap listOf()

            players.flatMap { player -> getEntriesForPlayer(player, trigger, entry.id) }
        }.distinctBy { it.id } // shared events may have same id with more players
    }

    /**
     * Expand triggers into api entries for all events of triggered triggers.
     * Must run on the server thread.
     */
    fun resolveTriggerEntries(rawEntries: Collection<ApiEntryRaw>, players: Collection<ServerPlayer>) =
        resolveTriggerEntries(parseTriggers(rawEntries), players)

    private fun <T : Any> getEntriesForPlayer(player: ServerPlayer, trigger: Trigger<T>, triggerId: String): List<ApiEntryRaw> {
        if (trigger.onlyPlayers.isNotEmpty()
            && player.uuid.toString() !in trigger.onlyPlayers
            && player.scoreboardName !in trigger.onlyPlayers
        )
            return listOf()

        val runKey = getRunKey(player, trigger) ?: return listOf()

        return trigger.events.map {
            val shared = it.type.alwaysShared
            ApiEntryRaw(
                id = if (shared) getTriggerEntryId(it, null, triggerId) else getTriggerEntryId(it, player, triggerId, runKey),
                type = it.type,
                details = it.details,
                active = true,
                forPlayer = if (shared) null else player.uuid,
            )
        }
    }

    private fun <T : Any> getRunKey(player: ServerPlayer, trigger: Trigger<T>): String? {
        val handler = trigger.triggerType.handler

        return handler.runKey(player, trigger.triggerDetails)
    }

    private fun getTriggerEntryId(entry: TriggerApiEntry, player: ServerPlayer?, triggerId: String, runKey: String? = null): String {
        return if (player == null) "$ID_PREFIX/$triggerId/${entry.childKey}"
        else "$ID_PREFIX/$triggerId/${player.uuid}/$runKey/${entry.childKey}"
    }

    private fun resolve(entry: ApiEntry<TriggerRaw>): Trigger<*>? {
        if (entry.details == null) {
            ApibalegoMod.LOGGER.error("Triggers need a details object")
            return null
        }
        return try {
            entry.details.resolve(entry.details.triggerType)
        } catch (e: Exception) {
            ApibalegoMod.LOGGER.error("Failed resolving trigger '${entry.id}': ${e.message}")
            null
        }
    }

    private fun parseTriggers(rawEntries: Collection<ApiEntryRaw>): List<ApiEntry<TriggerRaw>> {
        return rawEntries.filter(this::isTrigger).mapNotNull { raw ->
            val type = ApiEntryRegistry.lookup<TriggerRaw>(ID_API_HANDLER_TRIGGER)
            try {
                raw.resolve(type, type.parseDetails(raw))
            } catch (e: Exception) {
                ApibalegoMod.LOGGER.error("Failed parsing trigger '${raw.id}': ${e.message}")
                null
            }
        }
    }
}
