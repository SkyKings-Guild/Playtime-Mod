# SkyKings Playtime

Completed playtime records are published to `http://localhost:8787/@me/playtime` every
five minutes when an API key has been configured with `/set-playtime-key`. The
request uses the API key as its `Authorization` header and sends a JSON array
containing `start`, `end`, `type`, and `map` for each newly completed record.
Records are retained locally and retried if the request fails.

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
