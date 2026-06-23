import com.ruslan.gradle.*

plugins {
	id("com.ruslan.gradle.multiloader-convention")
	id("com.ruslan.gradle.multiloader-gametest-base")

	alias(libs.plugins.moddevgradle)
}
val utils = project.utils(versionCatalogs, ext)

val modid: String by project
val modVersion: String by project
val versionType: String? by project
val minecraftVersion = libs.versions.minecraft.asProvider().get()

val versionSuffix = if (versionType?.isBlank() == true) "" else "-$versionType"

version = "$modVersion-$minecraftVersion$versionSuffix-base"

base {
	archivesName = property("archives_base_name") as String
}

neoForge {
	// vanilla mode, see moddevgradle docs
	neoFormVersion = libs.versions.neoform.get()
}

dependencies {
	compileOnly( libs.jsr305 )
	compileOnly( libs.log4j )
	compileOnly( libs.ow.asm )

	compileOnly( libs.kotlin.stdlib )
	compileOnly( libs.kotlin.reflect )
	compileOnly( libs.kotlin.serialization )

	socketIoLibs.forEach(this::compileOnly)

	compileOnly(utils.getResourcefulConfig())
	compileOnly(utils.getFilloaxlib())
}

configurations {
	create(COMMON_JAVA) {
		isCanBeResolved = false
		isCanBeConsumed = true
	}
	create(COMMON_RESOURCES) {
		isCanBeResolved = false
		isCanBeConsumed = true
	}
}

artifacts {
	sourceSets.main.get().java.sourceDirectories.forEach { add(COMMON_JAVA, it) }
	sourceSets.main.get().kotlin.sourceDirectories.forEach { add(COMMON_JAVA, it) }
	sourceSets.main.get().resources.sourceDirectories.forEach { add(COMMON_RESOURCES, it) }
}
