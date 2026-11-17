# Matriz de regressão dos módulos (v1.27.0)

Uma linha por registro em `ModuleManager.java` (103 registros, contados no código: 102 `register(new …)` e `PerformanceModule.INSTANCE`).

Colunas:
- **Compila:** "estático" = passou só na checagem de compilação daqui, sem as classes do Minecraft. A compilação de verdade é a do GitHub Actions.
- **Toggle / Config / Efeito:** só vira "ok" com evidência de teste dentro do Minecraft 1.8.9 (data, ambiente, log). "Toggle ligado" não significa efeito validado.
- **Depende de:** precondições detectadas no código (heurística; conferir ao testar).

Nenhuma linha foi marcada como validada por mim: eu não consigo abrir o jogo. As duas observações do teste ao vivo do dono estão na coluna Evidência.

| # | Módulo | Classe | Categoria | Padrão | Depende de | Compila | Toggle | Config | Efeito | Evidência |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Dynamic Skies | `SkyModule` | SKY | desligado | alvo / combate | estático | não testado | não testado | não testado | visto ligado no teste ao vivo do dono (v1.26.0, singleplayer criativo) |
| 2 | Ambience | `AmbienceModule` | AMBIENCE | desligado | — | estático | não testado | não testado | não testado | — |
| 3 | Ambient Particles | `AmbientModule` | AMBIENCE | desligado | — | estático | não testado | não testado | não testado | — |
| 4 | World | `WorldModule` | AMBIENCE | desligado | — | estático | não testado | não testado | não testado | — |
| 5 | Item Physics | `ItemPhysicsModule` | AMBIENCE | desligado | — | estático | não testado | não testado | não testado | alternado no teste ao vivo; pedra apareceu 1,5–2x maior (corrigido na 1.27.0, falta repetir) |
| 6 | Fog | `FogModule` | AMBIENCE | desligado | — | estático | não testado | não testado | não testado | — |
| 7 | Block Overlay | `BlockOverlayModule` | AMBIENCE | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 8 | Wet World | `WetWorldModule` | AMBIENCE | desligado | — | estático | não testado | não testado | não testado | — |
| 9 | Target Aura | `TargetAuraModule` | AURAS | ligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 10 | Player Aura | `PlayerAuraModule` | AURAS | desligado | — | estático | não testado | não testado | não testado | — |
| 11 | Player Glow | `PlayerGlowModule` | AURAS | desligado | alvo / combate, áudio | estático | não testado | não testado | não testado | — |
| 12 | Aura Hat | `AuraHatModule` | COSMETICS | desligado | — | estático | não testado | não testado | não testado | — |
| 13 | Halo | `HaloModule` | COSMETICS | desligado | — | estático | não testado | não testado | não testado | — |
| 14 | Wings | `WingsModule` | COSMETICS | desligado | — | estático | não testado | não testado | não testado | — |
| 15 | Energy Shell | `EnergyShellModule` | COSMETICS | desligado | — | estático | não testado | não testado | não testado | — |
| 16 | Trails | `TrailsModule` | MOVEMENT | desligado | — | estático | não testado | não testado | não testado | — |
| 17 | Afterimage | `AfterimageModule` | MOVEMENT | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 18 | Jump Effects | `JumpEffectModule` | MOVEMENT | desligado | — | estático | não testado | não testado | não testado | — |
| 19 | Player FX | `PlayerFxModule` | MOVEMENT | desligado | — | estático | não testado | não testado | não testado | — |
| 20 | Hit Effect | `HitEffectModule` | COMBAT | ligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 21 | Hit Color | `HitColorModule` | COMBAT | desligado | — | estático | não testado | não testado | não testado | — |
| 22 | Kill Effect | `KillEffectModule` | COMBAT | ligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 23 | Shockwave | `ShockwaveModule` | COMBAT | ligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 24 | Projectile Trails | `ProjectileTrailsModule` | COMBAT | desligado | — | estático | não testado | não testado | não testado | — |
| 25 | Pearl Helper | `PearlHelperModule` | COMBAT | desligado | — | estático | não testado | não testado | não testado | — |
| 26 | Spacing Circle | `SpacingCircleModule` | COMBAT | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 27 | Aim Helper | `AimHelperModule` | COMBAT | desligado | alvo / combate, áudio | estático | não testado | não testado | não testado | — |
| 28 | Hit Particles | `HitParticlesModule` | COMBAT | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 29 | Damage Numbers | `DamageNumbersModule` | COMBAT | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 30 | Victory & Defeat | `MatchFxModule` | COMBAT | desligado | — | estático | não testado | não testado | não testado | — |
| 31 | Weapon Effects | `WeaponEffectsModule` | WEAPONS | desligado | item na mão, alvo / combate | estático | não testado | não testado | não testado | — |
| 32 | Item Glow | `ItemGlowModule` | WEAPONS | desligado | item na mão | estático | não testado | não testado | não testado | — |
| 33 | Weapon Models | `WeaponModelsModule` | WEAPONS | desligado | item na mão | estático | não testado | não testado | não testado | — |
| 34 | Motion Blur | `MotionBlurModule` | CAMERA | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 35 | View Bobbing | `ViewBobbingModule` | CAMERA | desligado | — | estático | não testado | não testado | não testado | — |
| 36 | Static FOV | `FovModule` | CAMERA | desligado | — | estático | não testado | não testado | não testado | — |
| 37 | Zoom | `ZoomModule` | CAMERA | ligado | — | estático | não testado | não testado | não testado | — |
| 38 | Camera FX | `CameraFxModule` | CAMERA | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 39 | No Hurt Cam | `NoHurtCamModule` | CAMERA | desligado | — | estático | não testado | não testado | não testado | — |
| 40 | Cinematic Mode | `CinematicModule` | CAMERA | desligado | — | estático | não testado | não testado | não testado | — |
| 41 | Low Fire | `LowFireModule` | CAMERA | desligado | — | estático | não testado | não testado | não testado | — |
| 42 | 1.7 Animations | `AnimationsModule` | ANIMATIONS | desligado | — | estático | não testado | não testado | não testado | — |
| 43 | Centered Inventory | `CenteredInventoryModule` | GAMEPLAY | ligado | — | estático | não testado | não testado | não testado | — |
| 44 | 1227 Link | `SocialModule` | COSMETICS | ligado | servidor | estático | não testado | não testado | não testado | — |
| 45 | Display | `DisplayModule` | SETTINGS | ligado | — | estático | não testado | não testado | não testado | — |
| 46 | Competitive Mode | `CompetitiveModule` | PVP | desligado | — | estático | não testado | não testado | não testado | — |
| 47 | Low Latency Mode | `LowLatencyModule` | PVP | desligado | — | estático | não testado | não testado | não testado | — |
| 48 | PvP Visual Optimization | `PvpVisualModule` | PVP | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 49 | Latency Monitor | `LatencyMonitorModule` | PVP | desligado | servidor | estático | não testado | não testado | não testado | — |
| 50 | Input Monitor | `InputMonitorModule` | PVP | desligado | — | estático | não testado | não testado | não testado | — |
| 51 | Toggle Sprint | `ToggleSprintModule` | GAMEPLAY | desligado | — | estático | não testado | não testado | não testado | — |
| 52 | Alerts | `AlertsModule` | GAMEPLAY | desligado | alvo / combate, áudio | estático | não testado | não testado | não testado | — |
| 53 | Smart Armor | `SmartArmorModule` | GAMEPLAY | desligado | — | estático | não testado | não testado | não testado | — |
| 54 | Waypoints | `WaypointsModule` | GAMEPLAY | desligado | servidor | estático | não testado | não testado | não testado | — |
| 55 | Nick Hider | `NickHiderModule` | GAMEPLAY | desligado | — | estático | não testado | não testado | não testado | — |
| 56 | Emotes | `EmotesModule` | ANIMATIONS | ligado | — | estático | não testado | não testado | não testado | — |
| 57 | Hit Sounds | `HitSoundsModule` | HITSOUNDS | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 58 | Music Player | `MusicModule` | MUSIC | ligado | rede externa | estático | não testado | não testado | não testado | — |
| 59 | Floating Lyrics | `FloatingLyricsModule` | MUSIC | desligado | — | estático | não testado | não testado | não testado | — |
| 60 | World Lyrics | `WorldLyricsModule` | MUSIC | desligado | — | estático | não testado | não testado | não testado | — |
| 61 | Music Atmosphere | `MusicAtmosphereModule` | MUSIC | desligado | — | estático | não testado | não testado | não testado | — |
| 62 | Discord Presence | `DiscordPresenceModule` | MUSIC | desligado | servidor, Discord aberto | estático | não testado | não testado | não testado | — |
| 63 | Dynamic Island | `DynamicIslandModule` | HUD | ligado | alvo / combate, servidor | estático | não testado | não testado | não testado | — |
| 64 | Hit Marker | `HitMarkerModule` | HUD | ligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 65 | Crosshair | `CrosshairModule` | HUD | desligado | alvo / combate | estático | não testado | não testado | não testado | — |
| 66 | Nametags | `NametagsModule` | HUD | desligado | servidor | estático | não testado | não testado | não testado | — |
| 67 | Chat | `ChatModule` | HUD | desligado | — | estático | não testado | não testado | não testado | — |
| 68 | Watermark | `WatermarkHud` | HUD | ver código | servidor | estático | não testado | não testado | não testado | — |
| 69 | Held Item | `HeldItemHud` | HUD | ver código | item na mão | estático | não testado | não testado | não testado | — |
| 70 | Info Bar | `InfoHud` | HUD | ver código | servidor | estático | não testado | não testado | não testado | — |
| 71 | Keystrokes | `KeystrokesHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 72 | Target HUD | `TargetHud` | HUD | ver código | alvo / combate | estático | não testado | não testado | não testado | — |
| 73 | Combo Counter | `ComboHud` | HUD | ver código | alvo / combate | estático | não testado | não testado | não testado | — |
| 74 | Armor | `ArmorHud` | HUD | ver código | item na mão | estático | não testado | não testado | não testado | — |
| 75 | Potions | `PotionHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 76 | Coordinates | `CoordsHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 77 | Movement Status | `MovementHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 78 | Clock | `ClockHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 79 | Memory | `SystemHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 80 | Server Info | `ServerHud` | HUD | ver código | servidor | estático | não testado | não testado | não testado | — |
| 81 | Compass | `CompassHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 82 | Session Stats | `SessionHud` | HUD | ver código | alvo / combate | estático | não testado | não testado | não testado | — |
| 83 | Scoreboard | `ScoreboardHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 84 | Hit Delay | `HitDelayHud` | HUD | ver código | alvo / combate | estático | não testado | não testado | não testado | — |
| 85 | W-Tap Indicator | `WTapHud` | HUD | ver código | servidor | estático | não testado | não testado | não testado | — |
| 86 | Reach Display | `ReachHud` | HUD | ver código | alvo / combate | estático | não testado | não testado | não testado | — |
| 87 | Target Info | `AimHelperHud` | HUD | ver código | alvo / combate | estático | não testado | não testado | não testado | — |
| 88 | Now Playing | `MusicHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 89 | Audio Visualizer | `VisualizerHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 90 | Inventory Counter | `InventoryHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 91 | Keybinds | `KeybindsHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 92 | Pack Display | `PackDisplayHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 93 | Saturation | `FoodHud` | HUD | ver código | — | estático | não testado | não testado | não testado | — |
| 94 | PvP Performance HUD | `PvpPerfHud` | HUD | ver código | alvo / combate, servidor | estático | não testado | não testado | não testado | — |
| 95 | Latency Graph | `LatencyGraphHud` | HUD | ver código | servidor | estático | não testado | não testado | não testado | — |
| 96 | Tab List | `TabListModule` | HUD | desligado | servidor | estático | não testado | não testado | não testado | — |
| 97 | Boss Bar | `BossBarModule` | HUD | desligado | — | estático | não testado | não testado | não testado | — |
| 98 | Menu Style | `MenuStyleModule` | SETTINGS | desligado | — | estático | não testado | não testado | não testado | — |
| 99 | Interface | `InterfaceModule` | SETTINGS | ligado | — | estático | não testado | não testado | não testado | — |
| 100 | Client | `ClientModule` | SETTINGS | ligado | áudio | estático | não testado | não testado | não testado | — |
| 101 | Presets | `PresetsModule` | SETTINGS | ligado | — | estático | não testado | não testado | não testado | — |
| 102 | Server Profiles | `ServerProfilesModule` | SETTINGS | desligado | servidor | estático | não testado | não testado | não testado | — |
| 103 | Performance | `PerformanceModule` | SETTINGS | ver código | — | estático | não testado | não testado | não testado | — |

## Como preencher
1. Mundo singleplayer criativo, só o 1227 na pasta mods, GUI Scale Normal, 1920x1080.
2. Para cada linha: ligar, conferir o efeito descrito no menu, mudar uma configuração, desligar, sair para o menu e voltar ao mundo.
3. Anotar a versão, a data e, se houver erro, o trecho de `logs/fml-client-latest.log`.
4. Repetir os módulos marcados "servidor" e "alvo / combate" num servidor.
