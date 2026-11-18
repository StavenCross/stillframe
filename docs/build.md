# Build and run Stillframe

Stillframe is a native Android TV app using Android platform views. The build
uses JDK 17 or later, Android SDK Platform 35, Build Tools 35.0.0, and the
`zip` command. It does not download application dependencies. The app targets
Android TV API 28 and later. The build uses a local development signing key.

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
./test.sh
./build.sh
adb -s DEVICE_SERIAL install -r build/stillframe-debug.apk
adb -s DEVICE_SERIAL shell am start -n dev.stillframe/.MainActivity
```

To read Watch Next, select the permission action above the dock and grant TV
listings access. The launcher groups the device's readable rows by publishing
app and uses the metadata they contain. Link handling and availability vary by
provider, device, and profile. The permission does not grant access to Google's
combined Continue Watching feed.

See [Portrait posters](portrait-posters.md) for optional TMDB artwork and
token setup.

## Home button

Installation leaves the system Home role unchanged. Open **Edit Dock → Set
as Home → Default Home app** to choose Stillframe in Android settings.

If the Home button still opens stock Google TV, turn off any other launcher
override. Open **Edit Dock → Set as Home → Home override → Open accessibility**
and enable **Stillframe Home override** under **Services**. The service
redirects the Google TV Home activity to Stillframe. It reacts to the
Android window event for that activity and starts Stillframe with the HOME
category. The service has no capability to retrieve window contents or
intercept keys. It does not send event data off device.
This fallback targets Google TV firmware with the stock LauncherX Home
activity. A brief stock screen may appear before Stillframe.

To undo the fallback, disable **Stillframe Home override** in the same
Accessibility settings. Use **Default Home app** to undo the role selection.

## Wallpapers

The app includes ten original dark images under `src/main/assets/wallpaper/`.
Edit Dock can switch to an approved system folder, an indexed shared image
folder, or the app's own Wallpapers folder. Allow image access only if choosing
the shared image option. The system document picker is offered where available.

For ADB setup, select **Stillframe Wallpapers folder** in Edit Dock first.
This lets the app create a folder owned by its Android UID. Then copy images
into it and reopen Stillframe:

```sh
adb -s DEVICE_SERIAL push path/to/wallpapers/. /sdcard/Android/data/dev.stillframe/files/Wallpapers/
```

Weather location is configured in Edit Dock. US ZIP searches use Zippopotam.us;
other text uses Android's geocoder. For U.S. locations, Stillframe sends
approximate coordinates to the [National Weather Service API](https://www.weather.gov/documentation/services-web-api)
to find a nearby station and read current conditions. Weather location explains
these lookups. Local settings are excluded from Android backup.

## App icons

Open **Edit Dock → App icons** to use a compatible installed icon pack,
including Projectivy Icon Pack. Stillframe looks up compiled or asset
`appfilter.xml` mappings at runtime and does not distribute pack artwork.
Unmapped apps use their Android monochrome asset or their installed icon.
The pin and drawer tiles use the same art and retain the compact square sizes.
Launcher control vectors are adapted from [Google Material Icons](https://github.com/google/material-design-icons)
under Apache 2.0. The license is included in the APK assets.

## Development timing

Start a debug build with `--ez profile_focus true` to enable numerical
`StillframeFocus` logs for navigation. These are key dispatch to frame submission
samples, separate from compositor presentation or physical remote latency.
Use `dumpsys gfxinfo dev.stillframe` for renderer frame statistics. Compare
navigation with steady wallpaper and during a wallpaper transition.
