# Trade Cycling

## Links

- [Modrinth](https://modrinth.com/mod/trade-cycling)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/trade-cycling)

---

This mod adds the trade cycling functionality of [Easy Villagers](https://www.curseforge.com/minecraft/mc-mods/easy-villagers).


Cycle the trades of every villager that hasn't been traded before without needing to wait for working hours or replacing the workstation block over and over again.

## Features

- Trade cycling with a button in the trading GUI
- Trade cycling with a keybind (C by default)
- Configurable trade cycling button location

![](https://media.giphy.com/media/AX8FajusxpjFW9wEyP/giphy.gif)
## Fork differences (deanxbox/trade-cycling)

This is a personal fork of [henkelmax/trade-cycling](https://github.com/henkelmax/trade-cycling) for Minecraft 26.3 (Fabric). It is not intended for upstream. Only the `common` and `fabric` modules are built; Forge and NeoForge are disabled in `settings.gradle`.

Added on top of the original:

- **Auto-cycle** (default key V): cycle a villager until the trades match your targets, with a speed setting and a max-attempts limit. Runs on the server (parallel trade simulator) when the server has the mod, with progress and cancel; also controllable via the `/clientautotrade` command.
- **Target config screen**: item targets (traded or trading side) and enchantment targets (minimum level, max emerald cost), match any/all, and a redesigned "Current Targets" list with icons, colour chips, edit and remove.
- **Cheapest price for enchantments**: clicking an enchantment fills the max cost with the lowest price you can get, including the double price for treasure enchantments (Mending is 10), your villager reputation and Hero of the Village. A max cost below that shows a red warning, in the cost box and on the target chip.
- Simulated offers use the villager's real gossip, so discounted prices are correct.

Limits: the minimum price is derived from the 26.3 code and has not been verified in game; item targets get no minimum price or warning; only English text is translated; client and server must run the same version for auto-cycle.
