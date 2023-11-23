plugins {
    id("com.github.ben-manes.versions")
}

tasks.register("incrementVersionFile") {
    doLast { file("VERSION.txt").writeText(project.version.toString()) }
}

tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}

// https://github.com/ben-manes/gradle-versions-plugin
fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.dependencyUpdates {
    // https://github.com/ben-manes/gradle-versions-plugin
    rejectVersionIf {
        isNonStable(candidate.version) && !isNonStable(currentVersion)
    }
}
