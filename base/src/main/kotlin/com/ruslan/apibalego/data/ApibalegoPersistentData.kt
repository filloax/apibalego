package com.ruslan.apibalego.data

import com.filloax.fxlib.api.codec.mutableSetOf
import com.filloax.fxlib.api.savedata.FxSavedData
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.ruslan.apibalego.ApiBalegoConstants.APIBALEGO_PERSISTENT_DATA
import com.ruslan.apibalego.utils.id
import net.minecraft.server.MinecraftServer

/**
 * Tracks which one-shot remote commands have already run, so they aren't re-executed
 * on every data sync.
 */
class ApibalegoPersistentData private constructor(
    alreadyRanCommands: Set<String> = setOf(),
    lastEndpointOutputs: Map<String, String> = mapOf(),
) : FxSavedData<ApibalegoPersistentData>(CODEC) {
    val alreadyRanCommands: MutableSet<String> = alreadyRanCommands.toMutableSet()
    val lastEndpointOutputs: MutableMap<String, String> = lastEndpointOutputs.toMutableMap()

    companion object {
        val CODEC: Codec<ApibalegoPersistentData> = RecordCodecBuilder.create { builder -> builder.group(
            Codec.STRING.mutableSetOf().fieldOf("alreadyRan").forGetter(ApibalegoPersistentData::alreadyRanCommands),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("lastEndpointOutputs").forGetter(ApibalegoPersistentData::lastEndpointOutputs),
        ).apply(builder, ::ApibalegoPersistentData) }

        private val DEF = define(id(APIBALEGO_PERSISTENT_DATA), ::ApibalegoPersistentData, CODEC)

        fun get(server: MinecraftServer): ApibalegoPersistentData {
            return server.loadData(DEF)
        }
    }
}
