package dev.stillframe;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads compatible user-installed packs without loading another app's code. */
final class IconPacks {
    private static final String[] ACTIONS = {"org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.novalauncher.THEME", "com.anddoes.launcher.THEME"};

    static final class Choice {
        final String pkg, label;
        Choice(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private final Resources resources;
    private final String pkg;
    private final PackMapping mapping;

    private IconPacks(Resources resources, String pkg, PackMapping mapping) {
        this.resources = resources;
        this.pkg = pkg;
        this.mapping = mapping;
    }

    static List<Choice> discover(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, Choice> choices = new LinkedHashMap<>();
        for (String action : ACTIONS) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(action), 0)) {
                String pkg = info.activityInfo.packageName;
                choices.putIfAbsent(pkg, new Choice(pkg, info.activityInfo.applicationInfo.loadLabel(pm).toString()));
            }
        }
        List<Choice> result = new ArrayList<>(choices.values());
        result.sort(Comparator.comparing(choice -> choice.label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    static IconPacks open(Context context, String pkg) {
        if (pkg.isEmpty()) return null;
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(pkg);
            PackMapping mapping = new PackMapping();
            // Blueprint packages can ship a sample asset and the actual mapping in compiled XML.
            int filter = res.getIdentifier("appfilter", "xml", pkg);
            if (filter != 0) {
                try (XmlResourceParser parser = res.getXml(filter)) {
                    parse(parser, mapping);
                }
            } else {
                try (InputStream input = res.getAssets().open("appfilter.xml")) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(input, null);
                    parse(parser, mapping);
                }
            }
            return new IconPacks(res, pkg, mapping);
        } catch (Exception unavailable) {
            return null;
        }
    }

    private static void parse(XmlPullParser parser, PackMapping mapping) throws Exception {
        for (int count = 0; count < 60_000; count++) {
            int event = parser.next();
            if (event == XmlPullParser.END_DOCUMENT) return;
            if (event == XmlPullParser.START_TAG && "item".equals(parser.getName()))
                mapping.add(parser.getAttributeValue(null, "component"), parser.getAttributeValue(null, "drawable"));
        }
    }

    Drawable icon(String app, String activity) {
        String name = mapping.find(app, activity);
        if (name == null) return null;
        int id = resources.getIdentifier(name, "drawable", pkg);
        if (id == 0) id = resources.getIdentifier(name, "mipmap", pkg);
        if (id == 0) return null;
        try {
            return resources.getDrawable(id, null);
        } catch (Resources.NotFoundException invalid) {
            return null;
        }
    }
}
