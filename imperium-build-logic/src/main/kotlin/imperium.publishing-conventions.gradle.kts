plugins {
    id("net.kyori.indra.publishing")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    gpl3OnlyLicense()
    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    github("xpdustry", "imperium") {
        ci(true)
        issues(true)
        scm(true)
    }

    configurePublications {
        pom {
            organization {
                name.set("Xpdustry")
                url.set("https://www.xpdustry.com")
            }

            developers {
                developer {
                    id.set("Phinner")
                    timezone.set("Europe/Brussels")
                }

                developer {
                    id.set("ZetaMap")
                    timezone.set("Europe/Paris")
                }

                developer {
                    id.set("L0615T1C5-216AC-9437")
                }
            }
        }
    }
}
