package com.ruslan.gradle

// afterEvaluate needed: gametest source set (and its configurations) is created in the
// build script body (Loom configureTests / manual sourceSets.create), which runs after plugins apply.
afterEvaluate {
    dependencies {
        "gametestImplementation"(project(path = BASE_PROJECT, configuration = "gametestOutput"))
    }
}
