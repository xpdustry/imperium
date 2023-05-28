tasks.register("incrementVersionFile") {
    doLast { file("VERSION.txt").writeText(project.getCalverVersion()) }
}
