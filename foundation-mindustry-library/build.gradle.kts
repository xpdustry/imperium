import fr.xpdustry.toxopid.dsl.anukenJitpack
import fr.xpdustry.toxopid.dsl.mindustryDependencies

plugins {
    id("foundation.java-conventions")
    id("foundation.publishing-conventions")
    id("fr.xpdustry.toxopid")
}

toxopid {
    compileVersion.set(libs.versions.mindustry.map { "v$it" })
    platforms.set(setOf(fr.xpdustry.toxopid.spec.ModPlatform.HEADLESS))
    useMindustryMirror.set(true)
}

repositories {
    anukenJitpack()
    xpdustryReleases()
}

dependencies {
    mindustryDependencies()
    compileOnly(libs.distributor.api)
}
