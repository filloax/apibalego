import com.ruslan.gradle.*

plugins {
	// see buildSrc
	id("com.ruslan.gradle.multiloader-loader")

	alias(libs.plugins.loom)
}

val utils = project.utils(versionCatalogs, ext)

val modid: String by project
val modVersion: String by project
val versionType: String? by project
val minecraftVersion = libs.versions.minecraft.asProvider().get()
val includeDeps = (property("includeDeps") as String).toBoolean()

val versionSuffix = if (versionType?.isBlank() == true) "" else "-$versionType"

version = "$modVersion-$minecraftVersion$versionSuffix-fabric"

if (includeDeps) println("Including dependencies for test mode")

loom {
	mods {
		register(modid) {
			sourceSet(sourceSets.main.get())
		}
	}
}

dependencies {
	minecraft( libs.minecraft )
	implementation( libs.jsr305 )

	socketIoLibs.forEach {
		implementation(it)
		include(it)
	}

	implementation( libs.fabric )
	implementation( libs.fabric.api ) {
		exclude(module = "fabric-api-deprecated")
	}

	listOf(
		libs.fabric.kotlin,
		libs.modmenu,
		utils.getResourcefulConfig("fabric"),
	).forEach {
		implementation(it)
		if (includeDeps)
			include(it)
	}

	implementation( libs.kotlin.serialization ) { exclude(module = "kotlin-stdlib") }

	utils.getFilloaxlib("fabric").let{
		implementation(it) { exclude(module = "kotlin-stdlib") }
		include(it)
	}
	implementation( libs.kotlinevents )
	include( libs.kotlinevents )
}
