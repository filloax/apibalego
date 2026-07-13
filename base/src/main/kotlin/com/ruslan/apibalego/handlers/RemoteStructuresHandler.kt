package com.ruslan.apibalego.handlers

import com.filloax.fxlib.api.EventUtil
import com.filloax.fxlib.api.FxLibServices
import com.filloax.fxlib.api.json.BlockPosSerializer
import com.filloax.fxlib.api.json.IdentifierSerializer
import com.filloax.fxlib.api.structure.FixedStructureGeneration
import com.ruslan.apibalego.ApibalegoMod
import com.ruslan.apibalego.http.ApiEntry
import com.ruslan.apibalego.http.ApiEntryHandler
import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Rotation
import kotlin.jvm.optionals.getOrNull

/**
 * Spawns structures placed by the gamemaster at fixed positions.
 *
 * Overworld-only due to current requirements.
 */
object RemoteStructuresHandler : ApiEntryHandler<RemoteStructuresHandler.RemoteStructureSpawnData> {
    @Serializable
    data class RemoteStructureSpawnData(
        @Serializable(with = IdentifierSerializer::class)
        val structureId: Identifier,
        @Serializable(with = BlockPosSerializer::class)
        val startPos: BlockPos,
        val rotation: Rotation? = null,
    )

    /**
     * In case dependants need to know which structures are currently enabled.
     */
    val STRUCTS_TO_SPAWN_BY_ID: Map<String, RemoteStructureSpawnData>
        get() = structsToSpawnById

    private val structsToSpawnById = mutableMapOf<String, RemoteStructureSpawnData>()
    private val fixedStructureGeneration: FixedStructureGeneration = FxLibServices.fixedStructureGeneration

    /**
     * Replace the set of structures to spawn with ones described by the entry. Inactive entries and entries pointing
     * at non-existent structures are skipped.
     */
    override fun handleApiUpdate(server: MinecraftServer, entries: Collection<ApiEntry<RemoteStructureSpawnData>>) {
        EventUtil.runWhenServerStarted(server) { _ ->
            structsToSpawnById.clear()

            entries.forEach {
                if (!it.active) return@forEach
                val spawnData = it.details!!
                val id = spawnData.structureId
                val structureRef = server.registryAccess().lookup(Registries.STRUCTURE).getOrNull()?.getValue(id)
                if (structureRef == null) {
                    ApibalegoMod.LOGGER.error("Cannot queue non-existent structure $id")
                } else {
                    fixedStructureGeneration.register(server.overworld(), it.getSpawnId(), spawnData.startPos, id, spawnData.rotation ?: Rotation.NONE)
                    structsToSpawnById[it.getSpawnId()] = spawnData
                }
            }
        }
    }

    private fun ApiEntry<RemoteStructureSpawnData>.getSpawnId() = "apibalego_structure_${id}"
}