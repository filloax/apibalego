package com.ruslan.apibalego.http

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Rotation

/**
 * Loader/consumer-agnostic representation of a gamemaster event. Consumer mods
 * map their own event shape onto this before handing it to [ApiEventRegistry.dispatch].
 * <br>
 * Reason for name of fields like desc, etc. is largely because this originated as a
 * structure spawn config, and was later generalized to other events.
 */
data class GenericApiEvent(
    val name: String,
    val active: Boolean,
    val desc: String? = null,
    val pos: BlockPos? = null,
    val rotation: Rotation? = null,
)
