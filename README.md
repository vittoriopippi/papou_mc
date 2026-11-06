# Papou HUD

A client-side [Fabric](https://fabricmc.net/) mod for Minecraft that adds a small, toggleable HUD for playing with friends:

- **Friend panels** – one panel per nearby player in the top-left corner, showing their name, the four armor pieces they are wearing (with durability bars), their armor points and their hearts.
- **Compass** – a strip at the top of the screen with cardinal/intercardinal directions (north in red) and the current heading in degrees.
- **XYZ coordinates** – your block position, right below the compass.
- **Settings menu** – show or hide each element and rebind every shortcut in game.

The mod only runs on the client: it works on any server (including vanilla) and nothing needs to be installed server-side. Friend panels show the players your client already knows about, i.e. those within render/loading distance.

## Requirements

| Component     | Version     |
| ------------- | ----------- |
| Minecraft     | 26.2        |
| Fabric Loader | ≥ 0.19.3    |
| Fabric API    | any for 26.2 (built against 0.161.0+26.2) |
| Java          | 25          |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2.
3. Download `papouhud-<version>.jar` from the [Releases](https://github.com/vittoriopippi/papou_mc/releases) page.
4. Put both jars in your `.minecraft/mods` folder and launch the game with the Fabric profile.

## Controls

| Action                 | Default key |
| ---------------------- | ----------- |
| Toggle friend panels   | `H`         |
| Toggle compass         | `N`         |
| Toggle XYZ coordinates | `J`         |
| Open settings menu     | `M`         |

Shortcuts only fire while you are in game with no screen open. Toggling an element shows a short ON/OFF message above the hotbar.

In the settings menu, click a shortcut and press the new key. `Delete` or `Backspace` clears it (no key), `Esc` cancels.

## Configuration

Settings are saved automatically to `config/papou.json` in your game directory:

```json
{
  "panelsEnabled": true,
  "compassEnabled": true,
  "coordsEnabled": true,
  "configVersion": 2,
  "keyPanels": 72,
  "keyCompass": 78,
  "keyCoords": 74,
  "keyMenu": 77
}
```

Keys are GLFW key codes; `-1` means no key. If the file is missing or unreadable, the defaults are used.

The mod is translated into English and Italian.

## Building from source

You need JDK 25. Then:

```bash
./gradlew build
```

The mod jar is written to `build/libs/papouhud-<version>.jar`. To start a development client with the mod loaded:

```bash
./gradlew runClient
```

The mod version is set in [`gradle.properties`](gradle.properties).

## License

Released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain), as declared in `fabric.mod.json`.
