# JS reference

Use these recipes when scripting a live server through `js` or `jsfile`. Each shell example uses the `$M` command defined in [the skill](SKILL.md). The console reads one line per command, so use `jsfile` for multi-line scripts. Errors in a script point to `file.js#line`.

The console runs Rhino on the server's main thread. `Vars`, `Groups`, `Events` and every Mindustry class are in scope. Its scope persists until the server stops, so variables and functions carry over between commands. The generic prelude defines reflection, class-loading, mod and player helpers in [`prelude.js`](prelude.js).

## Rhino and Kotlin quirks

- **Declarations:** Use `var` at the top level so reloading a file can declare it again. Use `let` inside functions. In loops, `const` retains its first value.
- **Numbers:** Java `long` values become JS doubles. Wrap IDs and timestamps in `String(x)` before comparing or printing.
- **Callbacks:** Pass a JS function where Java expects a functional interface (`Cons`, `Boolf`, `Runnable`): `Vars.maps.all().each(function (m) { ... })`.
- **Plugin classes:** Load mod and plugin classes with `cls("com.example.Foo")`; `Packages.*` only sees the game's class loader.
- **Kotlin:**
  - Read properties as JS properties: `app.instances` calls `getInstances()`.
  - An `internal` member's JVM name has the module appended: `onGameOver$imperium_mindustry`. List the real names with `cls(...).getDeclaredMethods()`.
  - Top-level functions live on the file's `FooKt` class.
  - Load `object` declarations with `kobject("com.example.Registry")`.
- **Main thread:** Game state is safe to touch in console scripts. A blocking call freezes the server until `stop` force-kills it.

## Game flow

```bash
$M cmd "config roundExtraTime 1"             # 1s between rounds instead of the default
$M cmd "host"                                # random map; or: host <map name> [survival|attack|pvp|sandbox]
$M cmd "gameover"                            # ends the round like a core loss, the next map follows
$M js 'var before = Vars.state.map.plainName()'
$M until 'Vars.state.isPlaying() && Vars.state.map.plainName() != before'
```

The next map is chosen by `Vars.maps.getNextMap(...)` at game over. Force one the way a vote would:

```bash
$M js 'Vars.maps.setNextMapOverride(Vars.maps.all().find(function (m) { return m.plainName() == "Glacier"; }))'
```

## Inspecting a plugin

```bash
$M js 'mods()'                               # name, version and state of every loaded plugin
$M js 'fields(mod("myplugin"))'              # every field of the plugin's main class
$M js 'field(field(mod("myplugin"), "manager"), "cache")'
$M js 'call(mod("myplugin"), "reload")'      # private methods too
$M js 'String(field(Vars.maps, "shuffler"))' # works on game internals as well
```

Plugins may store map metadata in `Vars.state.map.tags`, an `ObjectMap<String, String>`.

## Databases

For a plugin with a JDBC pool, reach its `DataSource` through `field(...)` and run plain JDBC in the project prelude. Imperium's `.mdt/prelude.js` has a working `sql(query)` helper built this way.

Quote identifiers the way the plugin's ORM created them. Exposed on H2 keeps reserved words like `"name"` and `"start"` lowercase and case-sensitive.
