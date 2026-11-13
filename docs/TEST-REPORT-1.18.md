# Test report, v1.18.0

## Done here
| Check | Result |
|---|---|
| `javac` on all 150+ files | No errors of our own; the only unresolved symbols are Minecraft and Forge classes, which are not in this environment |
| Minecraft 1.8.9 API names used by the new code | Checked one by one against the decompiled 1.8.9 sources (MCP names): GameSettings options and setters, ServerData / ServerList / OldServerPinger / LAN, SaveFormatComparator / ISaveFormat, GuiYesNo / GuiDisconnected / GuiConnecting fields, vanilla quit and respawn steps, EffectRenderer.fxLayers, RenderEntityItem, AbstractClientPlayer skins. All present |
| Order of private fields read by type | Checked against the sources: GuiDisconnected (reason, message, parent), GuiYesNo (line 1, line 2), GuiConnecting (NetworkManager), parent field of GuiMultiplayer / GuiSelectWorld / GuiOptions |
| Quit, respawn and connect logic | Same steps as vanilla GuiIngameMenu / GuiGameOver / GuiMultiplayer (compared line by line) |

## Not tested here (needs the game)
There is no Minecraft here, so none of these was run:
- starting the game with the mod;
- opening and closing each screen, at every GUI scale and resolution;
- every button;
- server ping and connection;
- loading, creating and deleting worlds;
- benchmark numbers;
- compatibility with other mods.

The compile against the real Forge happens in GitHub Actions.

**No FPS gain is claimed:** use **Settings › Performance › Run benchmark** before and after a preset, in the same spot, and compare the saved results.

## Checklist for the first in-game test
1. Game starts, the 1227 main menu appears, and every button opens its screen. Esc returns from each one.
2. Multiplayer:
   - servers ping (spinner, then ms and players);
   - add, edit, delete and Shift+arrows save to servers.dat;
   - Join and double-click connect;
   - after a kick, "Reconnect" works.
3. Singleplayer: play, create, rename, delete (confirm) and search.
4. Pause menu:
   - every shortcut opens its screen;
   - "Save and quit" / "Disconnect" leave correctly;
   - the blur turns off when the menu closes.
5. Settings:
   - each Minecraft option changes and is kept after a restart;
   - GUI scale re-lays out the screen;
   - mipmap applies when the slider is released;
   - volume sliders change the sound.
6. Performance: each preset changes render distance and graphics; the benchmark saves a result.
7. Profiles: save, load, overwrite, delete, clipboard copy and load, restore a backup.
8. Death screen (survival): Respawn; Title screen needs two clicks.
9. Turn off each "1227 ..." option in Interface: the vanilla screen comes back.
