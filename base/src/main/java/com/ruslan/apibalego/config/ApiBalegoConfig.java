package com.ruslan.apibalego.config;

import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.Config;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigInfo;

/**
 * ResourcefulConfig config for the main connection of the mod.
 */
@Config("apibalego")
@ConfigInfo(
        icon = "wifi",
        title = "Apibalego",
        description = "Gamemaster mode connection settings"
)
public final class ApiBalegoConfig {
    public static final String T_PREF = "apibalego.config.web.";

    @ConfigEntry(id = "webDataSync", translation = T_PREF + "webDataSync.name")
    @Comment(value = "CAREFUL: enables gamemaster-mode, allowing the player you decide to connect to to control the mod's features and more. Requires reloading the world.", translation = T_PREF + "webDataSync.comment")
    public static boolean webDataSync = false;
    @ConfigEntry(id = "dataSyncUrl", translation = T_PREF + "dataSyncUrl.name")
    @Comment(value = "Only connect to trusted sources!", translation = T_PREF + "dataSyncUrl.comment")
    public static String dataSyncUrl = "http://localhost:5000";
    @ConfigEntry(id = "dataSyncEndpoint", translation = T_PREF + "dataSyncEndpoint.name")
    public static String dataSyncEndpoint = "server_data";
    @ConfigEntry(id = "dataSyncApiKey", translation = T_PREF + "dataSyncApiKey.name")
    public static String dataSyncApiKey = "";
    @ConfigEntry(id = "dataSyncReloadTime", translation = T_PREF + "dataSyncReloadTime.name")
    @Comment(value = "How much time (in minutes) must pass between each server query. Must be at least 10 seconds.", translation = T_PREF + "dataSyncReloadTime.comment")
    public static float dataSyncReloadTime = 5f;

    @ConfigEntry(id = "liveUpdateService", translation = T_PREF + "liveUpdateService.name")
    @Comment(value = "CAREFUL: creates a websocket connection with the gamemaster, allowing them to control the mod in real time. Requires reloading the world.", translation = T_PREF + "liveUpdateService.comment")
    public static boolean liveUpdateService = false;
    @ConfigEntry(id = "liveUpdateUrl", translation = T_PREF + "liveUpdateUrl.name")
    @Comment(value = "Only connect to trusted sources!", translation = T_PREF + "liveUpdateUrl.comment")
    public static String liveUpdateUrl = "http://localhost:5000";
    @ConfigEntry(id = "liveUpdatePort", translation = T_PREF + "liveUpdatePort.name")
    public static int liveUpdatePort = -1;

    @ConfigEntry(id = "remoteCommandExecution", translation = T_PREF + "remoteCommandExecution.name")
    @Comment(value = "Allow the gamemaster to execute any arbitrary command.", translation = T_PREF + "remoteCommandExecution.comment")
    public static boolean remoteCommandExecution = false;

    @ConfigEntry(id = "remoteDatapackSync", translation = T_PREF + "remoteDatapackSync.name")
    @Comment(value = "Allow the gamemaster to download and enable/disable datapacks. Note that newly loaded content may not be sent to already-connected players until they rejoin.", translation = T_PREF + "remoteDatapackSync.comment")
    public static boolean remoteDatapackSync = false;
    @ConfigEntry(id = "remoteDatapackAllowExternalUrl", translation = T_PREF + "remoteDatapackAllowExternalUrl.name")
    @Comment(value = "If disabled, datapack download URLs must be on the same origin as the data sync URL. Enable to allow downloading from any URL.", translation = T_PREF + "remoteDatapackAllowExternalUrl.comment")
    public static boolean remoteDatapackAllowExternalUrl = false;

    @ConfigEntry(id = "clientDataSync", translation = T_PREF + "clientDataSync.name")
    @Comment(value = "CAREFUL: allows the client to connect to a gamemaster on its own, independent of any joined server (e.g. from the main menu).", translation = T_PREF + "clientDataSync.comment")
    public static boolean clientDataSync = false;
    @ConfigEntry(id = "clientDataSyncUrl", translation = T_PREF + "clientDataSyncUrl.name")
    @Comment(value = "Only connect to trusted sources!", translation = T_PREF + "clientDataSyncUrl.comment")
    public static String clientDataSyncUrl = "http://localhost:5000";
    @ConfigEntry(id = "clientDataSyncEndpoint", translation = T_PREF + "clientDataSyncEndpoint.name")
    public static String clientDataSyncEndpoint = "client_data";
    @ConfigEntry(id = "clientDataSyncApiKey", translation = T_PREF + "clientDataSyncApiKey.name")
    public static String clientDataSyncApiKey = "";
    @ConfigEntry(id = "clientDataSyncReloadTime", translation = T_PREF + "clientDataSyncReloadTime.name")
    @Comment(value = "How much time (in minutes) must pass between each client query. Must be at least 10 seconds.", translation = T_PREF + "clientDataSyncReloadTime.comment")
    public static float clientDataSyncReloadTime = 5f;
}
