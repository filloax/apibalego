package com.ruslan.apibalego.utils

import com.filloax.fxlib.api.FxLibServices
import com.ruslan.apibalego.Apibalego
import com.ruslan.apibalego.Apibalego.Companion.MOD_NAME
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger

fun resLoc(str: String): Identifier {
    return Identifier.fromNamespaceAndPath(Apibalego.MOD_ID, str)
}

class ApibalegoLogger(private val logger: Logger) {
    private val prefix by lazy {
        // The prefix is already present on Neoforge and Fabric's dev env
        if (Apibalego.isNeoforge || FxLibServices.platform.isDevEnvironment()) ""
        else "[$MOD_NAME] "
    }
    fun log(level: Level, msg: String, vararg params: Any?) = logger.log(level, "$prefix$msg", *params)
    fun info(msg: String, vararg params: Any?) = logger.info("$prefix$msg", *params)
    fun warn(msg: String, vararg params: Any?) = logger.warn("$prefix$msg", *params)
    fun error(msg: String, vararg params: Any?) = logger.error("$prefix$msg", *params)
}
