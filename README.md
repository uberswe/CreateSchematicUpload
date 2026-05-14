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
- **Automatic upload** - schematics are uploaded to [createmod.com](https://createmod.com) the moment you save them in-game
- **360° preview rendering** - 120 isometric frames are rendered client-side and uploaded alongside the schematic for an interactive 3D rotation view on the website
- **One-click sharing** - a clickable link appears in chat so you (or anyone) can view the schematic in a browser
- **Claim flow** - log in on the website to claim ownership, then publish to the community
- **Optional confirmation** - disable auto-upload in the config to get a confirmation screen before each upload
- **No account needed in-game** - uploads are anonymous; you claim them on the website when you're ready

### Download
- **Download from createmod.com** - enter a createmod.com URL or short code directly in the Create Schematic Table to download schematics shared by other players
- **Seamless integration** - downloaded schematics are placed into your local schematics folder and ready to use immediately

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
4. Drop the CreateSchematicHelper `.jar` for your loader into your `mods/` folder
5. Launch the game

## Configuration

**NeoForge / Forge:** `config/createschematichelper-client.toml`
**Fabric:** `config/createschematichelper.json`

| Option | Default | Description |
|---|---|---|
| `enabled` | `true` | Enable or disable the upload feature entirely |
| `autoUpload` | `true` | Upload automatically on save (if `false`, a confirmation screen is shown) |
| `baseUrl` | `https://createmod.com` | API base URL |
| `saveFeaturedFrames` | `false` | Save the 4 featured perspective images locally to the schematics folder |
| `saveAllFrames` | `false` | Save all 120 rotation frames locally to the schematics folder |
| `imageFormat` | `jpeg` | Image format for rendered frames: `jpeg` (smaller files, recommended) or `png` (lossless) |

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
3. Paste a createmod.com schematic URL or short code
4. The schematic is downloaded and saved to your local schematics folder

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
common/    - Shared code (config, upload/download handlers, rendering, mixins, GUI)
neoforge/  - NeoForge entry point, config, and platform-specific mixins
fabric/    - Fabric entry point and config
```

## Credits

- [Jamalam](https://github.com/JamCoreModding) - original CreateSchematicDownload mod
- [salem-5/Create-Blueprinted](https://github.com/salem-5/Create-Blueprinted) - isometric rendering approach

## Modpacks

You are free to include this mod in any modpack that is distributed for free. Selling this mod or charging money for access to it (or any modpack containing it) is not permitted.

## License

See [LICENSE](LICENSE) for details.
