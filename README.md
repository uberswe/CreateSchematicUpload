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

## Supported Versions

| Branch | Minecraft | Loaders | Create | Java |
|--------|-----------|---------|--------|------|
| `main` / `mc/1.21.1` | 1.21.1 | NeoForge, Fabric | 6.0.10+ | 21 |
| `mc/1.20.1` | 1.20.1 | Forge, NeoForge, Fabric | 6.0.8+ | 17 |
| `mc/1.19.2` | 1.19.2 | Forge, Fabric | 0.5.1+ | 17 |
| `mc/1.18.2` | 1.18.2 | Forge, Fabric | 0.5.1+ | 17 |

## Installation

1. Install the mod loader for your Minecraft version (NeoForge, Forge, or Fabric)
2. Install the [Create](https://modrinth.com/mod/create) mod
3. For Fabric: install [Fabric API](https://modrinth.com/mod/fabric-api)
4. Drop the CreateSchematicUpload `.jar` for your loader into your `mods/` folder
5. Launch the game

## Configuration

**NeoForge / Forge:** `config/createschematicupload-client.toml`
**Fabric:** `config/createschematicupload.json`

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

JARs are produced per loader:
- `neoforge/build/libs/` - NeoForge JAR
- `fabric/build/libs/` - Fabric JAR

## Project Structure

This is a multi-loader project following the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) pattern:

```
common/    - Shared code (config, upload handler, mixin, GUI)
neoforge/  - NeoForge entry point and config
fabric/    - Fabric entry point and config
```

## Modpacks

You are free to include this mod in any modpack that is distributed for free. Selling this mod or charging money for access to it (or any modpack containing it) is not permitted.

## License

See [LICENSE](LICENSE) for details.
