# 1227 Design System

Every 1227 client screen uses the same tokens and components: main menu, pause, servers, worlds, settings, profiles and screenshots. The code lives in `client/ui/`: `DS` (tokens), `Ui` (components), `Glyph` (icons), `UiScreen` (base screen) and `Img` (images).

## Colours
| Token | Value | Use |
|---|---|---|
| BG | `#0B0C11` | Screen background |
| SURFACE / SURFACE_2 / SURFACE_3 | `#12141B` / `#181B24` / `#20232E` | Cards, rows, hover |
| LINE / LINE_STRONG | white 9% / 17% | Borders, dividers |
| TEXT / MUTED / FAINT | `#ECEEF4` / `#9AA0AE` / `#646978` | Text, secondary text, captions |
| Accent | Settings › Interface › Accent color (default `#8B5CFF`) | Primary buttons, selection, toggles |
| GOOD / WARN / BAD | `#3DDC97` / `#FFC24A` / `#FF6B6B` | Status (ping, results, delete) |

## Type
The font is Poppins (the mod's bundled TTF, smooth at any GUI scale):

| Size | Factor | Use |
|---|---|---|
| Small | 0.82 | Descriptions, metadata |
| Body | 1.0 | Default |
| Label | 1.1 | Card titles |
| Title | 1.55 | Screen titles |
| Display | 2.6 | Death screen |

Captions are small, upper case and FAINT.

## Shape and space
- **Corner radius:** 4 for fields and pills, 6 for buttons and rows, 10 for cards and windows.
- **Spacing:** 4, 8, 12, 16 and 24 GUI units.
- **Button height:** 22. **Row height:** 24, or 30 when the row has a description.

## Components (`Ui`)
- **Buttons:** Primary (accent), Secondary (surface with border), Ghost (no fill until hover) and Danger (red text, red on hover). They can carry a glyph.
- **Small controls:**
  - Toggle: a 24 x 13 switch;
  - Slider: a 3-unit track with a knob;
  - Knob: drawn on colour bars;
  - Cycle: left click for the next value, right click for the previous;
  - Text field: Ctrl+V pastes, Enter or Esc confirms;
  - Icon button: shows a tooltip;
  - Pill: a status label.
- **Containers and feedback:** scroll areas (wheel, smooth, thin bar), tooltip, toast, card and glass card, spinner.
- **Destructive actions:** they ask for a second click within 3 seconds ("Click again") instead of opening another screen.

## Motion
- Hover and selection animate with an exponential ease (rate 14 per second).
- Screens fade in over 0.22 s, and lists appear with a short stagger.
- Nothing bounces or glows without a reason.

## Rules
- One accent colour, no gradients on controls, borders at most 0.6 units.
- Text from servers (MOTD, disconnect messages) keeps Minecraft's font so its colour codes still show.
- Every 1227 screen keeps a way to reach the original Minecraft screen (eye icon or "Classic ..." button).
