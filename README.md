<p align="center">
  <img src="docs/logo_transparent.png" alt="CreateSchematicHelper" width="300">
</p>

<h1 align="center">CreateSchematicHelper</h1>

<p align="center">
  A Minecraft mod for uploading and downloading <a href="https://modrinth.com/mod/create">Create</a> mod schematics
  via <a href="https://createmod.com">createmod.com</a>.
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

### Upload
- **Automatic upload** &mdash; schematics are uploaded to [createmod.com](https://createmod.com) the moment you save them in-game
- **360&deg; preview rendering** &mdash; 120 isometric frames are rendered client-side and uploaded alongside the schematic for an interactive 3D rotation view on the website
- **One-click sharing** &mdash; a clickable link appears in chat so you (or anyone) can view the schematic in a browser
- **Claim flow** &mdash; log in on the website to claim ownership, then publish to the community
- **Optional confirmation** &mdash; disable auto-upload in the config to get a confirmation screen before each upload
- **No account needed in-game** &mdash; uploads are anonymous; you claim them on the website when you're ready

### Download
- **Download from createmod.com** &mdash; enter a createmod.com URL directly in the Create Schematic Table to download schematics shared by other players
- **Seamless integration** &mdash; downloaded schematics are placed into your local schematics folder and ready to use immediately

## Side

This is a **client-side only** mod. It does not need to be installed on the server.

## Supported Versions

| Branch | Minecraft | Loaders | Create | Java |
|--------|-----------|---------|--------|------|
| `main` / `mc/1.21.1` | 1.21.1 | NeoForge | 6.0.10+ | 21 |

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Install the [Create](https://modrinth.com/mod/create) mod
3. Drop the CreateSchematicHelper `.jar` into your `mods/` folder
4. Launch the game

## Configuration

**NeoForge:** `config/createschematichelper-client.toml`

| Option | Default | Description |
|---|---|---|
| `enabled` | `true` | Enable or disable the upload feature entirely |
| `autoUpload` | `true` | Upload automatically on save (if `false`, a confirmation screen is shown) |
| `baseUrl` | `https://createmod.com` | API base URL |

## How It Works

### Uploading
1. Save a schematic using the Create mod's Schematic and Quill
2. The mod renders 120 isometric preview frames of the schematic
3. The `.nbt` file and preview images are uploaded to createmod.com
4. A clickable link appears in chat
5. Visit the link and log in to **claim** the schematic as yours
6. From there you can publish it to the community

### Downloading
1. Open the Create Schematic Table
2. Toggle to URL download mode
3. Paste a createmod.com schematic URL
4. The schematic is downloaded and saved to your local schematics folder

## Building from Source

```bash
git clone https://github.com/uberswe/CreateSchematicUpload.git
cd CreateSchematicUpload
./gradlew build
```

Output JAR: `neoforge/build/libs/`

## Project Structure

This is a multi-loader project following the [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) pattern:

```
common/    - Shared code (config, upload/download handlers, rendering, mixins, GUI)
neoforge/  - NeoForge entry point, config, and platform-specific mixins
```

## Credits

- [Jamalam](https://github.com/JamCoreModding) &mdash; original CreateSchematicDownload mod
- [salem-5/Create-Blueprinted](https://github.com/salem-5/Create-Blueprinted) &mdash; isometric rendering approach

## Modpacks

You are free to include this mod in any modpack that is distributed for free. Selling this mod or charging money for access to it (or any modpack containing it) is not permitted.

## License

See [LICENSE](LICENSE) for details.
