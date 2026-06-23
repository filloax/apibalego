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

configurations {
    create(COMMON_GAMETEST_RESOURCES) {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("gametestOutput") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

artifacts {
    sourceSets["gametest"].resources.sourceDirectories.forEach { add(COMMON_GAMETEST_RESOURCES, it) }
    add("gametestOutput", tasks.named<KotlinCompile>("compileGametestKotlin").map { it.destinationDirectory })
}
