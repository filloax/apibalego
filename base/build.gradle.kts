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

    //#region Test deps

	// MC classes: reuse the deobfuscated jar from the moddevgradle vanilla-mode task
	testImplementation(files(tasks.named("createMinecraftArtifacts").map { it.outputs.files }))
	// MC runtime deps (brigadier, log4j, authlib, …): same BOM neoform pulls in
	testImplementation("net.neoforged:minecraft-dependencies:$minecraftVersion")

	socketIoLibs.forEach { testImplementation(it) }
	testImplementation(utils.getFilloaxlib()) { exclude(module = "kotlin-stdlib") }
	testImplementation(libs.kotlin.serialization) { exclude(module = "kotlin-stdlib") }
	testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.mockito:mockito-core:5.14.2")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    //#endregion
}

tasks.test {
	useJUnitPlatform()
	jvmArgs("-Dnet.bytebuddy.experimental=true")
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
	create(COMMON_GAMETEST_JAVA) {
		isCanBeResolved = false
		isCanBeConsumed = true
	}
}

artifacts {
	sourceSets.main.get().java.sourceDirectories.forEach { add(COMMON_JAVA, it) }
	sourceSets.main.get().kotlin.sourceDirectories.forEach { add(COMMON_JAVA, it) }
	sourceSets.main.get().resources.sourceDirectories.forEach { add(COMMON_RESOURCES, it) }
	sourceSets["gametest"].java.sourceDirectories.forEach { add(COMMON_GAMETEST_JAVA, it) }
	sourceSets["gametest"].kotlin.sourceDirectories.forEach { add(COMMON_GAMETEST_JAVA, it) }
}
