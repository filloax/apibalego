package com.ruslan.apibalego.config

import com.ruslan.apibalego.ApibalegoMod
import com.teamresourceful.resourcefulconfig.api.loader.Configurator
import com.teamresourceful.resourcefulconfig.api.types.ResourcefulConfig

/**
 * Wrap/contain config initialization to avoid changing the main mod file
 * if the library changes initialization means between versions.
 */
object ApiBalegoConfigHandler {
    private val CONFIGURATOR = Configurator(ApibalegoMod.MOD_ID)
    var config: ResourcefulConfig? = null
        private set
    private val loadCallbacks: MutableList<(ResourcefulConfig) -> Unit> = mutableListOf()

    fun initConfig() {
        CONFIGURATOR.register(ApiBalegoConfig::class.java)
        config = CONFIGURATOR.getConfig(ApiBalegoConfig::class.java).also { c ->
            c.load { }
            loadCallbacks.forEach { it(c) }
        }
    }

    fun onConfigLoad(event: (ResourcefulConfig) -> Unit) {
        loadCallbacks.add(event)
        config?.let { event(it) }
    }

    fun saveConfig() {
        config?.save() ?: throw IllegalStateException("No config loaded!")
    }
}
