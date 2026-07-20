# Imperium

[![Translation status](https://hosted.weblate.org/widget/imperium/svg-badge.svg)](https://hosted.weblate.org/engage/imperium/)

The core of the Chaotic server network, maintained by Xpdustry.

## Translations

If you do not know how to code but still want to help, you can help with translations.
They are hosted on [Weblate](https://hosted.weblate.org/projects/imperium/).

## Building

You will need:

- Java 25

To compile the deployable JARs, run `./gradlew shadowJar`.

To run the tests, run `./gradlew test`. A complete build, including formatting checks and tests, can be run
with `./gradlew build`.

If you only want to compile one of the subprojects,
prefix the task name with the subproject name, such as `./gradlew :imperium-backend:shadowJar`.

## Testing

Imperium defaults to an H2 database and a no-op message broker, so no external services are required for local tests.
Production uses MariaDB for persistent and shared data. To test with MariaDB, start the service from the provided
[Compose file](docker-compose.yaml) with `docker compose up -d`, then set the database and message broker options in
your `config.yaml`. The available options and defaults are defined in
[ImperiumConfig.kt](imperium-common/src/main/kotlin/com/xpdustry/imperium/common/config/ImperiumConfig.kt).

### Mindustry

To start a local Mindustry server, run `./gradlew :imperium-mindustry:runMindustryServer`.

- This will start a server that you can interact with in the console.

To play on this server, start a Mindustry client with `./gradlew :imperium-mindustry:runMindustryDesktop`.

- This client is isolated from your local Mindustry installation, so it will not affect your data.

### Discord

First, create a Discord bot and a test server for it (there are plenty of online tutorials for that).
Then create the base configuration file named `config.yaml` in the directory
`imperium-backend/build/tmp/runApplication` with the following content:

```yaml
discord:
  token: "your discord bot token"
  categories:
    live-chat: "some channel id"
  channels:
    notifications: "some channel id"
    maps: "some channel id"
    reports: "some channel id"
# Optional
# permissions2roles:
#   MANAGE_MAPS: "some role id"
# achievements2roles:
#   ACTIVE: "some role id"
  ranks2roles:
    OWNER: "some role id"
    # Optional roles to add for further testing
    # ADMIN: "some role id"
    # MODERATOR: "some role id"
    # VERIFIED: "some role id"

webserver:
  port: 8080
```

Then start the backend with `./gradlew :imperium-backend:runApplication`.

> If it's the first time you run it, it will automatically download Mindustry assets from GitHub,
> this might take less than a minute. (Or more if you have potato internet `;-;`)

## Support

This plugin is open source for transparency and also easing contributions from CN players.
You are free to ask any question about the internals of this project in the issues tab or on discord.

**BUT**, do not expect any support if you are trying to use this project as it is in your own infrastructure.
This project is written with the xpdustry infrastructure and features in mind after all.
