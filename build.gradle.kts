val modVersion: String by project

plugins {
    alias(libs.plugins.ideaext)
}

tasks.register("modVersion") {
    println("VERSION=$modVersion")
}
