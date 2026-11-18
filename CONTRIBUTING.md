# Contributing to Stillframe

Stillframe is a native Android TV and Google TV launcher. The home screen shows
Continue Watching for the app highlighted in the dock. Fast remote navigation is
a priority. Keep D-pad actions immediate while artwork, wallpaper, and network
updates run in the background.

## Build and run

Use JDK 17 or newer, Android SDK Platform 35, Build Tools 35.0.0, Android
Platform Tools for `adb`, and the `zip` command. Stillframe supports Android
TV API 28 and later. Run these commands from a checkout that includes the
application source and build scripts.

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
./test.sh
./build.sh
adb devices -l
adb -s DEVICE_SERIAL install -r build/stillframe-debug.apk
adb -s DEVICE_SERIAL shell am start -n dev.stillframe/.MainActivity
```

Replace `DEVICE_SERIAL` with your test TV or emulator's serial. The APK uses
a local development signing key. Installing and opening it with ADB leaves
your default launcher selection alone. See [Build and run](docs/build.md) for
wallpaper folder setup, permissions, and performance measurements.

## Use Stillframe on a TV

- **Home and All Apps.** Use the D-pad to highlight a pinned app, then press
  Up to move into its row. Select a poster to open the title in its app.
  The dock's **All Apps** button opens the searchable app drawer.
- **Continue Watching.** Choose **Enable Watch Next** if prompted and grant
  TV listings access. Available titles, metadata, and playback links depend
  on what each app shares on that device. HIDIVE shows a
  **[Popular on HIDIVE](docs/hidive-picks.md)** row when it has no readable
  Watch Next titles.
- **Edit Dock.** Use the plus tile to add an app. Press Select on a pin,
  use Left or Right to reorder it, and press Select again to finish.
  Hold Select on a pin to bring up the removal button. **Weather location**, **Slideshow folder**, and
  **App icons** are also in Edit Dock.
- **Portrait posters.** Use **Edit Dock → Portrait posters** to configure
  optional TMDB artwork with your own credential. See
  [Portrait posters](docs/portrait-posters.md) for setup.
- **Use the remote's Home button.** Start with **Edit Dock → Set as Home →
  Default Home app**. If the button still opens Google TV, the optional
  **Home override** uses an Accessibility service. See the
  [Home button instructions](docs/build.md#home-button) for setup and rollback.

## Make and verify a change

Application code is in `src/main/java/dev/stillframe/`, Android resources are
in `src/main/res/`, and host-side tests are in `tests/`. Put behavior and
setup documentation in the relevant file under `docs/`. For a new topic,
explain what the feature does, why it exists, how to use or test it, and
which devices, permissions, or services it assumes.

1. Add or update a test for the behavior you're changing. Run it before the
   fix and confirm it fails for the intended reason. Use a runnable contract
   check for shell changes when applicable.
2. Keep the change focused. Reuse existing helpers and entry points. Keep
   files under 300 lines where practical. Split a file that's already over
   300 before adding more. Reserve the 500-line ceiling for cases where
   splitting would make the code harder to follow. Use comments to explain decisions
   that the code alone can't convey.
3. Run `./test.sh` and `./build.sh` again after the final code edit. For a UI
   change, use the remote or equivalent D-pad input on Android TV. Check the
   path you changed, including opening, selecting, backing out, and focus
   return where relevant. Record the device and Android version you tested.
4. For performance changes, compare a cold and a repeat interaction under
   similar conditions. Test steady wallpaper and a slideshow transition when
   relevant. Keep network and image decode work off the main thread. See
   [Development timing](docs/build.md#development-timing) for measuring
   key dispatch to frame submission.
5. Review the whole diff before opening a pull request. Include a short
   summary, how you tested it, and any device-specific behavior. Update
   the relevant docs and comments with the change.

## Report a bug

Include the Stillframe version, device model, Android or Google TV version,
and the steps to reproduce. Describe what you expected and what happened.
For Home-button bugs, include whether Stillframe is the default Home app and
whether Home override is enabled. For Watch Next bugs, name the affected
streaming service and say whether TV listings access is granted. Use a
fictional title when the exact title is unnecessary.

## Keep public contributions free of private data

GitHub issues, pull requests, commits, reviews, and attachments are public.
Use fictional test fixtures and generic paths. Before sharing, check all
logs, screenshots, and files for credentials, pairing codes, device IDs,
private addresses, precise locations, account or profile details, and
viewing history. Share only the relevant, sanitized excerpts from device
output. Check the rights to any assets you add and preserve their notices.

Keep credentials in ignored local files or the TV's app settings. Check both
Git author and committer metadata before pushing. Use a public display name
and your GitHub no-reply email address. If private data is in an outgoing
commit, remove it from the outgoing history before pushing.
