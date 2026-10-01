# SkyKings Playtime

SkyKings Playtime is a Fabric client-side mod for tracking and reporting playtime on Hypixel, specifically for SkyBlock players. It monitors the active server type and map, records sessions in a local SQLite database, and uploads completed playtime records to the SkyKings API when a valid API key is configured.

## Installation

Place the built mod jar into your Minecraft instance's `mods` folder, alongside [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) and [Fabric API](https://modrinth.com/mod/fabric-api).

## Configuration

Use the in-game command:

```text
/skykings-playtime
```

The key is validated against the SkyKings API before being saved. If the key is invalid or missing, the mod will notify you in chat.

## Development

This project uses:

- Java 25
- Fabric Loom
- Fabric API
- Hypixel Mod API
- SQLite JDBC

To build the mod locally:

```bash
./gradlew build
```

The backend API source is available at https://github.com/SkyKings-Guild/Playtime-API

## License

This project is licensed under the MIT license. See `LICENSE.md` for details.
