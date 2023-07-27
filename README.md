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

For local testing, you will need a mongo database and a rabbitmq server,
fortunately for you a docker-compose file is provided. You will simply have to run `docker-compose up -d` once.
- To access the web front-end of these services, see the comments in the docker-compose file.
- The mongo express container fails to start with the other containers sometimes. If this happens, just restart it.

### Mindustry

Then for starting a local mindustry server, run `./gradlew imperium-mindustry:runMindustryServer`.
- This will start a server that you can interact with in the console.
- If you want to change the server files, go to `imperium-mindustry/build/tmp/runMindustryServer`.

To play on this server, you can start a mindustry client by running `./gradlew imperium-mindustry:runMindustryClient`.
- This client is isolated from your local mindustry installation so no need to worry about your data.

### Discord

First, in the directory `imperium-discord/build/tmp/runImperiumDiscord`, create a file named `config.yaml` with the following content:
```yaml
discord:
  token: "Your discord bot token"
```

Then you can start the discord bot by running `./gradlew imperium-discord:runImperiumDiscord`.
