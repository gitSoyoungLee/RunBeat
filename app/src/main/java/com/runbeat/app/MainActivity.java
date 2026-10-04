package com.runbeat.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    public static final String PREFS = "runbeat";
    private static final int REQ_PERMS = 1;
    private static final int[] CHIP_BPMS = {0, 160, 170, 180};

    private TextView tvTime, tvDistance, tvPace, tvAvgPace, tvCadence, tvKcal, tvSteps, tvGps, tvBpm;
    private TextView tvCourseName, tvCourseSummary, tvSegLabel, tvCourseProgress, tvSegRemain, tvNext;
    private TextView tvPaused, tvMetroNote, btnModeFree, btnModeCourse, btnStartStop, btnPause, btnSkip;
    private View modeRow, cardCourse, cardInterval, cardSettings, runControls;
    private ProgressBar progressCourse;
    private TextView[] bpmChips;

    private SharedPreferences prefs;
    private boolean courseModeSelected; // 홈에서 고른 모드 (러닝 전)
    private boolean pendingStart;
    private boolean waitingForSave;

    private final Handler handler = new Handler(Looper.getMainLooper());
    // 0.5초마다 RunState 값을 읽어서 화면 갱신
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected boolean showHeader() {
        return false; // 홈은 "RunBeat" 헤더가 레이아웃 안에 있음
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTime = findViewById(R.id.tvTime);
        tvDistance = findViewById(R.id.tvDistance);
        tvPace = findViewById(R.id.tvPace);
        tvAvgPace = findViewById(R.id.tvAvgPace);
        tvCadence = findViewById(R.id.tvCadence);
        tvKcal = findViewById(R.id.tvKcal);
        tvSteps = findViewById(R.id.tvSteps);
        tvGps = findViewById(R.id.tvGps);
        tvBpm = findViewById(R.id.tvBpm);
        tvCourseName = findViewById(R.id.tvCourseName);
        tvCourseSummary = findViewById(R.id.tvCourseSummary);
        tvSegLabel = findViewById(R.id.tvSegLabel);
        tvCourseProgress = findViewById(R.id.tvCourseProgress);
        tvSegRemain = findViewById(R.id.tvSegRemain);
        tvNext = findViewById(R.id.tvNext);
        tvPaused = findViewById(R.id.tvPaused);
        tvMetroNote = findViewById(R.id.tvMetroNote);
        btnModeFree = findViewById(R.id.btnModeFree);
        btnModeCourse = findViewById(R.id.btnModeCourse);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnPause = findViewById(R.id.btnPause);
        btnSkip = findViewById(R.id.btnSkip);
        modeRow = findViewById(R.id.modeRow);
        cardCourse = findViewById(R.id.cardCourse);
        cardInterval = findViewById(R.id.cardInterval);
        cardSettings = findViewById(R.id.cardSettings);
        runControls = findViewById(R.id.runControls);
        progressCourse = findViewById(R.id.progressCourse);
        bpmChips = new TextView[]{findViewById(R.id.btnBpmOff), findViewById(R.id.btnBpm160),
                findViewById(R.id.btnBpm170), findViewById(R.id.btnBpm180)};

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        courseModeSelected = "course".equals(prefs.getString("mode", "free"));
        if (!RunState.running) {
            RunState.targetBpm = prefs.getInt("bpm", 0);
            RunState.weightKg = prefs.getFloat("weight", 60f);
            RunState.autoPauseEnabled = prefs.getBoolean("autoPause", true);
        }

        // 모드 선택
        btnModeFree.setOnClickListener(v -> setMode(false));
        btnModeCourse.setOnClickListener(v -> setMode(true));
        cardCourse.setOnClickListener(v -> startActivity(new Intent(this, CourseListActivity.class)));

        // 메트로놈 버튼들 (러닝 중에도 바꿀 수 있음)
        findViewById(R.id.btnBpmOff).setOnClickListener(v -> setBpm(0));
        findViewById(R.id.btnBpm160).setOnClickListener(v -> setBpm(160));
        findViewById(R.id.btnBpm170).setOnClickListener(v -> setBpm(170));
        findViewById(R.id.btnBpm180).setOnClickListener(v -> setBpm(180));
        findViewById(R.id.btnBpmMinus5).setOnClickListener(v -> adjustBpm(-5));
        findViewById(R.id.btnBpmMinus).setOnClickListener(v -> adjustBpm(-1));
        findViewById(R.id.btnBpmPlus).setOnClickListener(v -> adjustBpm(1));
        findViewById(R.id.btnBpmPlus5).setOnClickListener(v -> adjustBpm(5));
        updateBpmLabel();

        // 자동 일시정지 스위치
        Switch sw = findViewById(R.id.switchAutoPause);
        sw.setChecked(RunState.autoPauseEnabled);
        sw.setOnCheckedChangeListener((b, checked) -> {
            RunState.autoPauseEnabled = checked;
            prefs.edit().putBoolean("autoPause", checked).apply();
        });

        // 체중
        EditText etWeight = findViewById(R.id.etWeight);
        etWeight.setText(String.format(Locale.US, "%.1f", RunState.weightKg));
        etWeight.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float w = Float.parseFloat(s.toString());
                    if (w >= 20 && w <= 250) {
                        RunState.weightKg = w;
                        prefs.edit().putFloat("weight", w).apply();
                    }
                } catch (NumberFormatException ignored) { }
            }
        });

        // 시작 / 러닝 중 버튼
        btnStartStop.setOnClickListener(v -> tryStart());
        btnPause.setOnClickListener(v -> sendToService(
                RunState.isPaused() ? RunService.ACTION_RESUME : RunService.ACTION_PAUSE));
        btnSkip.setOnClickListener(v -> sendToService(RunService.ACTION_SKIP));
        findViewById(R.id.btnStop).setOnClickListener(v -> confirmStop());

        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btnBattery).setOnClickListener(v -> requestIgnoreBattery());

        // 앱을 처음 열 때 권한을 미리 받아둔다 (대회 당일 출발선에서 당황하지 않게)
        List<String> missing = missingPermissions();
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCourseCard(); // 코스 고르기 화면에서 돌아왔을 때 반영
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresher);
    }

    // ───────────────────────── 모드 ─────────────────────────

    private void setMode(boolean course) {
        courseModeSelected = course;
        prefs.edit().putString("mode", course ? "course" : "free").apply();
        refresh();
    }

    private void updateCourseCard() {
        Course c = CourseStore.selected(this);
        tvCourseName.setText(c.name + "  ·  " + Fmt.shortDuration(c.totalSec()));
        tvCourseSummary.setText(c.summary());
    }

    // ───────────────────────── 화면 갱신 ─────────────────────────

    private void refresh() {
        boolean running = RunState.running;
        boolean paused = running && RunState.isPaused();
        boolean courseRunning = running && RunState.courseMode;

        // 어떤 카드를 보여줄지
        modeRow.setVisibility(running ? View.GONE : View.VISIBLE);
        btnModeFree.setSelected(!courseModeSelected);
        btnModeCourse.setSelected(courseModeSelected);
        cardCourse.setVisibility(!running && courseModeSelected ? View.VISIBLE : View.GONE);
        cardInterval.setVisibility(courseRunning ? View.VISIBLE : View.GONE);
        cardSettings.setVisibility(running ? View.GONE : View.VISIBLE);
        btnStartStop.setVisibility(running ? View.GONE : View.VISIBLE);
        runControls.setVisibility(running ? View.VISIBLE : View.GONE);
        btnSkip.setVisibility(courseRunning && !RunState.courseDone ? View.VISIBLE : View.GONE);
        tvMetroNote.setVisibility((running ? RunState.courseMode : courseModeSelected) ? View.VISIBLE : View.GONE);

        // 일시정지 표시
        tvPaused.setVisibility(paused ? View.VISIBLE : View.GONE);
        tvPaused.setText(RunState.autoPaused ? "자동 일시정지 · 다시 달리면 재개돼요" : "일시정지됨");
        btnPause.setText(paused ? "재개" : "일시정지");

        // 수치
        tvTime.setText(Fmt.duration(RunState.elapsedMs));
        tvDistance.setText(Fmt.km(RunState.distanceM));
        tvPace.setText(Fmt.pace(RunState.currentPace));
        tvAvgPace.setText(Fmt.pace(RunState.avgPace));
        tvCadence.setText(RunState.cadence > 0 ? String.valueOf(RunState.cadence) : "--");
        tvKcal.setText(String.format(Locale.US, "%.0f", RunState.kcal));
        tvSteps.setText(String.format(Locale.US, "%,d 걸음", RunState.steps));

        // 인터벌
        if (courseRunning) {
            int color = RunState.courseDone ? getColor(R.color.good)
                    : getColor(Course.typeColorRes(RunState.segType));
            tvSegLabel.setText(RunState.segLabel);
            tvSegLabel.setTextColor(color);
            tvSegRemain.setText(RunState.courseDone ? "완료" : Fmt.duration(RunState.segRemainingMs));
            tvSegRemain.setTextColor(color);
            tvNext.setText(RunState.nextLabel);
            tvCourseProgress.setText("전체 " + Fmt.duration(RunState.courseElapsedMs)
                    + " / " + Fmt.duration(RunState.courseTotalMs));
            long total = Math.max(1, RunState.courseTotalMs);
            progressCourse.setProgress((int) (RunState.courseElapsedMs * 1000 / total));
        }

        // GPS 상태 알약: 글자 + 왼쪽 점 색깔로 한눈에
        int acc = RunState.gpsAccuracy;
        int gpsColor;
        if (!running) {
            tvGps.setText("대기 중");
            gpsColor = getColor(R.color.sub);
        } else if (acc < 0) {
            tvGps.setText("GPS 찾는 중");
            gpsColor = getColor(R.color.warn);
        } else if (acc <= 25) {
            tvGps.setText("GPS 양호 ±" + acc + "m");
            gpsColor = getColor(R.color.good);
        } else {
            tvGps.setText("GPS 약함 ±" + acc + "m");
            gpsColor = getColor(R.color.warn);
        }
        tvGps.setCompoundDrawableTintList(ColorStateList.valueOf(gpsColor));

        // 종료 직후 저장된 기록 상세 화면으로 이동
        if (waitingForSave && !running && RunState.lastSavedId != null) {
            waitingForSave = false;
            Toast.makeText(this, "기록이 저장됐어요", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, RunDetailActivity.class);
            i.putExtra(RunDetailActivity.EXTRA_ID, RunState.lastSavedId);
            startActivity(i);
        }
    }

    // ───────────────────────── 메트로놈 ─────────────────────────

    private void setBpm(int bpm) {
        if (bpm != 0) bpm = Math.max(120, Math.min(220, bpm));
        RunState.targetBpm = bpm;
        prefs.edit().putInt("bpm", bpm).apply();
        updateBpmLabel();
    }

    private void adjustBpm(int delta) {
        int cur = RunState.targetBpm;
        setBpm(cur == 0 ? 170 + delta : cur + delta);
    }

    private void updateBpmLabel() {
        int b = RunState.targetBpm;
        tvBpm.setText(b == 0 ? "꺼짐" : b + " spm");
        tvBpm.setTextColor(getColor(b == 0 ? R.color.sub : R.color.brand));
        // 지금 bpm과 같은 칩만 주황색(selected)으로
        for (int i = 0; i < bpmChips.length; i++) {
            bpmChips[i].setSelected(CHIP_BPMS[i] == b);
        }
    }

    // ───────────────────────── 시작 / 종료 ─────────────────────────

    private void tryStart() {
        LocationManager lm = getSystemService(LocationManager.class);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "위치(GPS)를 켜주세요", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        List<String> missing = missingPermissions();
        if (!missing.isEmpty()) {
            pendingStart = true;
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
            return;
        }
        startRun();
    }

    private void startRun() {
        Intent i = new Intent(this, RunService.class).setAction(RunService.ACTION_START);
        if (courseModeSelected) i.putExtra(RunService.EXTRA_COURSE_ID, CourseStore.selected(this).id);
        startForegroundService(i);
    }

    private void sendToService(String action) {
        startService(new Intent(this, RunService.class).setAction(action));
    }

    private void confirmStop() {
        new AlertDialog.Builder(this)
                .setTitle("러닝을 종료할까요?")
                .setMessage("기록이 저장됩니다.")
                .setPositiveButton("종료", (d, w) -> {
                    waitingForSave = true;
                    sendToService(RunService.ACTION_STOP);
                })
                .setNegativeButton("계속 달리기", null)
                .show();
    }

    // ───────────────────────── 권한 ─────────────────────────

    private List<String> missingPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        List<String> missing = new ArrayList<>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        return missing;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMS || !pendingStart) return;
        pendingStart = false;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startRun();
        } else {
            Toast.makeText(this, "정확한 위치 권한이 있어야 거리를 잴 수 있어요", Toast.LENGTH_LONG).show();
        }
    }

    private void requestIgnoreBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "이미 해제되어 있어요 👍", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
