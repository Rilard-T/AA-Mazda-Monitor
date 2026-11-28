[Русский](README.md) | [English](README.en.md)

# AA Mazda Monitor and AA Dash Nav

Two **Android Auto** apps that show car data on the head unit screen. Both talk directly to an **ELM327** adapter over Bluetooth, BLE or Wi-Fi — no Torque Pro. Parameters are tuned for the **Mazda SkyActiv-D** diesel (verified on a Mazda 3 BP 2020, 1.8): DPF, regenerations, temperatures, boost, fuel consumption.

[**AA Mazda Monitor**](#aa-mazda-monitor-master) is the mature app with a projected gauge dashboard. [**AA Dash Nav**](#aa-dash-nav-nav) is the newer app built on Car App Library templates, with a layout editor and graphs. They are two independent products: both can live on the same phone, and their settings and logs do not overlap.

[Download](#downloads) · [Which one](#which-one) · [Comparison](#the-two-apps-side-by-side) · [Installation](#installation) · [Adapter connection](#connecting-the-adapter) · [Repository map](#repository-map) · [All releases](https://github.com/Rilard-T/AA-Mazda-Monitor/releases) · [Issues](https://github.com/Rilard-T/AA-Mazda-Monitor/issues)

---

## Downloads

| App | APK | Release |
|---|---|---|
| **AA Mazda Monitor** (`master` branch) | [app-release.apk](https://github.com/Rilard-T/AA-Mazda-Monitor/releases/latest/download/app-release.apk) | [latest release](https://github.com/Rilard-T/AA-Mazda-Monitor/releases/latest) |
| **AA Dash Nav** (`nav` branch) | [app-nav-release.apk](https://github.com/Rilard-T/AA-Mazda-Monitor/releases/download/nav-latest/app-nav-release.apk) | [latest channel build](https://github.com/Rilard-T/AA-Mazda-Monitor/releases/tag/nav-latest) |

These links are permanent: they always point to the latest build of their channel, so they never need updating on release. All builds: [Releases](https://github.com/Rilard-T/AA-Mazda-Monitor/releases). Both are signed with the same key, so a new build installs over the previous one without uninstalling the app or losing settings.

---

## Which one

| What you want | App |
|---|---|
| Gauges on screen, familiar analog dials, the widest set of ready-made sensors and info tiles around the gauges | **AA Mazda Monitor** |
| Your own layout for wide and narrow screens, separate graph screens, tabs in the Android Auto UI | **AA Dash Nav** |
| Both | Install both: they do not conflict |

Both apps are free, open source and update from this repository.

---

## What it looks like

### AA Mazda Monitor (`master`)

![Gauges and navigation on the head unit](docs/screenshots/car-navigation.jpg)
*On the car screen: navigation on the left, gauges in the centre (RPM, speed, temperature), info tiles around them and the now-playing track line.*

![Gauges on the head unit](docs/screenshots/car-parked.jpg)
*The same screen while parked.*

![Android Auto screen capture](docs/screenshots/android-auto-screenshot.jpg)
*Another gauge style and the moment of connecting to the engine unit ("Connecting to ECU").*

### AA Dash Nav (`nav`)

![AA Dash Nav gauges on the head unit](docs/screenshots/nav-gauges.jpg)
*Gauges on the car screen: RPM, speed and coolant temperature; below — DPF %, oil temperature, transmission temperature and distance since the last regeneration.*

![Gauges with the media player](docs/screenshots/nav-gauges-media.jpg)
*The same screen with the player: "Gauges", "Graph 1" and "Exit" tabs and the now-playing card.*

![Graph on the head unit](docs/screenshots/nav-graph.jpg)
*A separate graph screen: RPM, speed and DPF % for the last 2 minutes.*

---

## The two apps side by side

These are not an "old" and a "new" version of one product, but two different approaches to the same screen.

**AA Mazda Monitor** draws the dashboard itself and projects it into Android Auto: the app controls every pixel, so the dials look like real analog gauges, with "View 1–4" info tiles and the track line around them. Element positions are fixed; only their sizes change.

**AA Dash Nav** uses the stock **Car App Library** templates: Android Auto draws the frame, tabs and cards, and the app fills them. The result looks more system-native and depends more on the Android Auto version, but it gains tab screens and a layout editor where gauges, values and media are placed by hand for wide and narrow screen shapes.

| | **AA Mazda Monitor** (`master`) | **AA Dash Nav** (`nav`) |
|---|---|---|
| Android Auto screen | projection of its own gauge dashboard | Car App Library templates: gauges, graphs, several tabs |
| Layout | fixed, with element size settings | editor: forms, proportions, elements, graphs, tools |
| Graphs | on the main screen | separate tab screens |
| Media | line under the centre gauge | now-playing card |
| Sensors and tiles | more ready-made "View 1–4" tiles, experimental soot tools | gauges, values and media in the layout, sensor per element |
| Package and icon | `com.aamazda.monitor`, dark dial | `com.aadash.nav`, blue dial with a **β** mark |
| Release tag and file | `0.9.0`, `app-release.apk` | `nav-<version>-<build number>`, `app-nav-release.apk` |
| Updates | latest regular release | latest `nav` channel release, build number is compared too |

Each channel has its own CI and releases; the `nav` branch is not merged into `master`.

---

## AA Mazda Monitor (`master`)

The workhorse: a projected gauge dashboard with parameter tiles around the gauges and the now-playing track under the centre gauge. Mazda parameters (`22` mode) are verified on a Mazda 3 2020 diesel 1.8: soot accumulation and generation, regeneration counter, distance since regeneration, transmission oil temperature.

**Features**

- Gauges and "View 1–4" info tiles on the Android Auto screen; gauge, value and label sizes 50–150%, hide the top row.
- **"OBD connection"** screen: link status, adapter selection, start/stop, polling rate, soot threshold, sensor check without a car.
- **Custom sensors and PID import** — add a sensor by hand or import a Torque Pro CSV; imported sensors are available on the gauges right away.
- The regeneration log is kept automatically; "DPF, %" is computed from the soot threshold (6.5 g/L by default).
- Experimental tools: record soot at regeneration start to find the real threshold for your engine.
- Now-playing track line (Settings → Media), notification access required.
- Problem report and full log export to `.txt` from the app.
- Russian and English UI, light and dark themes, wallpaper colours on Android 12+.
- Over-the-air updates from the regular releases of this repository.

Detailed description, a connection-error table and FAQ are in the [AA Mazda Monitor description](https://github.com/Rilard-T/AA-Mazda-Monitor/blob/master/docs/AA_MAZDA_MONITOR.en.md). Version history is in [`CHANGELOG.md`](CHANGELOG.md).

---

## AA Dash Nav (`nav`)

The app for those who want their own layout. The screen consists of tabs: gauges and graphs. Position, size and look of elements are configured in the editor; wide and narrow screen areas can have different layouts.

**Features**

- **Gauges on the Android Auto screen** — round dials and separate values, layouts for wide and narrow screen shapes.
- **Layout editor** — touch preview: drag, resize, align, grid and snapping, widget borders, add gauges, values and media.
- **Per-gauge setup** — sensor, scale maximum, red zone, dial size, labels and ticks; sensors can be copied to all forms with one command.
- **Graphs** — separate tab screens with selected sensors and a graph name.
- **Gauge themes** — 24 built-in themes and a flat look; font, ticks and a debug grid are switched in the editor.
- **Car profiles** — several profiles with their own settings, layout and log; the active profile is switched in settings.
- **App appearance** — light, dark or system theme, an accent colour picked from a palette (any hue, 5 quick presets), gauge and tile sizes, hiding the top row.
- **Media** — artist and track title on the Android Auto screen (notification access required).
- **DPF regeneration** — a status 1/2 banner in the header or first row, adjustable shift, size and colours, auto-hide after it ends; a regeneration log with export and import.
- **Over-the-air updates** — from the `nav` channel of this repository; version and build number are compared.
- **Custom sensors** — add a PID by hand or import a Torque Pro CSV; a new sensor is available in the layout and graphs right away.
- **Diagnostics** — a check of which PIDs answer, the full app log with `.txt` export and a problem report.

**Screen and editor.** The editor opens from settings: **"AA NAV dashboards" → "Gauge layout"**.

- **Form** — separate layouts for the wide and narrow screen area.
- **Proportions** — preview for the car screen, phone landscape or phone portrait.
- **Look** — gauge theme, font, ticks, debug grid, regeneration banner (enable, position, shift, size, background, text, border).
- **Elements** — add a gauge, value or media, align it, set widget borders.
- **Graphs** — create and configure graph screens and choose their sensors.
- **Tools** — grid, snapping, copying sensors to all forms, resetting the layout.

Long-press an element to open its menu, drag to move it, use the corner handle to resize. Do not forget to tap **"Save"**.

**Regeneration.** The regeneration status from the engine unit (`22 0380`) is shown as a banner: `0` — no regeneration, `1` or `2` — a regeneration is running. The banner can be placed in the header or the first row, and its shift, size and colours can be changed; it hides itself after the regeneration ends. The regeneration log is kept automatically; export and import are in settings, in the regeneration log section.

**Updates.** The app checks the releases of the `nav` channel; tags look like `nav-<version>-<build number>`, and the version and build number are compared. Manual check: **"About" → "Check for updates"**.

A short feature summary for release notes is in [`docs/NAV_FEATURES.md`](docs/NAV_FEATURES.md), the roadmap is in [`docs/PLAN_NAV.md`](docs/PLAN_NAV.md).

---

## Installation

Separate installer tools (like AAEnabler) are no longer needed — the app installs itself with the required source.

**First-time installation (two steps):**

1. Download the APK from [Releases](https://github.com/Rilard-T/AA-Mazda-Monitor/releases): `app-nav-release.apk` for AA Dash Nav or `app-release.apk` for AA Mazda Monitor, open the file and install it normally.
2. **You must reinstall the app from inside itself:** open the app → **"About" → "Install APK" → "Install from Play source"** (or **"Choose file from storage"** if the APK is already on your phone) and confirm installing over the existing app. This sets the `com.android.vending` installer source, without which Android Auto will not list the app.
3. Enable developer mode in Android Auto (tap the version number ten times) and allow **"Unknown sources"**.

**Future updates:**

- **"About" → "Install APK" → "Install from Play source"** (or **"Choose file from storage"**).
- Settings, the regeneration log and the layout are preserved; no need to uninstall the app.

---

## Connecting the adapter

1. Plug the ELM327 into the diagnostic port and turn on the ignition or start the engine.
2. Pair the adapter in the phone's Bluetooth settings (usually `OBDII`, PIN `1234` or `0000`), or connect the phone to the adapter's Wi-Fi network.
3. In the app open **"OBD connection"**, choose the connection type and adapter, then tap **"Start / connect"**.
4. Wait for the "Connected" status and sensor values.

**Bluetooth Classic**, **BLE** and **Wi-Fi** are supported. The adapter holds one connection — close other OBD apps. To show the now-playing track, grant notification access (Settings → "Notification access (media)").

For Wi-Fi: the phone joins the adapter's network (usually `192.168.0.10`, port `35000`) and mobile data stays available. On Samsung, disable auto-switching to mobile data. With wireless Android Auto the phone's Wi-Fi is busy with the head unit, so only Bluetooth works there.

---

## Custom sensors and Torque Pro PID import

In both apps: **"OBD connection" → "Custom PIDs"**. A sensor can be added by hand — name, mode and PID in hex, formula, units, minimum and maximum — or loaded from a ready-made Torque Pro list.

- **CSV import.** The **"Import Torque CSV…"** button accepts a Torque Pro export (`Manage custom PIDs → Export`) with the columns `Name,ShortName,ModeAndPID,Equation,Min,Max,Units,Header`. The separator is a comma or a semicolon and is detected automatically; quotes and the header row are handled as in a normal CSV, and lines starting with `#` are skipped.
- **Torque formulas.** `A`…`P` are the response bytes; `SIGNED(x)`, `BIT(x,n)`, `INT(x)`, `ABS`, `MIN`, `MAX`, `ROUND`, `SQRT` work. Header `7E0` is the engine unit, `7E1` the transmission.
- **No duplicates.** Importing again updates sensors with the same "mode + PID" instead of adding copies.
- **Where they appear.** An imported sensor is immediately visible in "Sensor check", in the gauge PID picker and in the AA Dash Nav layout editor. Each sensor also has a one-shot "does the unit answer" test and a live value test.
- **Response bytes only.** Formulas that reference other sensors (for example `[0C]` in Torque notation) are **not supported yet**: a formula must be computed from the response bytes `A`…`P` and numeric constants. Such rows are skipped during import.

---

## Repository map

| Where | What is there |
|---|---|
| `master` | **AA Mazda Monitor**, the stable branch and the repository main page |
| `nav` | **AA Dash Nav**, a separate product and the `nav-…` update channel |
| [`app/`](app) | app sources; the `stable` flavor builds AA Mazda Monitor, `nav` builds AA Dash Nav |
| [`app/src/nav`](app/src/nav) | resources and code of the NAV variant, [`app/src/stable`](app/src/stable) of AA Mazda Monitor |
| [`lib/speedviewlib`](lib/speedviewlib) | gauge library (SpeedView fork) |
| [`docs/screenshots`](docs/screenshots) | screen photos used in this README |
| [`docs/NAV_FEATURES.md`](docs/NAV_FEATURES.md) | short feature list of AA Dash Nav and its differences from AA Mazda Monitor |
| [`docs/PLAN_NAV.md`](docs/PLAN_NAV.md) | AA Dash Nav roadmap |
| [`docs/PID_PROFILES.md`](docs/PID_PROFILES.md) | how to add a sensor and prepare a PID profile for another SkyActiv-D model |
| [`docs/RELEASING.md`](docs/RELEASING.md) | release rules and the signing key (owner document) |
| [`docs/release-notes-*.md`](docs) | AA Mazda Monitor release notes |
| [`CHANGELOG.md`](CHANGELOG.md) | AA Mazda Monitor version history |
| [`version.properties`](version.properties) | current version; bumped on release |
| [`.github/workflows`](.github/workflows) | CI: `nav.yml` — the `nav` channel, `build.yml`, `ci.yml`, `pr-checks.yml` — build and checks |
| [Releases](https://github.com/Rilard-T/AA-Mazda-Monitor/releases) | ready-to-install APKs for both apps |
| [Issues](https://github.com/Rilard-T/AA-Mazda-Monitor/issues) | bugs and suggestions |

Where to start: download an APK from the table above, install it like a regular app, connect the adapter. After that, update from the app: **"About" → "Install APK"**. If something does not work, check the limitations below and the [AA Mazda Monitor description](https://github.com/Rilard-T/AA-Mazda-Monitor/blob/master/docs/AA_MAZDA_MONITOR.en.md), which has a connection-message table and an FAQ.

---

## Limitations

- The parameters were verified on a **Mazda 3 (BP) 2020, SkyActiv-D 1.8**. Other SkyActiv-D models may have different PID sets, and some PIDs may not answer.
- Reading and clearing trouble codes (DTC) is not supported.
- BLE and Wi-Fi have not been tested with every adapter model.
- The default 6.5 g/L soot threshold is an average; the unit may also start a regeneration by distance, so treat "DPF, %" as an estimate.
- Custom PIDs are computed from the response bytes only: formulas that reference other sensors are not supported yet.
- The media player needs notification access and system media session support in the player.
- The interface was checked on a real car head unit and a Samsung phone; other head units and Android Auto versions may lay out or size things differently.
- AA Mazda Monitor projects its dashboard through an unofficial Android Auto interface: Android Auto updates may break it.
- AA Dash Nav builds its screen on Car App Library templates, so its look depends on the Android Auto version and the head unit.

---

## Feedback

Bugs and suggestions: [Issues](https://github.com/Rilard-T/AA-Mazda-Monitor/issues) or **"⋮ → Report a problem"** in the app (AA Mazda Monitor) and the problem report in settings (AA Dash Nav). Include the app version, phone and adapter model, connection type and the log.

---

## License and credits

- The project is distributed under **GPL-3.0** ([`LICENSE.md`](LICENSE.md)). The source code is available in this repository, and the changes are published under the same license.
- Based on [AA Torque](https://github.com/agronick/aa-torque) by Kyle Agronick; the Chillout app and the Martoreto SDK/demo. Acknowledgements and translators are listed in the "About" window.
- Icons (CC BY 3.0, Noun Project): *Horse* — Peleg Red; *engine cylinder* — Thuy Nguyen; *transmission* — Gustyne Pissesa Ardhaneswari.
- Modification and maintenance: **Rilard-T**, 2026.
- Part of the code and documentation was prepared with the help of an AI assistant; building, verification and testing were done by the author.
