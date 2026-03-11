<p align="center">
  <img src="docs/logo_transparent.png" alt="CreateSchematicUpload" width="300">
</p>

<h1 align="center">CreateSchematicUpload</h1>

<p align="center">
  A Minecraft mod that automatically uploads Create mod schematics to
  <a href="https://createmod.com">createmod.com</a> for easy sharing.
</p>

<p align="center">
  <a href="https://github.com/uberswe/CreateSchematicUpload/actions/workflows/build.yml"><img src="https://github.com/uberswe/CreateSchematicUpload/actions/workflows/build.yml/badge.svg" alt="Build"></a>
  <a href="https://github.com/uberswe/CreateSchematicUpload/releases/latest"><img src="https://img.shields.io/github/v/release/uberswe/CreateSchematicUpload?include_prereleases&sort=semver&logo=github" alt="GitHub Release"></a>
  <a href="https://modrinth.com/mod/create-schematic-upload"><img src="https://img.shields.io/modrinth/dt/vDsPXWBh?logo=modrinth&label=Modrinth" alt="Modrinth Downloads"></a>
  <a href="https://www.curseforge.com/projects/1483578"><img src="https://img.shields.io/curseforge/dt/1483578?logo=curseforge&label=CurseForge" alt="CurseForge Downloads"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/NeoForge-21.1-orange?logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAOCAYAAAAfSC3RAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAABHSURBVDhPYxgFgwAwQmkMwCRQ0f8Bpv///88AxFgBSCMTlEYGWDUiA2SNyACnRmSAohEZ4NWIDMg2YtMEF0PWBKUBA4PBAACwBSE34GZTQQAAAABJRU5ErkJggg==" alt="NeoForge">
  <a href="https://github.com/uberswe/CreateSchematicUpload/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-All%20Rights%20Reserved-blue" alt="License"></a>
</p>

---

## Features

- **Automatic upload** &mdash; schematics are uploaded to [createmod.com](https://createmod.com) the moment you save them in-game
- **One-click sharing** &mdash; a clickable link appears in chat so you (or anyone) can view the schematic in a browser
- **Claim flow** &mdash; log in on the website to claim ownership, then publish to the community
- **Optional confirmation** &mdash; disable auto-upload in the config to get a confirmation screen before each upload
- **No account needed in-game** &mdash; uploads are anonymous; you claim them on the website when you're ready

## Side

This is a **client-side only** mod. It does not need to be installed on the server.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1+ |
| Create | 6.0+ |

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Install the [Create](https://modrinth.com/mod/create) mod
3. Drop the CreateSchematicUpload `.jar` into your `mods/` folder
4. Launch the game

## Configuration

The config file is located at `config/createschematicupload-client.toml`:

| Option | Default | Description |
|---|---|---|
| `enabled` | `true` | Enable or disable the upload feature entirely |
| `autoUpload` | `true` | Upload automatically on save (if `false`, a confirmation screen is shown) |
| `baseUrl` | `https://createmod.com` | API base URL |

## How It Works

1. Save a schematic using the Create mod's Schematic and Quill
2. The mod uploads the `.nbt` file to createmod.com
3. A clickable link appears in chat
4. Visit the link and log in to **claim** the schematic as yours
5. From there you can publish it to the community

## Building from Source

```bash
git clone https://github.com/uberswe/CreateSchematicUpload.git
cd CreateSchematicUpload
./gradlew build
```

The built jar will be in `build/libs/`.

## Modpacks

You are free to include this mod in any modpack that is distributed for free. Selling this mod or charging money for access to it (or any modpack containing it) is not permitted.

## License

See [LICENSE](LICENSE) for details.
