package com.runbeat.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 러닝 기록 목록.
 * - 카드를 누르면 상세 화면
 * - 오른쪽 휴지통 버튼(또는 카드를 길게 누르기)으로 삭제
 */
public class HistoryActivity extends BaseActivity {

    private final List<RunRecord> runs = new ArrayList<>();
    private RunAdapter adapter;
    private ListView listView;
    private View emptyView;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("러닝 기록");

        // 목록과 "기록 없음" 안내를 겹쳐두고 보이기/숨기기만 바꾼다.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setSelector(android.R.color.transparent);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, dp(16));
        listView.addHeaderView(buildHeader(), null, false);
        adapter = new RunAdapter();
        listView.setAdapter(adapter);
        root.addView(listView);

        emptyView = buildEmptyView();
        root.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);

        // 헤더가 0번이라서 실제 기록 위치는 position - 1
        listView.setOnItemClickListener((parent, view, pos, id) -> {
            int i = pos - listView.getHeaderViewsCount();
            if (i < 0 || i >= runs.size()) return;
            Intent intent = new Intent(this, RunDetailActivity.class);
            intent.putExtra(RunDetailActivity.EXTRA_ID, runs.get(i).id);
            startActivity(intent);
        });
        listView.setOnItemLongClickListener((parent, view, pos, id) -> {
            int i = pos - listView.getHeaderViewsCount();
            if (i < 0 || i >= runs.size()) return false;
            confirmDelete(runs.get(i));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(); // 상세 화면에서 삭제하고 돌아와도 바로 반영
    }

    private void load() {
        runs.clear();
        runs.addAll(RunStorage.loadAll(this));
        adapter.notifyDataSetChanged();

        double totalM = 0;
        for (RunRecord r : runs) totalM += r.distanceM;
        tvTotal.setText(String.format(Locale.US, "총 %d회 · %s km", runs.size(), Fmt.km(totalM)));

        boolean empty = runs.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void confirmDelete(RunRecord r) {
        new AlertDialog.Builder(this)
                .setTitle("이 기록을 삭제할까요?")
                .setMessage(Fmt.date(r.startTime) + "\n" + Fmt.km(r.distanceM) + " km · "
                        + Fmt.duration(r.durationMs) + "\n\n삭제하면 되돌릴 수 없어요.")
                .setPositiveButton("삭제", (d, w) -> {
                    RunStorage.delete(this, r.id);
                    Toast.makeText(this, "기록을 삭제했어요", Toast.LENGTH_SHORT).show();
                    load();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───────────────────────── 화면 조각 만들기 ─────────────────────────

    private View buildHeader() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), dp(12));

        tvTotal = new TextView(this);
        tvTotal.setTextColor(getColor(R.color.text));
        tvTotal.setTextSize(22);
        tvTotal.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        box.addView(tvTotal);

        TextView hint = new TextView(this);
        hint.setText("휴지통을 누르거나 카드를 길게 누르면 삭제돼요");
        hint.setTextColor(getColor(R.color.sub));
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, 0);
        box.addView(hint);
        return box;
    }

    private View buildEmptyView() {
        TextView tv = new TextView(this);
        tv.setText("아직 기록이 없어요\n\n첫 러닝을 시작해보세요!");
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(17);
        tv.setTextColor(getColor(R.color.sub));
        tv.setBackgroundColor(getColor(R.color.bg));
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ───────────────────────── 목록 어댑터 ─────────────────────────

    /** 기록 1개를 카드 한 장(item_run.xml)으로 그려주는 어댑터 */
    private class RunAdapter extends BaseAdapter {
        @Override public int getCount() { return runs.size(); }
        @Override public Object getItem(int i) { return runs.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(HistoryActivity.this).inflate(R.layout.item_run, parent, false);
            RunRecord r = runs.get(i);

            ((TextView) v.findViewById(R.id.tvDate)).setText(Fmt.date(r.startTime)
                    + (r.courseName != null ? "  ·  " + r.courseName : "  ·  자유 달리기"));
            ((TextView) v.findViewById(R.id.tvKm)).setText(Fmt.km(r.distanceM));
            ((TextView) v.findViewById(R.id.tvMeta)).setText(Fmt.duration(r.durationMs)
                    + "   ·   " + Fmt.pace(r.avgPace()) + "/km"
                    + "   ·   " + String.format(Locale.US, "%.0f kcal", r.kcal));
            v.findViewById(R.id.tvBadge).setVisibility(r.finished ? View.GONE : View.VISIBLE);
            v.findViewById(R.id.btnDelete).setOnClickListener(b -> confirmDelete(r));
            return v;
        }
    }
}
