plugins {
    id("com.github.ben-manes.versions")
}

tasks.register("incrementVersionFile") {
    doLast { file("VERSION.txt").writeText(project.version.toString()) }
}

tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}
