---
name: mindustry-server
description: Test or debug toxopid mod or plugin behavior on a live Mindustry server. Send console or JS commands to inspect or change live state.
---

# Mindustry server

`mdt.java` runs the project's toxopid `runMindustryServer` task in the background. It needs Java 25. From the project root, set:

```bash
M="java .agents/skills/mindustry-server/mdt.java"
```

Run `$M` for the authoritative command and flag syntax.

## Session

1. Run `$M start`. It is ready when the output includes `data dir:` and every prelude reports `loaded`.
   - If task discovery reports `several server tasks found`, select the plugin's server task and save it as `task=` in `<root>/.mdt/config.properties`, then start again.
   - If the plugin initializes after `Server loaded`, save a regex matching its last initialization line as `ready=` in the same file before starting.
2. Drive the server with console commands or JS. Each console command returns the log lines it produced; exit code 1 signals an error in that output. Check the expected output or state before the next command. Only `host` logs `Map loaded.`, so after `gameover` or a map change, wait on the game state with `until` rather than on the log with `wait`. Console commands, JS scripts, waits and polls default to a 30-second timeout.
3. After a code change, run `$M restart`. It rebuilds through Gradle and redeploys the jar. Continue when startup again prints `data dir:` and every prelude reports `loaded`. Plugin configs, databases and saves survive in the data directory.
4. Run `$M stop` when done. `$M status` then prints `not running` and exits 1.

Before writing JS against the server, read [the JS reference](reference.md) for Rhino and Kotlin quirks, game flow, maps, and plugin state recipes. The generic helpers live in [`prelude.js`](prelude.js); put project-specific helpers in `<root>/.mdt/prelude.js`. The project prelude loads after the generic one on every start, so commit it with the project.

The data directory is under `build/tmp/<task>/`. `./gradlew clean` wipes its state.
