package com.runbeat.app;

import android.app.AlertDialog;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 기록 상세: 요약 + 지도 경로 + 구간 페이스 차트 + 1km 구간표 + 삭제 */
public class RunDetailActivity extends BaseActivity {

    public static final String EXTRA_ID = "run_id";

    private MapView map;
    private RunRecord record;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RouteMap.init(this);
        setContentView(R.layout.activity_detail);
        setTitle("러닝 상세");

        String id = getIntent().getStringExtra(EXTRA_ID);
        record = id == null ? null : RunStorage.load(this, id);
        if (record == null) {
            Toast.makeText(this, "기록을 불러오지 못했어요", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        RunRecord r = record;

        ((TextView) findViewById(R.id.tvTitle)).setText(Fmt.date(r.startTime)
                + (r.courseName != null ? "  ·  " + r.courseName : "  ·  자유 달리기")
                + (r.finished ? "" : "  ·  중간 저장"));
        ((TextView) findViewById(R.id.tvBigKm)).setText(Fmt.km(r.distanceM));

        // 가장 빠른 1km 구간
        long bestMs = 0;
        for (RunRecord.Split s : r.splits) {
            if (bestMs == 0 || s.splitMs < bestMs) bestMs = s.splitMs;
        }

        LinearLayout grid = findViewById(R.id.statGrid);
        grid.addView(statRow(
                stat("시간", Fmt.duration(r.durationMs)),
                stat("평균 페이스", Fmt.pace(r.avgPace())),
                stat("칼로리", String.format(Locale.US, "%.0f", r.kcal))));
        View gap = new View(this);
        grid.addView(gap, new LinearLayout.LayoutParams(1, dp(16)));
        grid.addView(statRow(
                stat("케이던스", r.avgCadence > 0 ? String.valueOf(r.avgCadence) : "-"),
                stat("걸음", String.format(Locale.US, "%,d", r.steps)),
                stat("최고 1km", bestMs > 0 ? Fmt.pace(bestMs / 1000.0) : "-")));

        setupMap(r);
        setupSplits(r, bestMs);

        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnSaveImage).setOnClickListener(v -> saveImage());
    }

    // ───────────────────────── 이미지로 저장 ─────────────────────────

    private static final int REQ_STORAGE = 10;
    private boolean saving;

    private void saveImage() {
        if (saving) return; // 버튼 연타 방지
        if (ImageSaver.needsPermission()
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        View content = findViewById(R.id.detailContent);
        View actions = findViewById(R.id.actions);
        // 버튼 영역 바로 위까지 = 요약 · 지도 · 차트 · 구간표 전부
        android.graphics.Bitmap bmp = ViewCapture.capture(content, actions.getTop(), getColor(R.color.bg));
        if (bmp == null) {
            Toast.makeText(this, "이미지를 만들지 못했어요", Toast.LENGTH_SHORT).show();
            return;
        }
        saving = true;
        Toast.makeText(this, "저장하는 중…", Toast.LENGTH_SHORT).show();
        String name = ImageSaver.fileName(record.startTime);
        // PNG로 압축하는 데 시간이 걸리므로 화면이 멈추지 않게 별도 스레드에서
        new Thread(() -> {
            boolean ok = ImageSaver.save(getApplicationContext(), bmp, name);
            bmp.recycle();
            runOnUiThread(() -> {
                saving = false;
                Toast.makeText(this, ok ? "갤러리 RunBeat 앨범에 저장했어요" : "저장하지 못했어요",
                        Toast.LENGTH_LONG).show();
            });
        }, "save-image").start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_STORAGE) return;
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            saveImage();
        } else {
            Toast.makeText(this, "저장 권한이 있어야 갤러리에 저장할 수 있어요", Toast.LENGTH_LONG).show();
        }
    }

    // ───────────────────────── 삭제 ─────────────────────────

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("이 기록을 삭제할까요?")
                .setMessage("삭제하면 되돌릴 수 없어요.")
                .setPositiveButton("삭제", (d, w) -> {
                    RunStorage.delete(this, record.id);
                    Toast.makeText(this, "기록을 삭제했어요", Toast.LENGTH_SHORT).show();
                    finish(); // 목록으로 돌아가면 onResume에서 새로 읽어서 사라져 있음
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───────────────────────── 요약 격자 ─────────────────────────

    private LinearLayout statRow(View... cells) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (View c : cells) {
            row.addView(c, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    private View stat(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getColor(R.color.sub));
        l.setTextSize(12);
        box.addView(l);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(getColor(R.color.text));
        v.setTextSize(22);
        v.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        v.setMaxLines(1);
        // 3칸이라 좁음 → 칸 너비에 맞춰 글자 크기 자동 축소
        v.setAutoSizeTextTypeUniformWithConfiguration(12, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        box.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
        return box;
    }

    // ───────────────────────── 지도 ─────────────────────────

    private void setupMap(RunRecord r) {
        // 지도 모서리를 카드처럼 둥글게 자르기
        FrameLayout frame = findViewById(R.id.mapFrame);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        frame.setClipToOutline(true);

        // 상세 화면의 지도는 "그림"처럼 고정한다.
        // 예전에는 지도가 손가락을 독차지(requestDisallowInterceptTouchEvent)해서
        // 갤럭시 스크롤 캡처가 페이지 대신 지도를 움직이는 문제가 있었다.
        // 이제 지도 위에 투명한 덮개(mapCover)가 터치를 받으므로, 위아래 드래그는 항상 페이지가 스크롤된다.
        map = findViewById(R.id.map);
        RouteMap.show(this, map, r.route, false);

        // 덮개를 "톡" 누르면 움직일 수 있는 전체 화면 지도로
        findViewById(R.id.mapCover).setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, MapActivity.class);
            i.putExtra(MapActivity.EXTRA_ID, r.id);
            startActivity(i);
        });
    }

    // ───────────────────────── 구간표 + 차트 ─────────────────────────

    private void setupSplits(RunRecord r, long bestMs) {
        LinearLayout container = findViewById(R.id.splits);
        List<Float> paceList = new ArrayList<>();
        List<String> labelList = new ArrayList<>();

        for (RunRecord.Split s : r.splits) {
            double pace = s.splitMs / 1000.0; // 1km 구간이므로 걸린 초 = 페이스
            paceList.add((float) pace);
            labelList.add(String.valueOf(s.km));
            addRow(container, s.km + " km", Fmt.pace(pace), Fmt.duration(s.cumMs),
                    s.cadence > 0 ? String.valueOf(s.cadence) : "-", s.splitMs == bestMs);
        }

        // 마지막 남은 1km 미만 구간
        long lastCum = r.splits.isEmpty() ? 0 : r.splits.get(r.splits.size() - 1).cumMs;
        double remDist = r.distanceM - r.splits.size() * 1000.0;
        long remMs = r.durationMs - lastCum;
        if (remDist >= 50 && remMs > 0) {
            double pace = (remMs / 1000.0) / (remDist / 1000.0);
            paceList.add((float) pace);
            labelList.add("끝");
            addRow(container, String.format(Locale.US, "%.2f km", remDist / 1000.0),
                    Fmt.pace(pace), Fmt.duration(r.durationMs), "-", false);
        }

        if (container.getChildCount() == 0) {
            TextView none = new TextView(this);
            none.setText("1km 이상 달리면 구간 기록이 표시돼요");
            none.setTextColor(getColor(R.color.sub));
            none.setTextSize(14);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(12), 0, dp(12));
            container.addView(none);
        }

        float[] paces = new float[paceList.size()];
        for (int i = 0; i < paces.length; i++) paces[i] = paceList.get(i);
        PaceChartView chart = findViewById(R.id.chart);
        chart.setData(paces, labelList.toArray(new String[0]), (float) r.avgPace());
    }

    private void addRow(LinearLayout container, String km, String pace, String cum, String cad, boolean best) {
        // 구분선
        if (container.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(getColor(R.color.card_stroke));
            container.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(10));
        int color = best ? getColor(R.color.brand) : getColor(R.color.text);
        row.addView(cell(km, 0.8f, Gravity.START, color, false));
        row.addView(cell(pace, 1f, Gravity.END, color, true));
        row.addView(cell(cum, 1.2f, Gravity.END, color, false));
        row.addView(cell(cad, 1f, Gravity.END, color, false));
        container.addView(row);
    }

    private TextView cell(String text, float weight, int gravity, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(15);
        tv.setGravity(gravity);
        tv.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
