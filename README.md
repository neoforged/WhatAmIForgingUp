# WhatAmIForgingUp
A tool used to collect statistics on usage of arbitrary classes in Minecraft mods.

# Self-Hosting
> [!CAUTION]
> Before you proceed, it is worth noting that the database size is reasonably big and you will
> need enough space to download all mods for a game version from both platforms. The initial
> index can take hours depending on the amount of mods it finds and its speed is dependent on your network speed
> and bandwidth too.

A prerequisite to self-hosting is a PostgreSQL database. This database will need to have its `max_connections` increased from the default of `100`. `300` is recommended. You should also increase the `shared_buffers` to account for multiple connections.  

You will also need the token of a Discord bot invited in a server with Send Messages permissions,
and a CurseForge API key.

This tool is on GHCR as a Docker image at `ghcr.io/neoforged/whatamiforgingup`.
WhatAmIForgingUp is configured using environment variables:
- `CF_API_KEY`: a CurseForge API key used for searching and indexing mods available on CurseForge
- `DISCORD_TOKEN`: the token of the Discord bot
- `DISCORD_CHANNEL_ID`: the ID of a channel that the bot will send index status updates in
- `POSTGRES_DB_URL`: the URL of the Postgres database. The format is `<ip>:<port>/<database_name>` (example: `localhost:1234/waifu`)
- `POSTGRES_DB_USERNAME`: the username of a user with write (and importantly create schema) permissions to the database
- `POSTGRES_DB_PASSWORD`: the password of the database user
- `KEEP_PLATFORM_CACHES`: boolean defaulting to `true`. If set to `false`, the bot will not keep indexed mod jars in its cache, deleting them after they've been indexed. Note that while this is used for space saving purposes, for the initial index you will still need to be able to store all mod jars (which could amount to several gigabytes) as they will be deleted only after all mods are indexed
- `DEFAULT_INDEX_INTERVAL`: duration defaulting to `1h`. When a version is tracked without a index interval specified, the interval will default to this duration. Example duration: `1d5h3m45s` - 1 day, 5 hours, 3 minutes and 45 seconds. This duration has second precision.

As for memory usage, 16 gigabytes are recommended.

You will have to persist the `/home/waifu` folder.

Once WAIFU is started, in order to start indexing a version you will have to use the `/game-version track` Discord command. After that, in 10 seconds the bot will
start the initial index of all mods for that game version (this can take hours). After that, the bot will look for new mods and files on both CurseForge and Modrinth
every hour, with a 10 minute delay between game versions (if you want to index more than one version).
