package io.github.gcross.hyperring;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.gcross.hyperring.diagnostics.DeviceDiagnostics;
import io.github.gcross.hyperring.media.ImportedRingtone;
import io.github.gcross.hyperring.media.MediaStoreRingtoneWriter;
import io.github.gcross.hyperring.ringtone.RingtoneApplyManager;
import io.github.gcross.hyperring.ringtone.RingtoneApplyResult;
import io.github.gcross.hyperring.ringtone.SettingsFallbackLauncher;
import io.github.gcross.hyperring.ringtone.SimTarget;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_AUDIO = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;

    private Uri selectedAudioUri;
    private ImportedRingtone lastImported;
    private TextView selectedAudioText;
    private TextView importedText;
    private TextView statusText;
    private TextView diagnosticsText;
    private RadioGroup targetGroup;
    private EditText sim1KeyText;
    private EditText sim2KeyText;
    private AlertDialog progressDialog;

    private final MediaStoreRingtoneWriter ringtoneWriter = new MediaStoreRingtoneWriter();
    private final RingtoneApplyManager applyManager = new RingtoneApplyManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        requestRuntimePermissions();
        refreshDiagnostics();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                ClipData.Item item = data.getClipData().getItemAt(0);
                uri = item.getUri();
            }
            if (uri != null) {
                selectedAudioUri = uri;
                int flags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                selectedAudioText.setText(resolveDisplayName(uri));
                statusText.setText("已选择音频。");
            }
        }
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(247, 248, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(18);
        int topPadding = dp(18);
        int bottomPadding = dp(24);
        root.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(horizontalPadding, topPadding + insets.getSystemWindowInsetTop(),
                    horizontalPadding, bottomPadding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.post(root::requestApplyInsets);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("HyperRing");
        title.setTextColor(Color.rgb(24, 29, 36));
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("HyperOS 双卡铃声工具");
        subtitle.setTextColor(Color.rgb(96, 104, 116));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        root.addView(buildAudioCard());
        root.addView(buildApplyCard());
        root.addView(buildHyperOsCard());
        root.addView(buildStatusCard());
        root.addView(buildDiagnosticsCard());
        return scrollView;
    }

    private View buildAudioCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("音频"));

        selectedAudioText = bodyText("未选择");
        card.addView(selectedAudioText);

        Button pickButton = primaryButton("选择音频");
        pickButton.setOnClickListener(v -> pickAudio());
        card.addView(pickButton);

        importedText = bodyText("尚未导入铃声库");
        importedText.setPadding(0, dp(10), 0, 0);
        card.addView(importedText);
        return card;
    }

    private View buildApplyCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("应用到"));

        targetGroup = new RadioGroup(this);
        targetGroup.setOrientation(RadioGroup.VERTICAL);
        addTargetButton(SimTarget.SIM_1, true);
        addTargetButton(SimTarget.SIM_2, false);
        addTargetButton(SimTarget.BOTH, false);
        addTargetButton(SimTarget.SYSTEM_DEFAULT, false);
        addTargetButton(SimTarget.IMPORT_ONLY, false);
        card.addView(targetGroup);

        Button applyButton = primaryButton("导入并应用");
        applyButton.setOnClickListener(v -> importAndApply());
        card.addView(applyButton);

        Button permissionButton = secondaryButton("授权修改系统设置");
        permissionButton.setOnClickListener(v -> SettingsFallbackLauncher.openWriteSettings(this));
        card.addView(permissionButton);

        Button soundSettingsButton = secondaryButton("打开系统声音设置");
        soundSettingsButton.setOnClickListener(v -> SettingsFallbackLauncher.openSoundSettings(this));
        card.addView(soundSettingsButton);
        return card;
    }

    private View buildHyperOsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("HyperOS 校准"));

        sim1KeyText = input("SIM1 key");
        sim2KeyText = input("SIM2 key");
        card.addView(sim1KeyText);
        card.addView(sim2KeyText);

        Button saveKeys = secondaryButton("保存 key");
        saveKeys.setOnClickListener(v -> {
            applyManager.saveHyperOsKeys(this,
                    sim1KeyText.getText().toString(), sim2KeyText.getText().toString());
            refreshDiagnostics();
            statusText.setText("已保存 HyperOS key。");
        });
        card.addView(saveKeys);
        return card;
    }

    private View buildStatusCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("状态"));
        statusText = bodyText("准备就绪。");
        card.addView(statusText);
        return card;
    }

    private View buildDiagnosticsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("诊断"));
        Button refreshButton = secondaryButton("刷新诊断");
        refreshButton.setOnClickListener(v -> refreshDiagnostics());
        card.addView(refreshButton);

        diagnosticsText = bodyText("");
        diagnosticsText.setTextSize(12);
        diagnosticsText.setTypeface(Typeface.MONOSPACE);
        diagnosticsText.setTextIsSelectable(true);
        card.addView(diagnosticsText);
        return card;
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_AUDIO);
    }

    private void importAndApply() {
        if (selectedAudioUri == null) {
            Toast.makeText(this, "请先选择音频", Toast.LENGTH_SHORT).show();
            return;
        }
        statusText.setText("正在导入...");
        showProgressDialog();
        new Thread(() -> {
            try {
                ImportedRingtone imported = ringtoneWriter.importAsRingtone(this, selectedAudioUri);
                lastImported = imported;
                SimTarget target = currentTarget();
                RingtoneApplyResult result = applyManager.apply(this, imported.getUri(),
                        imported.getAbsolutePath(), target);
                runOnUiThread(() -> {
                    dismissProgressDialog();
                    importedText.setText(imported.getDisplayName() + " / "
                            + formatBytes(imported.getBytesWritten()));
                    statusText.setText(result.getMessage());
                    refreshDiagnostics();
                    if (result.getStatus() == RingtoneApplyResult.Status.NEED_WRITE_SETTINGS_PERMISSION) {
                        showWriteSettingsDialog(result.getMessage());
                    } else {
                        showResultDialog("处理完成", result.getMessage());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dismissProgressDialog();
                    String message = "导入失败：" + e.getMessage();
                    statusText.setText(message);
                    showResultDialog("处理失败", message);
                });
            }
        }).start();
    }

    private void showProgressDialog() {
        dismissProgressDialog();
        progressDialog = new AlertDialog.Builder(this)
                .setTitle("处理中")
                .setMessage("正在导入音频并应用铃声...")
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    private void showResultDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showWriteSettingsDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("需要授权")
                .setMessage(message)
                .setPositiveButton("去授权",
                        (dialog, which) -> SettingsFallbackLauncher.openWriteSettings(this))
                .setNegativeButton("稍后", null)
                .show();
    }

    private SimTarget currentTarget() {
        int checkedId = targetGroup.getCheckedRadioButtonId();
        View view = targetGroup.findViewById(checkedId);
        Object tag = view == null ? null : view.getTag();
        if (tag instanceof SimTarget) {
            return (SimTarget) tag;
        }
        return SimTarget.SIM_1;
    }

    private void refreshDiagnostics() {
        if (diagnosticsText == null) {
            return;
        }
        diagnosticsText.setText(DeviceDiagnostics.collect(this));
    }

    private void addTargetButton(SimTarget target, boolean checked) {
        RadioButton button = new RadioButton(this);
        button.setText(target.getLabel());
        button.setTag(target);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(32, 36, 44));
        button.setPadding(0, dp(4), 0, dp(4));
        targetGroup.addView(button);
        if (checked) {
            button.setChecked(true);
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(8));
        background.setStroke(1, Color.rgb(230, 233, 238));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.rgb(24, 29, 36));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(10));
        return title;
    }

    private TextView bodyText(String text) {
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(Color.rgb(79, 88, 101));
        body.setTextSize(14);
        body.setLineSpacing(dp(2), 1.0f);
        return body;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setTextSize(14);
        editText.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(247, 248, 250));
        background.setCornerRadius(dp(8));
        background.setStroke(1, Color.rgb(221, 225, 232));
        editText.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, 0, 0, dp(8));
        editText.setLayoutParams(params);
        return editText;
    }

    private Button primaryButton(String text) {
        Button button = button(text, Color.rgb(255, 106, 0), Color.WHITE);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = button(text, Color.rgb(246, 247, 249), Color.rgb(32, 36, 44));
        return button;
    }

    private Button button(String text, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(8));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_PHONE_STATE};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE};
        }
        requestPermissions(permissions, REQUEST_PERMISSIONS);
    }

    private String resolveDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (!TextUtils.isEmpty(value)) {
                        return value;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return uri.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", kb);
        }
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
