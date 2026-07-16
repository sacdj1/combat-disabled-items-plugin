# Combat Disabled Items - Plugin

A Paper/Spigot/CraftBukkit-family plugin reimplementation of
[Combat Disabled Items](https://github.com/sacdj1/combat-disabled-items):
get hit by another player and your firework rockets (and anything else
you've configured) become unusable for a set duration - stops "hit and land,
then immediately fly away untouched."

This is the plugin version, for server owners who'd rather run a plugin than
manage datapack files. It's event-driven (hooks damage events directly)
rather than tick-polled, so it's cheaper per-tick than the datapack
equivalent, but it's **earlier in development** and doesn't yet have full
feature parity - see [Status](#status) below. If you want the most complete,
battle-tested version today, use the
[datapack](https://github.com/sacdj1/combat-disabled-items).

Targets Minecraft "26.2" (Paper API `26.2.build.60-beta`, Java 25).
Deliberately built against classic Bukkit/Spigot APIs (`plugin.yml`, legacy
chat/title/actionbar APIs, BungeeChat click/hover events) rather than
Paper-exclusive Adventure APIs, so it should also run on plain
Spigot/CraftBukkit and other Paper-family forks, not just Paper itself -
only actually tested on Paper so far, though.

## Status

Working:
- Combat tagging (hit-based, PvP), with configurable duration, attacker/
  victim tagging, retag behavior, reset-on-death, PvE mode, ranged-hit
  tagging, Creative-mode exemption.
- Item disguising for firework rockets (+ optional wind charge/elytra),
  reaching the full inventory, not just what's held.
- Actionbar countdown with a red → gold → yellow color fade, big tag title,
  configurable sounds.
- Full in-game GUI config menu (`/scdi menu`, or just `/scdi`), plus direct
  `/scdi config get|set|list` commands.
- Config import/export as a compact shareable code (`/scdi export` /
  `/scdi import <code>`).

Not yet ported from the datapack:
- Test dummy system (planned - spawn cooldown + per-player/total caps).
- One-shot kill detection/announcement.
- Proximity tagging, teams.
- Per-player warning preferences, armor flash/recolor.

## Install

Drop the built jar into your server's `plugins/` folder and restart (or
`/reload` if your server allows it). Requires Java 25 and a Paper-family
server on "26.2" or compatible.

Build from source:
```
./gradlew build
```
Jar lands in `build/libs/`.

## Commands

- `/scdi` or `/scdi menu` - open the config GUI (op only).
- `/scdi reload` - reload config.yml.
- `/scdi status` - your own (or, from console, the server's) current tag state.
- `/scdi config get|set|list [key] [value]` - direct config access.
- `/scdi export` - get a clickable, copyable config code to share.
- `/scdi import <code>` - apply a shared config code.

## Configuration

Everything lives in `config.yml`, editable live and reloadable with
`/scdi reload`, or through the `/scdi menu` GUI. See the file itself for the
current key list - it's short enough not to need a separate reference table
yet (unlike the datapack's ~90 keys).

## License

Free for personal and non-commercial use under
[CC BY-NC-SA 4.0](LICENSE.md) - share it, modify it, build on it, just
credit the original and don't sell it. Running this (or a modified version)
on a server that generates revenue in any way needs a separate commercial
license - same terms and pricing as the
[datapack](https://github.com/sacdj1/combat-disabled-items#commercial-licensing).
See [LICENSE.md](LICENSE.md) for full terms.
