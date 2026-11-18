package dev.stillframe;

public final class AmbienceWeatherTest {
    public static void main(String[] arguments) {
        WallpaperImage.Geometry portrait = WallpaperImage.geometry(4000, 6000, 1920, 1080);
        require(portrait.width == 1440 && portrait.height == 2160,
                "Portrait decoding stays within the two-screen-height limit");
        require(portrait.cropWidth == 1440 && portrait.cropHeight == 810,
                "Wallpaper crop matches the display aspect ratio");
        WallpaperImage.Geometry landscape = WallpaperImage.geometry(3840, 2160, 1920, 1080);
        require(landscape.width == 1920 && landscape.height == 1080,
                "A matching wallpaper decodes at display size");
        WallpaperImage.Geometry small = WallpaperImage.geometry(320, 180, 1920, 1080);
        require(small.width == 320 && small.height == 180,
                "Small images are enlarged by the view, keeping decoder allocations bounded");
        WallpaperImage.Geometry banner = WallpaperImage.geometry(12000, 1, 1920, 1080);
        require(banner.cropWidth > 0 && banner.cropHeight > 0,
                "Extreme aspect ratios still yield a nonempty crop");

        int blueAccent = 0xff3e4e5c;
        int idleBlue = DockPalette.surface(blueAccent);
        int idleRed = DockPalette.surface(0xff5c4e3e);
        require((idleBlue >>> 24) < 0xc0 && (idleBlue >>> 24) >= 0x70,
                "Idle dock glass lets more of the wallpaper show through");
        require((idleBlue & 255) - (idleBlue >>> 16 & 255) > 30
                        && (idleRed >>> 16 & 255) - (idleRed & 255) > 30,
                "Cool and warm wallpapers retain their dominant hues clearly");
        int neutral = DockPalette.surface(0xff555555);
        require((neutral >>> 16 & 255) == (neutral & 255),
                "Neutral wallpaper accents stay neutral");
        int mark = DockPalette.mark(blueAccent);
        require((mark & 255) == 255 && (mark >>> 16 & 255) >= 224 && (mark >>> 16 & 255) < 255,
                "Idle icon marks pick up wallpaper color while retaining readability");

        require("Pictures/Wallpapers/".equals(
                        WallpaperFolders.legacyRelativePath("/storage/emulated/0/Pictures/Wallpapers/example.jpg")),
                "Legacy MediaStore paths reveal the folder without storage-root details");
        require("Pictures/".equals(
                        WallpaperFolders.legacyRelativePath("/storage/1234-ABCD/Pictures/example.jpg")),
                "Legacy removable storage uses the same relative folder labels");
        require("/".equals(WallpaperFolders.legacyRelativePath("/storage/emulated/0/example.jpg"))
                        && WallpaperFolders.legacyRelativePath("/data/private/example.jpg") == null,
                "Root images have their own label and nonexternal paths are ignored");

        require("01234".equals(Weather.zipCode("01234-0000")),
                "ZIP+4 lookups send only the five-digit ZIP, including leading zeros");
        require(Weather.zipCode("12345 Example Lane") == null,
                "An address beginning with digits stays an address");
        require("12.35,-34.57".equals(Weather.roundedPoint(12.345678, -34.56789)),
                "Weather provider coordinates are rounded before transmission");
        require(Weather.roundedPoint(Double.NaN, 0) == null
                        && Weather.roundedPoint(90.1, -180) == null,
                "Malformed and out-of-range coordinates are rejected");
        long now = java.time.Instant.parse("2026-01-01T12:00:00Z").toEpochMilli();
        Weather.Reading day = NwsWeather.reading(23.7, "wmoUnit:degC", "Clear",
                "https://api.weather.gov/icons/land/day/skc", "2026-01-01T11:50:00+00:00", now);
        Weather.Reading night = NwsWeather.reading(-2.7, "wmoUnit:degF", "Fair",
                "https://api.weather.gov/icons/land/night/skc", "2026-01-01T11:40:00Z", now);
        require("75°F".equals(day.temperature) && day.icon == NwsWeather.Icon.CLEAR,
                "Celsius observations become rounded Fahrenheit temperatures");
        require("-3°F".equals(night.temperature) && night.icon == NwsWeather.Icon.NIGHT,
                "Existing Fahrenheit values and nighttime conditions remain distinct");
        Weather.Reading freezing = NwsWeather.reading(29, "wmoUnit:degF", "Freezing rain", "",
                "2026-01-01T10:30:00Z", now);
        require("Freezing rain".equals(freezing.condition) && freezing.icon == NwsWeather.Icon.SNOW,
                "Precipitation can be categorized when the NWS icon metadata is absent");
        require(NwsWeather.reading(Double.NaN, "wmoUnit:degC", "", "", "2026-01-01T11:50:00Z", now) == null
                        && NwsWeather.reading(3, "wmoUnit:foo", "", "", "2026-01-01T11:50:00Z", now) == null
                        && NwsWeather.reading(3, "wmoUnit:degC", "", "", "2026-01-01T09:59:59Z", now) == null
                        && NwsWeather.reading(3, "wmoUnit:degC", "", "", "invalid", now) == null,
                "Missing, unknown, stale or malformed observations are rejected");
        Weather.Reading found = NwsWeather.firstFresh(java.util.Arrays.asList("TEST1", "TEST2", "TEST3"), station -> {
            if ("TEST1".equals(station)) throw new java.io.IOException("Offline station");
            if ("TEST2".equals(station)) return null;
            return night;
        });
        require(found == night, "A failed or stale station falls back to the next nearby station");
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
