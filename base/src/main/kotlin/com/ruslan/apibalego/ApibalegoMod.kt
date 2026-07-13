package com.ruslan.apibalego

import com.ruslan.apibalego.client.http.BuiltinClientApiHandlers
import com.ruslan.apibalego.client.http.ClientDataSync
import com.ruslan.apibalego.client.http.ClientGamemasterApi
import com.ruslan.apibalego.client.socket.BuiltinClientLiveUpdateEvents
import com.ruslan.apibalego.client.socket.ClientLiveUpdatesConnection
import com.ruslan.apibalego.config.ApiBalegoConfigHandler
import com.ruslan.apibalego.http.BuiltinApiHandlers
import com.ruslan.apibalego.http.GamemasterApi
import com.ruslan.apibalego.network.ApiBalegoPackets
import com.ruslan.apibalego.socket.BuiltinLiveUpdateEvents
import com.ruslan.apibalego.utils.ApibalegoLogger
import org.apache.logging.log4j.LogManager
import java.nio.file.Path

object ApibalegoMod {
    const val MOD_ID = "apibalego"
    const val MOD_NAME = "Apibalego"

    @JvmField
    val LOGGER = ApibalegoLogger(LogManager.getLogger(MOD_NAME))

    var isNeoforge = false      // set to true in ApiBalegoNeo for custom logic

    // Used for datapacks/resourcepacks
    lateinit var gameDir: Path
        private set

    private var preInitialized = false
    private var initialized = false
    private var clientPreInitialized = false
    private var clientInitialized = false

    /**
     * Things preload stuff needs loaded
     */
    @JvmStatic
    fun preInit(gameDir: Path) {
        if (preInitialized) return
        preInitialized = true
        this.gameDir = gameDir

        ApiBalegoConfigHandler.initConfig()
        BuiltinApiHandlers.registerAll()
    }

    @JvmStatic
    fun init(gameDir: Path) {
        if (initialized) return
        initialized = true
        preInit(gameDir)

        LOGGER.info("Initializing")

        BuiltinLiveUpdateEvents.registerAll()

        GamemasterApi.init()

        ApiBalegoPackets.registerPacketsS2C()
        ApiBalegoPackets.registerPacketsC2S()

        ApiBalegoModEvents.get().initCallbacks()

        LOGGER.info("Initialized!")
    }

    /**
     * The subset of [initClient] that client-side pack preload (see [PreloadPackSyncClient]) needs
     * before a Minecraft instance exists: [preInit] plus the client handler-type registry
     * ([com.ruslan.apibalego.client.http.ClientApiEntryRegistry]). Callable from Java since Fabric's
     * client mixin forces this early - see ClientPackRepositoryMixin.
     */
    @JvmStatic
    fun preInitClient(gameDir: Path) {
        preInit(gameDir)
        if (clientPreInitialized) return
        clientPreInitialized = true

        BuiltinClientApiHandlers.registerAll()
    }

    /**
     * Initialize the client-only parts of the mod. Call from each loader's client entrypoint,
     * after [init]. Calling more than once is harmless.
     */
    fun initClient() {
        if (clientInitialized) return
        clientInitialized = true
        preInitClient(gameDir)

        BuiltinClientLiveUpdateEvents.registerAll()
        ClientGamemasterApi.init()
        ClientDataSync.start()
        ClientLiveUpdatesConnection.clientStart()

        LOGGER.info("Initialized client!")
    }
}
