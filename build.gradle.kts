import java.time.Clock
import java.time.LocalDateTime

plugins {
    id("foundation.parent-conventions")
}

group = "com.xpdustry"
description = "The core of the Xpdustry network."
version = run {
    // Computes the next CalVer version
    val today = LocalDateTime.now(Clock.systemUTC())
    if (rootProject.file("VERSION.txt").exists().not()) {
        return@run "${today.year}.${today.monthValue}.0"
    }
    val previous = rootProject.file("VERSION.txt").readLines().first().split('.', limit = 3).map(String::toInt)
    if (previous.size != 3) {
        error("Invalid version format: ${previous.joinToString(".")}")
    }
    val build = if (today.year == previous[0] && today.monthValue == previous[1]) previous[2] + 1 else 0
    return@run "${today.year}.${today.monthValue}.$build"
}
