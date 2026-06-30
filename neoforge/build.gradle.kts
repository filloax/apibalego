import com.ruslan.gradle.*

plugins {
    // see buildSrc
    id("com.ruslan.gradle.multiloader-loader")
    // id("com.ruslan.gradle.multiloader-gametest-loader")  // disabled: neoforge gametests don't work

    alias(libs.plugins.moddevgradle)
}

val utils = project.utils(versionCatalogs, ext)

val modid: String by project
val modVersion: String by project
val versionType: String? by project
val minecraftVersion = libs.versions.minecraft.asProvider().get()
val includeDeps = (property("includeDeps") as String).toBoolean()

val versionSuffix = if (versionType?.isBlank() == true) "" else "-$versionType"

version = "$modVersion-$minecraftVersion$versionSuffix-neoforge"

if (includeDeps) println("Including dependencies for test mode")

// val gametest: SourceSet = sourceSets.create("gametest") {
//     compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
//     runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
// }

neoForge {
    version = libs.versions.neoforge.asProvider().get()

//    accessTransformers.files.setFrom(project(BASE_PROJECT).file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        create("client") {
            client()
            ideName = "APIBalego - NeoForge Client"
        }

        create("server") {
            server()
            ideName = "APIBalego - NeoForge Server"
        }

        // create("gameTestServer") {
        //     type = "gameTestServer"
        //     ideName = "APIBalego - Game Test Server"
        // }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("neoforge.enabledGameTestNamespaces", modid)

            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modid) {
            sourceSet(sourceSets.main.get())
            // sourceSet(sourceSets["gametest"])
        }
    }
}

// configurations {
//     create(COMMON_GAMETEST_RESOURCES) { isCanBeResolved = true }
// }

// tasks.named<ProcessResources>("processGametestResources") {
//     dependsOn(configurations.getByName(COMMON_GAMETEST_RESOURCES))
//     from(configurations.getByName(COMMON_GAMETEST_RESOURCES))
// }

dependencies {
    implementation( libs.jsr305 )
    // COMMON_GAMETEST_RESOURCES(project(path = BASE_PROJECT, configuration = COMMON_GAMETEST_RESOURCES))

    socketIoLibs.forEach {
        implementation(it)
        jarJar(it)
    }

    listOf(
        libs.kotlinforge,
        utils.getResourcefulConfig("neoforge"),
    ).forEach {
        implementation(it)
        if (includeDeps)
            jarJar(it)
    }

    utils.getFilloaxlib("neoforge").let{
        implementation(it) { exclude(module = "kotlin-stdlib") }
        jarJar(it)
        // need this to fix dev runs (aka gametest)
        // does not work as-is, as it makes it try to load a library as type accesstransformers, which
        // does not work
//        accessTransformers(it)
    }
    implementation( libs.kotlinevents )
    jarJar( libs.kotlinevents )
}
