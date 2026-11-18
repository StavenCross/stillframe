package dev.stillframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

final class PortraitPosterDialogs {
    private static final String DISCLOSURE = "Stillframe shares searches for titles in your Continue Watching "
            + "list with TMDB to find portrait artwork. The credential is stored on this device.";
    private final Activity activity;
    private final int tint;
    private final Runnable changed;

    PortraitPosterDialogs(Activity activity, int tint, Runnable changed) {
        this.activity = activity;
        this.tint = tint;
        this.changed = changed;
    }

    void show() {
        Map<String, Runnable> actions = new LinkedHashMap<>();
        boolean hasToken = TmdbSettings.hasToken(activity);
        boolean enabled = TmdbSettings.enabled(activity);
        if (enabled) actions.put("Turn off portrait posters", () -> setEnabled(false));
        else if (hasToken) actions.put("Turn on portrait posters", this::confirmEnable);
        else actions.put("Set up TMDB", () -> token(false));
        if (hasToken) {
            actions.put("Replace TMDB credential", () -> token(true));
            actions.put("Remove TMDB credential", this::confirmRemove);
        }
        actions.put("About and credits", this::credits);
        String[] labels = actions.keySet().toArray(new String[0]);
        AlertDialog dialog = StillframeDialogs.builder(activity)
                .setTitle(enabled ? "Portrait posters · On" : "Portrait posters · Off")
                .setAdapter(StillframeDialogs.options(activity, labels, false),
                        (d, selection) -> actions.get(labels[selection]).run())
                .setNegativeButton("Cancel", null).show();
        StillframeDialogs.style(dialog, tint);
    }

    private void confirmEnable() {
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("Turn on portrait posters?")
                .setMessage(DISCLOSURE)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Turn on", (d, button) -> setEnabled(true)).show();
        StillframeDialogs.style(dialog, tint);
    }

    private void setEnabled(boolean enabled) {
        TmdbSettings.enabled(activity, enabled);
        changed.run();
    }

    private void token(boolean replacing) {
        EditText input = new EditText(activity);
        input.setHint("API Read Access Token or v3 API Key");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSaveEnabled(false);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        AlertDialog dialog = StillframeDialogs.builder(activity)
                .setTitle(replacing ? "Replace TMDB credential" : "Set up TMDB")
                .setMessage(DISCLOSURE).setView(StillframeDialogs.field(input))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(replacing ? "Save credential" : "Save and turn on", null).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.setOnDismissListener(ignored -> input.getText().clear());
        dialog.show();
        StillframeDialogs.style(dialog, tint);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!TmdbCatalog.isCredential(value)) {
                input.setError("Enter the TMDB API Read Access Token or v3 API Key.");
                input.requestFocus();
                return;
            }
            TmdbSettings.save(activity, value);
            if (!replacing) TmdbSettings.enabled(activity, true);
            dialog.dismiss();
            changed.run();
        });
        input.setOnEditorActionListener((v, action, event) ->
                action == EditorInfo.IME_ACTION_DONE && dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        input.requestFocus();
    }

    private void confirmRemove() {
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("Remove TMDB credential?")
                .setMessage("Portrait posters will turn off. You can add another credential at any time.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, button) -> {
                    TmdbSettings.clear(activity);
                    changed.run();
                }).show();
        StillframeDialogs.style(dialog, tint, AlertDialog.BUTTON_POSITIVE);
    }

    private void credits() {
        LinearLayout content = StillframeDialogs.content(activity);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.tmdb_attribution);
        logo.setContentDescription("The Movie Database");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams mark = new LinearLayout.LayoutParams(dp(100), dp(20));
        mark.bottomMargin = dp(10);
        content.addView(logo, mark);
        TextView notice = new TextView(activity);
        notice.setText("This application uses TMDB and the TMDB APIs but is not endorsed, "
                + "certified, or otherwise approved by TMDB.");
        notice.setTextColor(GlassStyle.QUIET);
        notice.setTextSize(17);
        notice.setLineSpacing(dp(3), 1f);
        content.addView(notice);
        AlertDialog dialog = StillframeDialogs.builder(activity).setTitle("About and credits")
                .setView(content).setPositiveButton("Done", null).show();
        StillframeDialogs.style(dialog, tint);
    }

    private int dp(int value) { return GlassStyle.dp(activity, value); }
}
