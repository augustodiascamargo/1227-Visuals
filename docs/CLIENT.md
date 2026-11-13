# 1227 Client inside the mod (v1.18.0)

Installation: Minecraft 1.8.9, then Forge 11.15.1.2318, then `1227-<version>.jar` in `mods`. No launcher, account or server of our own is needed.

## Systems
| System | Package | What it does |
|---|---|---|
| Menu System | `client/menu` | Swaps Minecraft screens for 1227's through Forge's `GuiOpenEvent` (no class patching) and restyles the screens it keeps |
| Settings System | `client/settings` | One settings screen with 13 categories; Minecraft options are changed through Minecraft's own setters |
| Performance System | `client/perf` + `PerformanceModule` | Presets, entity and particle limits, background FPS, benchmark |
| Profile System | `client/settings/Profiles` | Named profiles, clipboard sharing, automatic backups |
| Visual, Cosmetics, PvP, HUD and Audio | `module/`, `hud/`, `vfx/`, `music/` | The existing modules |
| Notification System | `core/Notifier` + `client/ui/ClientToasts` | Island in game, toast in menus |
| Resource Manager | `client/ui/Img` | Background, server-icon and screenshot textures, freed when no longer used |
| Design System | `client/ui` | See `DESIGN.md` |

## Screens
| Minecraft screen | 1227 | How |
|---|---|---|
| Title screen | **Main menu**: Singleplayer, Multiplayer, Options, Modules, Cosmetics, Profiles, Mods, Language, Screenshots, classic menu, Quit; account card, what's new | Replaced |
| Pause | **Pause menu**: back, options, LAN, achievements, statistics, mod options, quit + 10 shortcuts | Replaced, with the same quit steps as vanilla |
| Multiplayer | **Servers**: list from `servers.dat`, favourites, history, LAN, real ping / players / version, icon, add / edit / delete / direct / reorder | Replaced; connection goes through Forge (`FMLClientHandler.connectToServer`) |
| Singleplayer | **Worlds**: search, play, create, rename, delete, saves folder | Replaced; loading goes through Forge (`tryLoadExistingWorld`) |
| Options | **Settings** (13 categories) | Replaced; the classic screen is one click away |
| Death screen | **1227 death screen** | Replaced (except in hardcore) |
| Disconnected | **Disconnected** with Reconnect | Replaced |
| Connecting, Loading terrain, Yes/No confirmations, "open link?" | **Restyled** | Minecraft keeps the screen and its buttons; 1227 only draws it |
| Forge loading screen | 1227 colours through `config/splash.properties` (optional, next start) | Forge's own option |
| Video, Sound, Chat, Skin, Language, Resource packs, Controls | Their options live in Settings; the dedicated vanilla screens (language list, pack list, key list) stay vanilla | Kept |

Each replacement can be turned off in **Settings › Interface**.

## Compatibility
- **Exact classes only:** 1227 replaces only the vanilla classes themselves. If another mod already replaced the title screen (or any other one) with its own class, that mod's screen stays. 1227 runs its handler at LOW priority so other mods go first.
- **Dropped items:** 1227 registers its own renderer for them (Item Physics and entity distance). With another Item Physics mod, the last one loaded wins.
- **Particle limiter:** it reads Minecraft's particle lists by type. If that fails, the limiter turns itself off and logs it.

## Limits of Forge 1.8.9 without class patching (and the alternative used)
| Wanted | Limit | Alternative |
|---|---|---|
| Restyle Minecraft's vanilla lists (key bindings, resource packs, language) | Drawn by vanilla `GuiSlot` with no event | Their options are in Settings; the lists open as vanilla |
| World loading progress ("Building terrain") | It is not a `GuiScreen` (LoadingScreenRenderer) | Kept vanilla |
| Update check | No server of our own | The menu shows the installed version and what's new; updates come from GitHub Releases |
| Freelook | Changes the player's view direction; many servers forbid it | Not done |
| Faster chunk rendering | Needs patching Minecraft's renderer | Render distance, graphics and VBO presets, entity and particle limits |
| HUD drawn less often | Needs a framebuffer cache inside Minecraft's HUD | Not done; HUD elements are already immediate and light |
| Difficulty in Settings | Only in the classic Options screen in vanilla | "Classic Minecraft options" button |
