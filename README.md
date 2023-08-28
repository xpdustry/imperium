# imperium

The core of the Chaotic server network, maintained by Xpdustry.

## Contributing

We welcome contributions from developers of all levels of experience, including beginners who are eager to learn.
If you're interested in contributing to Imperium, feel free to explore our codebase,
check the issue tracker for tasks or bugs, and don't hesitate to ask questions on our Discord server.

## Building

You will need:
- Java 17
- Docker

Before doing anything, make sure docker is running.
Then you can compile the project with `./gradlew build` or specific modules with `./gradlew :imperium-<module>:build`.

## Testing

For local testing, you will need a mongo database, a rabbitmq server and a S3 server.
Fortunately for you, a docker-compose file is provided with everything.
You will simply have to run `docker-compose up -d` once.

- To access the web front-end of these services, see the comments in the docker-compose file.
- The mongo express container fails to start with the other containers sometimes. If this happens,
  just restart it with the command `docker-compose restart mongo-express`.

### Mindustry

First, create the base configuration file named `config.yaml` in the directory `imperium-mindustry/build/tmp/runMindustryServer/config/mods/imperium`,
with the following content:
```yaml
server:
  name: "name of the server (must be alphanumeric)"
```

Then for starting a local mindustry server, run `./gradlew imperium-mindustry:runMindustryServer`.
- This will start a server that you can interact with in the console.

To play on this server, you can start a mindustry client by running `./gradlew imperium-mindustry:runMindustryClient`.
- This client is isolated from your local mindustry installation so no need to worry about your data.

### Discord

First, create discord bot and a test server for it (there are plenty of online tutorials for that).
Then create the base configuration file named `config.yaml` in the directory `imperium-discord/build/tmp/runImperiumDiscord`,
with the following content:
```yaml
server:
  token: "your discord bot token"
  categories:
    live-chat: "some channel id"
  channels:
    notifications: "some channel id"
    maps: "some channel id"
  roles:
    administrator: "some role id"
    moderator: "some role id"
    verified: "some role id"
```

Then you can start the discord bot by running `./gradlew imperium-discord:runImperiumDiscord`.

> If it's the first time you run it, it will automatically download mindustry assets from GitHub,
> this might take less than 2 minutes. (Or more if you have potato internet `;-;`)
