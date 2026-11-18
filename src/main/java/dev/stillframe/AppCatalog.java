package dev.stillframe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AppCatalog {
    static final String STORE = "com.android.vending";
    static final String APPS = ":apps";
    static final String EDIT = ":edit";
    static final String HIDIVE = "com.twentyfouri.androidtv.hidive";
    static final List<String> DEFAULTS = Arrays.asList("com.netflix.ninja", "com.crunchyroll.crunchyroid",
            "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
            "com.disney.disneyplus", "com.wbd.stream", "com.cbs.ott", HIDIVE);
    private static final int[] COLORS = {0xffb71323, 0xffc74c04, 0xff0875a8, 0xff39424c,
            0xff233da3, 0xff2b47aa, 0xff1257ae, 0xff007793};

    final Map<String, Drawers.App> apps = new LinkedHashMap<>();
    final List<IconPacks.Choice> iconPacks;
    final String selectedPack;
    final String packLabel;
    private final Map<String, Intent> launches = new LinkedHashMap<>();

    private AppCatalog(List<IconPacks.Choice> choices, String selected, boolean available) {
        iconPacks = choices;
        selectedPack = available ? selected : "";
        packLabel = choices.stream().filter(choice -> choice.pkg.equals(selectedPack))
                .map(choice -> choice.label).findFirst().orElse("System icons");
    }

    static AppCatalog read(Context context, String selected) {
        List<IconPacks.Choice> choices = IconPacks.discover(context);
        IconPacks pack = IconPacks.open(context, selected);
        AppCatalog result = new AppCatalog(choices, selected, pack != null);
        PackageManager pm = context.getPackageManager();
        for (String category : Arrays.asList(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(context.getPackageName()) || result.apps.containsKey(pkg)) continue;
                String label = info.loadLabel(pm).toString();
                int index = DEFAULTS.indexOf(pkg);
                Drawable mapped = pack == null ? null : pack.icon(pkg, info.activityInfo.name);
                Drawable icon = IconArt.resolve(context, info.loadIcon(pm), mapped);
                result.apps.put(pkg, new Drawers.App(pkg, label, icon, index < 0 ? 0xff456582 : COLORS[index]));
                result.launches.put(pkg, new Intent(query).setClassName(pkg, info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            }
        }
        return result;
    }

    List<Drawers.App> sorted() {
        List<Drawers.App> sorted = new ArrayList<>(apps.values());
        sorted.sort(Comparator.comparing(app -> app.label, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    void launch(Activity activity, String pkg) {
        Intent intent = launches.get(pkg);
        if (intent != null) {
            try {
                activity.startActivity(intent);
                return;
            } catch (RuntimeException ignored) {
                // An app may have been disabled or uninstalled after enumeration.
            }
        }
        Toast.makeText(activity, "App unavailable", Toast.LENGTH_SHORT).show();
    }
}
