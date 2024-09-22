# imperium

[![Translation status](https://hosted.weblate.org/widget/imperium/svg-badge.svg)](https://hosted.weblate.org/engage/imperium/)

The core of the Chaotic server network, maintained by Xpdustry.

## Translations

If you do not know how to code but still want to help, you can help with translations.
They are hosted on [Weblate](https://hosted.weblate.org/projects/imperium/).

## Building

You will need:
- Java 17
- Docker

For simply compiling the project, run `./gradlew shadowJar`.

If you also want to run the tests, first, make sure docker is running, then run `./gradlew build`.

If you only want to compile one of the subprojects,
just prefix the task name with the subproject name such as `./gradlew :imperium-discord:shadowJar`.

## Testing

Imperium needs a mariadb database, a rabbitmq server and a s3 server to run in production.
But for local testing, it will default to h2, a noop message broker and local file storage.
If you wish to test with the production setup, use the provided docker-compose file.
You will simply have to run `docker-compose up -d` once, then update the [configuration file](imperium-common/src/main/kotlin/com/xpdustry/imperium/common/config/ImperiumConfig.kt) accordingly.

> Some of the docker-compose services have an admin interface, more details in the [docker-compose file](docker-compose.yaml).

### Mindustry

First, create the base configuration file named `config.yaml` in the directory `imperium-mindustry/build/tmp/runMindustryServer/config/mods/imperium`,
with the following content:
```yaml
# Enable this so the server create presets for easier testing, look out for warnings in the console
# DO NOT USE IT IN PRODUCTION
testing: true

mindustry:
  gamemode: SURVIVAL
```

Then for starting a local mindustry server, run `./gradlew imperium-mindustry:runMindustryServer`.
- This will start a server that you can interact with in the console.

To play on this server, you can start a mindustry client by running `./gradlew imperium-mindustry:runMindustryClient`.
- This client is isolated from your local mindustry installation so no need to worry about your data.

### Discord

First, create discord bot and a test server for it (there are plenty of online tutorials for that).
Then create the base configuration file named `config.yaml` in the directory `imperium-discord/build/tmp/runShadow`,
with the following content:
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

Then you can start the discord bot by running `./gradlew imperium-discord:runShadow`.

> If it's the first time you run it, it will automatically download mindustry assets from GitHub,
> this might take less than a minute. (Or more if you have potato internet `;-;`)

## Support

This plugin is open source for transparency and also easing contributions from CN players.
You are free to ask any question about the internals of this project in the issues tab or on discord.

**BUT**, do not expect any support if you are trying to use this project as it is in your own infrastructure.
This project is written with the xpdustry infrastructure and features in mind after all.
