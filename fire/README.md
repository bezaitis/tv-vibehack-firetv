# Fire TV fork

Fire OS won't let any app replace Amazon's home screen. This fork leaves Amazon's home installed and enabled,
and adds a small accessibility service (`FireHomeService`) to the launcher. Whenever Amazon's home comes to the
front (the Home button, boot, or backing out of an app), the service brings this launcher back over it. The
debloat list switches off Amazon's tracking, ads and the preloads you don't use, and leaves Prime Video, casting,
AirPlay, mirroring, Alexa voice search and updates alone.

Everything is reversible. Packages are disabled, never uninstalled, and the watcher is a single setting.

Written for an Insignia NS-50F501NA26 on Fire OS 8.8.1.0. The scripts work on any Fire TV, but the list is
for this set.

## What you need

A Mac, Linux or Windows computer (on Windows, use Git Bash) on the same Wi-Fi as the TV, with Python 3. For the
launcher you also need either a GitHub account (GitHub builds the APK for you) or Android Studio.

## Steps

**1. On the TV.** Go to Settings > My Fire TV > About, highlight the TV's name and press select 7 times. Then go to
Settings > My Fire TV > Developer Options and turn on **ADB debugging**. Note the IP address under
Settings > My Fire TV > About > Network.

**2. One-time setup on the computer,** run from this folder:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50      # your TV's IP
```

**3. Look before changing anything.** This step is read-only. It saves a backup of the TV's package list in
`fire/` and shows what the list would disable, what's missing on this TV, and every Amazon package it leaves
alone. The first time, the TV asks **Allow USB debugging?** Tick "Always allow" and accept.

```bash
fire/check.sh fire/insignia-ns50f501na26.txt
```

To keep something, delete its line from `fire/insignia-ns50f501na26.txt` before the next step.

**4. Debloat.**

```bash
fire/debloat.sh fire/insignia-ns50f501na26.txt
```

Use the TV for a day. If anything you rely on stopped working, re-enable that one package with
`tools/venv/bin/python tools/tv.py 'pm enable --user 0 <package>'`.

**5. Get the launcher APK.** Pick one:

- **GitHub (no Android SDK):** push this folder to your own GitHub repository. The *Fire TV APK* workflow builds
  the app. Under Actions, open the latest run, download the **tvhome-fire** artifact, unzip it, and put
  `tvhome-fire.apk` in `fire/`.
- **Android Studio installed:** skip this step. `install.sh` builds the app itself.

**6. Install.**

```bash
fire/install.sh
```

Press Home on the remote. Amazon's home flashes for a moment, then this one comes up.

## Themes

Dock apps, hidden apps, columns, background colour and wallpaper are set by a theme (keys in
[`themes/README.md`](../themes/README.md)). On Fire TV, push one with `fire/theme.sh`, not `tools/theme.sh`: Fire OS
8 stops adb from writing to the launcher's storage, so this script hands the files over through the launcher itself.

```bash
fire/theme.sh themes/insignia     # apply
fire/theme.sh --reset             # back to the built-in look
```

`fire/apps.sh` lists every app on the TV by name with the package name a theme needs, for `dock_apps`,
`grid_apps` (the apps under the dock, in your order) and `hidden_apps`.

Reinstalling the launcher from a different GitHub build removes the old copy first, and its theme with it: run
`fire/theme.sh` again afterwards.

## Undo

```bash
fire/restore.sh fire/insignia-ns50f501na26.txt               # re-enable everything, turn the watcher off
fire/restore.sh fire/insignia-ns50f501na26.txt --uninstall   # same, and remove the launcher app
```

Without a computer: turn off **Home screen over Fire TV** under Settings > Accessibility, and Amazon's home is
back. A factory reset (Settings > My Fire TV > Reset to Factory Defaults) also undoes all of it, since nothing is
rooted or changed on the system partition.

## Things to know

- **Fire OS updates** may switch disabled packages back on. Run `fire/debloat.sh` again after one. If an update
  turns off the watcher, run `fire/install.sh` again.
- While the watcher is on, Amazon's own home screen, including its app library and profile switcher, can't be
  reached. Turn the watcher off in Settings > Accessibility to use it, and back on with `fire/install.sh`.
- **Inputs** (HDMI and antenna) in the new launcher use Android's standard TV input system, which Fire TV sets
  support. This hasn't been tried on this Insignia yet. If an input won't open from there, the Live TV app
  still switches inputs.
- **Untested on this TV** until you run it. `check.sh` shows what would change before anything does, and
  `debloat.sh` reports each package as disabled, refused by Fire OS (protected), or not on this TV.
