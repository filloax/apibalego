package com.ruslan.gradle

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
}

// explicitly state Action otherwise IDE complains
sourceSets.create("gametest", Action {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
})

// Shared gametest code is consumed two ways by the loaders. COMMON_GAMETEST_JAVA exposes it as
// SOURCES, compiled into each loader's gametest source set so the shared `object` singletons live
// on the loader mod's classloader at runtime; a compiled-class runtime dependency would put them on
// a separate classloader and duplicate them. GAMETEST_OUTPUT exposes the same code as compiled
// classes for IDE only (consumed compileOnly) as IDEA can't share a srcDir owned by base.
configurations {
    create(COMMON_GAMETEST_RESOURCES) {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create(GAMETEST_OUTPUT) {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

artifacts {
    sourceSets["gametest"].resources.sourceDirectories.forEach { add(COMMON_GAMETEST_RESOURCES, it) }
    add(GAMETEST_OUTPUT, tasks.named<KotlinCompile>("compileGametestKotlin").map { it.destinationDirectory })
}
