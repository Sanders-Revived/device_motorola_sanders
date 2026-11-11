Device configuration for Moto G5S Plus (sanders)
===========================================

Basic   | Spec Sheet
-------:|:-------------------------
CPU     | Octa-core 2.0 GHz Cortex-A53
Chipset | Qualcomm MSM8953 Snapdragon 625
GPU     | Adreno 506
Memory  | 3/4 GB RAM
Shipped Android Version | 7.0.0
Storage | 32/64 GB
MicroSD | Up to 256 GB
Battery | Li-Ion 3000mAh battery
Display | 1080 x 1920 pixels, 5.5 inches (~401 ppi pixel density)
Camera  | Dual 13 MP, f/2.0, autofocus, dual-LED (dual tone) flash, 2150 x 1440 pixels

![Moto G5S Plus](https://files.catbox.moe/shi6rw.png "Moto G5S Plus")

## DisplayChanger

The device build includes the privileged DisplayChanger app in Settings. Open
`Settings > Display > Screen resolution` and choose 720P (720 x 1280) or 1080P
(1080 x 1920), then tap `Apply`. Each change reads the current logical DPI, including a custom
Developer Options `Smallest width` value, and recalculates DPI so that the same
smallest width is preserved. The restore action clears the Android display-size
override. Applying the native 1080P option clears the Android display-size
override while preserving that effective smallest width.

The selector follows the system theme and reads the framework Monet palette at
runtime. On Lineage builds that expose the dynamic system colors, the accent,
background, status bar, navigation bar, radio controls, and Apply button follow
the user's current wallpaper palette in both light and dark mode. Older builds
fall back to the platform Material colors without requiring the newer
`dynamiccolors` library.
