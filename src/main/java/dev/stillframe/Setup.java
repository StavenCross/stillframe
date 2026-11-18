package dev.stillframe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class Setup {
    private static final int FOLDER_REQUEST = 21;
    private static final int HOME_REQUEST = 22;
    private static final int IMAGES_REQUEST = 23;

    private final Activity activity;
    private final Ambience ambience;
    private final Weather weather;
    private final Runnable changed;
    private int tint = 0xff263949;

    Setup(Activity activity, Ambience ambience, Weather weather, Runnable changed) {
        this.activity = activity;
        this.ambience = ambience;
        this.weather = weather;
        this.changed = changed;
    }

    void tint(int accent) { tint = accent; }
    int tint() { return tint; }

    void icons(AppCatalog catalog, Consumer<String> apply) {
        List<IconPacks.Choice> choices = new ArrayList<>();
        choices.add(new IconPacks.Choice("", "System icons"));
        choices.addAll(catalog.iconPacks);
        String[] labels = choices.stream().map(choice -> choice.label).toArray(String[]::new);
        int current = 0;
        for (int i = 0; i < choices.size(); i++)
            if (choices.get(i).pkg.equals(catalog.selectedPack)) current = i;
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("App icons")
                .setSingleChoiceItems(StillframeDialogs.options(activity, labels, true), current, (d, index) -> {
                    d.dismiss();
                    apply.accept(choices.get(index).pkg);
                }).setNegativeButton("Cancel", null).show();
        StillframeDialogs.style(dialog, tint);
    }

    void location() {
        EditText input = new EditText(activity);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(150)});
        input.setHint("Address or ZIP code");
        input.setText(weather.location());
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = StillframeDialogs.builder(activity)
                .setTitle("Weather location").setMessage("Enter an address or ZIP code.")
                .setView(StillframeDialogs.field(input))
                .setNegativeButton("Cancel", null)
                .setNeutralButton("About weather", (d, button) -> weatherDetails())
                .setPositiveButton("Save", (d, button) -> locationSaved(input.getText().toString()))
                .create();
        dialog.setOnShowListener(d -> input.requestFocus());
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        StillframeDialogs.style(dialog, tint);
    }

    private void locationSaved(String value) {
        weather.location(value);
        changed.run();
    }

    private void weatherDetails() {
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("Weather data")
                .setMessage(Weather.PRIVACY)
                .setNegativeButton("Back", (d, button) -> location())
                .setNeutralButton("Clear location", (d, button) -> locationSaved(""))
                .setPositiveButton("Done", null).show();
        StillframeDialogs.style(dialog, tint, AlertDialog.BUTTON_NEUTRAL);
    }

    void folder() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Bundled wallpapers");
        actions.add(() -> { ambience.folder(null); changed.run(); });
        if (ambience.hasLocalFolder()) {
            labels.add("Stillframe Wallpapers folder");
            actions.add(() -> { ambience.useLocalFolder(); changed.run(); });
        }
        ResolveInfo picker = activity.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PackageManager.MATCH_DEFAULT_ONLY);
        if (picker != null && !picker.activityInfo.name.endsWith("$DocumentsStub")) {
            labels.add("Browse device folders");
            actions.add(this::browse);
        }
        labels.add("Browse shared image folders");
        actions.add(this::shared);
        String source = ambience.folderLabel();
        int current = source.startsWith("Bundled") || source.startsWith("Using bundled") ? 0
                : source.equals("Wallpapers folder") && ambience.hasLocalFolder() ? 1 : -1;
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("Slideshow folder")
                .setSingleChoiceItems(StillframeDialogs.options(activity, labels.toArray(new String[0]), true),
                        current, (d, index) -> {
                    d.dismiss();
                    actions.get(index).run();
                })
                .setNegativeButton("Cancel", null).show();
        StillframeDialogs.style(dialog, tint);
    }

    private void browse() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            activity.startActivityForResult(intent, FOLDER_REQUEST);
        } catch (RuntimeException failure) {
            message("The system folder picker is unavailable.");
        }
    }

    void tvListings(int request) {
        android.content.SharedPreferences permissions = activity.getSharedPreferences(
                "permissions", Activity.MODE_PRIVATE);
        if (permissions.getBoolean("tv_asked", false)
                && !activity.shouldShowRequestPermissionRationale(WatchFeed.PERMISSION)) {
            open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.getPackageName(), null)));
        } else {
            permissions.edit().putBoolean("tv_asked", true).apply();
            activity.requestPermissions(new String[]{WatchFeed.PERMISSION}, request);
        }
    }

    void requestHome() {
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("Set as Home")
                .setMessage("Choose the Android Home app first. If the remote still opens Google TV, "
                        + "enable Stillframe Home override in Accessibility settings.")
                .setPositiveButton("Default Home app", (d, button) -> requestHomeRole())
                .setNeutralButton("Home override", (d, button) -> showHomeOverride())
                .setNegativeButton("Cancel", null).show();
        StillframeDialogs.style(dialog, tint);
    }

    private void showHomeOverride() {
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle(R.string.home_override_label)
                .setMessage(activity.getString(R.string.home_override_description)
                        + "\n\nTurn off any other launcher's override first. You can turn this service "
                        + "off on the same Android settings page.")
                .setPositiveButton("Open accessibility", (d, button) ->
                        open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Back", (d, button) -> requestHome()).show();
        StillframeDialogs.style(dialog, tint);
    }

    private void requestHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager manager = activity.getSystemService(RoleManager.class);
            if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    activity.startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_HOME), HOME_REQUEST);
                    return;
                } catch (RuntimeException ignored) { /* Try Android's Home settings instead. */ }
            }
        }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }

    void result(int request, int result, Intent data) {
        if (request == FOLDER_REQUEST && result == Activity.RESULT_OK && data != null && data.getData() != null) {
            ambience.folder(data.getData());
            changed.run();
        }
    }

    private void shared() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{permission}, IMAGES_REQUEST);
            return;
        }
        ambience.sharedFolders(paths -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (paths.isEmpty()) {
                message("No indexed image folders. Try Pictures or the Stillframe Wallpapers folder.");
                return;
            }
            String[] labels = paths.stream().map(path -> "/".equals(path) ? "Shared storage" : path)
                    .toArray(String[]::new);
            AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("Shared image folders")
                    .setAdapter(StillframeDialogs.options(activity, labels, false), (selection, index) -> {
                        ambience.useSharedFolder(paths.get(index));
                        changed.run();
                    }).setNegativeButton("Cancel", null).show();
            StillframeDialogs.style(dialog, tint);
        });
    }

    void permissions(int request, int[] grants) {
        if (request == IMAGES_REQUEST && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED)
            activity.getWindow().getDecorView().postDelayed(this::shared, 175);
    }

    private void open(Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (RuntimeException failure) {
            message("Android couldn't open this screen.");
        }
    }

    private void message(String value) {
        Toast.makeText(activity, value, Toast.LENGTH_LONG).show();
    }
}
