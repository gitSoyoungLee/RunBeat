package com.runbeat.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 설정 화면. 홈 오른쪽 위 톱니바퀴 버튼으로 연다.
 *
 * 러닝 전에 한 번 정해 두면 되는 값(체중, 자동 일시정지, 테마 등)을 여기로 모아서
 * 홈 화면은 "달리기"에 필요한 것만 보이게 한다.
 * 값은 모두 AppSettings로 읽고 쓴다.
 */
public class SettingsActivity extends BaseActivity {

    private AppSettings settings;
    private TextView tvBatteryState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        settings = AppSettings.of(this);

        // 자동 일시정지
        // RunState에도 바로 넣어 줘야 홈으로 돌아갔을 때(그리고 다음 러닝에서) 그 값이 쓰인다
        Switch sw = findViewById(R.id.switchAutoPause);
        sw.setChecked(settings.autoPause());
        sw.setOnCheckedChangeListener((b, checked) -> {
            settings.setAutoPause(checked);
            RunState.autoPauseEnabled = checked;
        });

        // 체중: 글자를 칠 때마다 저장. "6"처럼 치는 도중의 값은 범위 밖이라 저장되지 않는다
        EditText etWeight = findViewById(R.id.etWeight);
        etWeight.setText(String.format(Locale.US, "%.1f", settings.weightKg()));
        etWeight.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float w = Float.parseFloat(s.toString());
                    if (settings.setWeightKg(w)) RunState.weightKg = w;
                } catch (NumberFormatException ignored) { }
            }
        });

        // 테마
        setupThemeChips();

        // 배터리 최적화 해제
        tvBatteryState = findViewById(R.id.tvBatteryState);
        findViewById(R.id.rowBattery).setOnClickListener(v -> requestIgnoreBattery());

        // 앱 정보: build.gradle.kts의 versionName을 그대로 보여 준다 (코드에 "0.7"을 따로 적지 않게)
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(versionName());
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "-"; // 자기 자신은 항상 설치돼 있으니 실제로는 일어나지 않는다
        }
    }

    /**
     * 테마 칩 3개. 지금 설정과 같은 칩만 selected(주황색)로 보인다.
     *
     * 누르면 저장하고 recreate()로 이 화면을 바로 다시 그린다.
     * 뒤에 있는 홈 같은 화면은 돌아갈 때 BaseActivity.onResume()이 알아서 다시 그린다.
     */
    private void setupThemeChips() {
        String[] modes = {ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK};
        int[] ids = {R.id.btnThemeSystem, R.id.btnThemeLight, R.id.btnThemeDark};
        String current = settings.theme();
        for (int i = 0; i < ids.length; i++) {
            String mode = modes[i];
            TextView chip = findViewById(ids[i]);
            chip.setSelected(mode.equals(current));
            chip.setOnClickListener(v -> {
                if (mode.equals(settings.theme())) return; // 같은 걸 또 누르면 아무것도 안 함
                settings.setTheme(mode);
                recreate();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 시스템 창에서 "허용"을 누르고 돌아오면 바로 "해제됨"으로 바뀌어 보이게
        tvBatteryState.setText(isIgnoringBattery()
                ? "해제됨 ✓ 화면이 꺼져도 기록이 이어져요"
                : "화면이 꺼져도 기록이 끊기지 않게 해 주세요");
    }

    private boolean isIgnoringBattery() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    /**
     * 갤럭시는 배터리를 아끼려고 화면이 꺼진 앱을 멈추기도 한다 → GPS 기록이 중간에 끊긴다.
     * "최적화 해제"를 해 두면 RunBeat는 멈추지 않는다.
     */
    private void requestIgnoreBattery() {
        if (isIgnoringBattery()) {
            Toast.makeText(this, "이미 해제되어 있어요 👍", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            // 일부 기기는 위 창을 막아 둔다 → 전체 목록 화면으로 대신 연다
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
