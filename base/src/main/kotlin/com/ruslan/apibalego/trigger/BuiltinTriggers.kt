package com.ruslan.apibalego.trigger

import com.filloax.fxlib.api.json.IdentifierSerializer
import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.utils.id
import kotlinx.serialization.Serializable
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats

val ID_TRIGGER_ADVANCEMENT = id("advancement")
val ID_TRIGGER_RESPAWN = id("respawn")

object BuiltinTriggers {
    fun registerAll() {
        TriggerRegistry.register(
            ID_TRIGGER_ADVANCEMENT,
            AdvancementTriggerHandler.AdvancementTriggerDetails.serializer(),
            AdvancementTriggerHandler,
        )
        TriggerRegistry.register(
            ID_TRIGGER_RESPAWN,
            null,
            RespawnTriggerHandler,
            Nothing::class,
        )
    }
}

object AdvancementTriggerHandler : TriggerTypeHandler<AdvancementTriggerHandler.AdvancementTriggerDetails> {
    @Serializable
    data class AdvancementTriggerDetails(
        @Serializable(with = IdentifierSerializer::class)
        val advancement: Identifier,
    )

    override fun runKey(player: ServerPlayer, details: AdvancementTriggerDetails?): String? {
        details ?: return null
        val holder = player.level().server.advancements.get(details.advancement) ?: run {
            ApibalegoMod.LOGGER.warn("Advancement trigger: unknown advancement ${details.advancement}")
            return null
        }
        return if (player.advancements.getOrStartProgress(holder).isDone) "done" else null
    }

    @JvmStatic
    fun onAdvancementAwarded(player: ServerPlayer) {
        TriggerHandler.runTrigger(player, ID_TRIGGER_ADVANCEMENT)
    }
}

object RespawnTriggerHandler : TriggerTypeHandler<Nothing> {
    override fun runKey(player: ServerPlayer, details: Nothing?): String =
        player.stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)).toString()

    fun onRespawn(player: ServerPlayer) {
        TriggerHandler.runTrigger(player, ID_TRIGGER_RESPAWN)
    }
}
