import com.ruslan.gradle.*

plugins {
    // see buildSrc
    id("com.ruslan.gradle.multiloader-loader")

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

neoForge {
    version = libs.versions.neoforge.asProvider().get()

    runs {
        create("apibalego_client") {
            client()
        }

        create("apibalego_server") {
            server()
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")
            systemProperty("neoforge.enabledGameTestNamespaces", modid)
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        register(modid) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation( libs.jsr305 )

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
    }
    implementation( libs.kotlinevents )
    jarJar( libs.kotlinevents )
}
