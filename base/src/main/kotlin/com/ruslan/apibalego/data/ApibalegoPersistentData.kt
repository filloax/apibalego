package com.ruslan.apibalego.data

import com.filloax.fxlib.api.codec.mutableSetOf
import com.filloax.fxlib.api.savedata.FxSavedData
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.ruslan.apibalego.ApiBalegoConstants.CMD_EVENTS_DATA
import com.ruslan.apibalego.utils.resLoc
import net.minecraft.server.MinecraftServer

/**
 * Tracks which one-shot remote commands have already run, so they aren't re-executed
 * on every data sync.
 */
class ApibalegoPersistentData private constructor(
    alreadyRan: Set<String> = setOf(),
) : FxSavedData<ApibalegoPersistentData>(CODEC) {
    val alreadyRan: MutableSet<String> = alreadyRan.toMutableSet()

    companion object {
        val CODEC: Codec<ApibalegoPersistentData> = RecordCodecBuilder.create { builder -> builder.group(
            Codec.STRING.mutableSetOf().fieldOf("alreadyRan").forGetter(ApibalegoPersistentData::alreadyRan),
        ).apply(builder, ::ApibalegoPersistentData) }

        private val DEF = define(resLoc(CMD_EVENTS_DATA), ::ApibalegoPersistentData, CODEC)

        fun get(server: MinecraftServer): ApibalegoPersistentData {
            return server.loadData(DEF)
        }
    }
}
