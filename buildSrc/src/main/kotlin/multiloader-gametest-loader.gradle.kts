package com.ruslan.gradle

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Pull the shared base gametest code in as SOURCES and compile it into this loader's gametest
// source set (same approach as COMMON_JAVA for main code). This keeps the shared bodies on the
// loader mod's classloader; consuming them as compiled classes at runtime would split shared
// `object` singletons across two classloaders (config handler null in tests, etc).
configurations {
    create(COMMON_GAMETEST_JAVA) {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
}

dependencies {
    add(COMMON_GAMETEST_JAVA, project(path = BASE_PROJECT, configuration = COMMON_GAMETEST_JAVA))
}

// afterEvaluate needed: gametest source set (and its configurations + compile task) is created in
// the build script body (Loom configureTests / manual sourceSets.create), which runs after plugins.
afterEvaluate {
    // compileOnly so IDE resolves the shared code as a module dependency (it can't share base's
    // srcDir); kept off the runtime classpath so the source-compiled copy above stays authoritative.
    dependencies {
        "gametestCompileOnly"(project(path = BASE_PROJECT, configuration = GAMETEST_OUTPUT))
    }
    tasks.named<KotlinCompile>("compileGametestKotlin") {
        dependsOn(configurations.getByName(COMMON_GAMETEST_JAVA))
        source(configurations.getByName(COMMON_GAMETEST_JAVA))
    }
}
