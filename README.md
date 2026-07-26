# Combat Disabled Items - Plugin

A Paper plugin reimplementation of
[Combat Disabled Items](https://github.com/sacdj1/combat-disabled-items):
get hit by another player and your firework rockets (and anything else
you've configured) become unusable for a set duration - stops "hit and land,
then immediately fly away untouched."

This is the plugin version, for server owners who'd rather run a plugin than
manage datapack files. It's event-driven (hooks damage events directly)
rather than tick-polled, so it's cheaper per-tick than the datapack
equivalent, and as of this version it's reached feature parity with the
datapack's core config surface - see [Status](#status) below.

Targets Minecraft "26.2" (Paper API `26.2.build.60-beta`, Java 25). Requires
**Paper** (or a Paper fork like Purpur/Pufferfish) specifically - it uses
Paper-exclusive APIs (Mannequin-based test dummies, `ItemMeta#setItemModel`,
Adventure's native actionbar/title) that plain Spigot/CraftBukkit doesn't
have, so it will not run there.

## Status

Working:
- Combat tagging (hit-based, PvP), with configurable duration, attacker/
  victim tagging, retag behavior, reset-on-death, PvE mode, ranged-hit
  tagging, Creative-mode exemption, and a passive-restore safety net that
  periodically double-checks untagged players have nothing left disguised.
- Item disguising for firework rockets, wind charges, elytra, and worn
  armor, plus an unlimited custom item rule list (by material, by
  enchantment, or both) - independently toggleable hotbar/backpack scanning,
  and optional per-item duration overrides distinct from the main timer.
- Disguise cosmetics: configurable name/color/bold/italic, a two-color
  particle flash on worn armor with an opt-in actual-recolor, a configurable
  disguise model (Paper's `item_model` component) and worn-armor material.
- Dropping, partially splitting a stack, and storing a disguised item in a
  chest/barrel/shulker/dispenser/dropper/hopper/ender chest are all allowed,
  not blocked - tracking follows the item wherever it goes (including a
  stranger taking it, which reveals the real item for them instead of
  leaving a permanently-disguised decoy). Placing one as a block, composting
  it, equipping it on an armor stand, framing it, or moving it into a
  villager trade/furnace/anvil/other "consuming" inventory are still
  blocked outright - see `DisguiseProtectionListener`'s class doc for why.
- Proximity tagging (keeps items disabled while another player - or,
  optionally, a test dummy - stays within range, independent of whether a
  hit landed), with movement-based attacker/victim role inference and team
  exemption.
- Teams (`/scdi team request|confirm|reset`) with a request timeout.
- One-shot kill detection/announcement, with a cooldown option and
  attacker/victim tag exemptions on a one-shot kill.
- Per-player armor/inventory warning preferences (chat + optional sound).
- Test dummy system: spawn cooldown, per-player/server caps, a simulated
  health pool separate from its real health bar (so sustained damage kills
  it like a real player would, not just one big hit), regen, look-at-player,
  item pickup/equip, live health + DPS display, damage number popups,
  cheat-death (with configurable invulnerability window, sound, particle),
  pinned/no-gravity/extinguish options, distance-limited announcements, and
  optionally tagging/proximity-tagging the real player who hits it - lets
  the whole tag flow be tested solo.
- Actionbar countdown with the datapack's exact flash-then-fade wording/
  timing, a matching big tag title, an optional floating countdown that
  follows above a tagged player's head, a below-name scoreboard timer, and
  tab-list team coloring - all with configurable sounds/volumes.
- Full in-game GUI config menu (`/scdi menu`, or just `/scdi`), plus direct
  `/scdi config get|set|list` commands.
- Config import/export as a compact shareable code (`/scdi export` /
  `/scdi import <code>`).

Known minor gaps (not blockers, just not ported yet):
- No `/scdi config reset` to bulk-restore defaults - edit `config.yml`/the
  menu by hand, or delete the file and `/scdi reload`.
- Hopper (or other block-automation) transfers of a disguised item are
  blocked outright rather than tracked through, unlike a chest/barrel move -
  see the class doc on `DisguiseProtectionListener` for why.

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
- `/scdi team request|confirm|reset [player]` - team management.
- `/scdi dummy spawn|remove|removeall` - test dummy management.
- `/scdi settings` - your own per-player warning preferences.
- `/scdi customitem add|remove|list` - manage custom disabled-item rules.
- `/scdi config get|set|list [key] [value]` - direct config access.
- `/scdi export` - get a clickable, copyable config code to share.
- `/scdi import <code>` - apply a shared config code.
- `/scdi debug tag|untag|vanish [player]` (admin) - force a tag state for
  testing, or toggle vanish (hide from other players' tab list/visibility
  while testing - a testing-only utility, not a real feature of the pack).

## Configuration

Everything lives in `config.yml`, editable live and reloadable with
`/scdi reload`, or through the `/scdi menu` GUI. See the file itself for the
current key list.

## License

Free for personal and non-commercial use under
[CC BY-NC-SA 4.0](LICENSE.md) - share it, modify it, build on it, just
credit the original and don't sell it. Running this (or a modified version)
on a server that generates revenue in any way needs a separate commercial
license - same terms and pricing as the
[datapack](https://github.com/sacdj1/combat-disabled-items#commercial-licensing).
See [LICENSE.md](LICENSE.md) for full terms.
