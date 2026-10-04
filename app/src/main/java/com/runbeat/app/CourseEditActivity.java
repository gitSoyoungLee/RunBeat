package com.runbeat.app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 코스 편집 화면.
 * - 구간을 누르면 시간 변경
 * - 각 구간·반복 블록 오른쪽 ✕ 로 삭제 (워밍업·쿨다운도 삭제 가능)
 * - 아래 버튼으로 구간 / 반복 블록 추가
 *
 * 화면은 course 데이터를 바꿀 때마다 render()로 통째로 다시 그린다. (단순하고 버그가 적음)
 */
public class CourseEditActivity extends BaseActivity {

    public static final String EXTRA_JSON = "course_json";
    public static final String EXTRA_NEW = "is_new";

    private Course course;
    private EditText etName;
    private LinearLayout stepsBox;
    private TextView tvTotal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            course = Course.fromJson(new JSONObject(getIntent().getStringExtra(EXTRA_JSON)));
        } catch (Exception e) {
            Toast.makeText(this, "코스를 열지 못했어요", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setTitle(getIntent().getBooleanExtra(EXTRA_NEW, false) ? "새 코스" : "코스 수정");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        LinearLayout root = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        root.setPadding(p, 0, p, Ui.dp(this, 28));
        scroll.addView(root);

        // 코스 이름
        root.addView(label("코스 이름"));
        etName = new EditText(this);
        etName.setText(course.name);
        etName.setSingleLine(true);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setTextColor(getColor(R.color.text));
        etName.setTextSize(18);
        etName.setBackgroundResource(R.drawable.bg_input);
        etName.setPadding(p, 0, p, 0);
        root.addView(etName, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        // 구간 목록
        root.addView(label("구간  ·  누르면 시간 변경"));
        stepsBox = Ui.vertical(this);
        root.addView(stepsBox);

        // 추가 버튼
        root.addView(label("추가하기"));
        LinearLayout addRow1 = Ui.horizontal(this);
        addRow1.addView(addButton("+ 워밍업", v -> addSingle(Course.WARMUP, 300)), chipLp());
        addRow1.addView(addButton("+ 달리기", v -> addSingle(Course.RUN, 120)), chipLp());
        addRow1.addView(addButton("+ 걷기", v -> addSingle(Course.WALK, 60)), chipLp());
        root.addView(addRow1);
        LinearLayout addRow2 = Ui.horizontal(this);
        addRow2.setPadding(0, Ui.dp(this, 8), 0, 0);
        addRow2.addView(addButton("+ 쿨다운", v -> addSingle(Course.COOLDOWN, 300)), chipLp());
        addRow2.addView(addButton("+ 반복 블록", v -> addBlock()), chipLp());
        root.addView(addRow2);

        // 총 시간 + 저장
        tvTotal = Ui.text(this, "", 15, R.color.sub, true);
        tvTotal.setGravity(Gravity.CENTER);
        tvTotal.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 10));
        root.addView(tvTotal);

        TextView save = Ui.text(this, "저장", 20, android.R.color.white, true);
        save.setGravity(Gravity.CENTER);
        save.setBackgroundResource(R.drawable.bg_btn_start);
        save.setClickable(true);
        save.setOnClickListener(v -> save());
        root.addView(save, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)));

        setContentView(scroll);
        render();
    }

    // ───────────────────────── 그리기 ─────────────────────────

    private void render() {
        stepsBox.removeAllViews();
        if (course.steps.isEmpty()) {
            TextView none = Ui.text(this, "구간이 없어요. 아래에서 추가해 주세요.", 14, R.color.sub, false);
            none.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, Ui.dp(this, 8));
            stepsBox.addView(none);
        }
        for (int i = 0; i < course.steps.size(); i++) {
            Course.Step st = course.steps.get(i);
            View v = st.isBlock() ? blockView(st) : segRow(st, st.segs.get(0), true);
            LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(this, 8);
            stepsBox.addView(v, lp);
        }
        int total = course.totalSec();
        tvTotal.setText("총 " + (total > 0 ? Fmt.shortDuration(total) : "0분"));
    }

    /** 구간 한 줄: [색 막대] 이름 ...... 5:00  ✕ */
    private View segRow(Course.Step owner, Course.Seg seg, boolean isSingleStep) {
        LinearLayout row = Ui.horizontal(this);
        row.setBackgroundResource(R.drawable.bg_seg);
        row.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 2), 0);
        row.setMinimumHeight(Ui.dp(this, 54));
        row.setClickable(true);
        row.setOnClickListener(v -> editTime(seg));

        View bar = new View(this);
        bar.setBackgroundColor(getColor(Course.typeColorRes(seg.type)));
        LinearLayout.LayoutParams barLp = Ui.lp(Ui.dp(this, 4), Ui.dp(this, 28));
        barLp.rightMargin = Ui.dp(this, 12);
        row.addView(bar, barLp);

        row.addView(Ui.text(this, Course.typeName(seg.type), 16, R.color.text, true), Ui.weight(1));
        row.addView(Ui.text(this, Fmt.duration(seg.sec * 1000L), 17, R.color.text, true));

        row.addView(deleteButton(v -> {
            if (isSingleStep) course.steps.remove(owner);
            else owner.segs.remove(seg);
            render();
        }), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        return row;
    }

    /** 반복 블록: 점선 테두리 안에 [반복 − ×N +  ✕] + 구간들 + [+ 달리기][+ 걷기] */
    private View blockView(Course.Step st) {
        LinearLayout box = Ui.vertical(this);
        box.setBackgroundResource(R.drawable.bg_block);
        int p = Ui.dp(this, 10);
        box.setPadding(p, Ui.dp(this, 4), p, p);

        LinearLayout header = Ui.horizontal(this);
        header.addView(Ui.text(this, "반복", 15, R.color.brand, true), Ui.weight(1));
        header.addView(smallChip("−", v -> {
            if (st.repeat > 1) { st.repeat--; render(); }
        }), Ui.lp(Ui.dp(this, 40), Ui.dp(this, 34)));
        TextView count = Ui.text(this, "× " + st.repeat, 17, R.color.text, true);
        count.setGravity(Gravity.CENTER);
        header.addView(count, Ui.lp(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(smallChip("+", v -> {
            if (st.repeat < 30) { st.repeat++; render(); }
        }), Ui.lp(Ui.dp(this, 40), Ui.dp(this, 34)));
        header.addView(deleteButton(v -> confirmDeleteBlock(st)), Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44)));
        box.addView(header);

        for (Course.Seg g : st.segs) {
            LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 6);
            box.addView(segRow(st, g, false), lp);
        }

        LinearLayout adds = Ui.horizontal(this);
        adds.setPadding(0, Ui.dp(this, 8), 0, 0);
        adds.addView(smallChip("+ 달리기", v -> {
            st.segs.add(new Course.Seg(Course.RUN, 60)); render();
        }), chipLp());
        adds.addView(smallChip("+ 걷기", v -> {
            st.segs.add(new Course.Seg(Course.WALK, 60)); render();
        }), chipLp());
        box.addView(adds);
        return box;
    }

    // ───────────────────────── 추가 / 삭제 ─────────────────────────

    /**
     * 추가 위치 규칙
     * - 워밍업 : 맨 앞 (이미 있는 워밍업들 뒤)
     * - 쿨다운 : 맨 뒤
     * - 그 외  : 맨 뒤의 쿨다운들 바로 앞
     */
    private void addSingle(int type, int sec) {
        Course.Step st = Course.Step.single(type, sec);
        int idx;
        if (type == Course.WARMUP) {
            idx = 0;
            while (idx < course.steps.size() && firstType(course.steps.get(idx)) == Course.WARMUP) idx++;
        } else if (type == Course.COOLDOWN) {
            idx = course.steps.size();
        } else {
            idx = beforeTrailingCooldown();
        }
        course.steps.add(idx, st);
        render();
    }

    private void addBlock() {
        course.steps.add(beforeTrailingCooldown(),
                Course.Step.block(3, new Course.Seg(Course.RUN, 120), new Course.Seg(Course.WALK, 60)));
        render();
    }

    private int beforeTrailingCooldown() {
        int idx = course.steps.size();
        while (idx > 0 && firstType(course.steps.get(idx - 1)) == Course.COOLDOWN
                && !course.steps.get(idx - 1).isBlock()) idx--;
        return idx;
    }

    private static int firstType(Course.Step st) {
        return st.segs.isEmpty() ? -1 : st.segs.get(0).type;
    }

    private void confirmDeleteBlock(Course.Step st) {
        new AlertDialog.Builder(this)
                .setTitle("반복 블록을 삭제할까요?")
                .setMessage("블록 안의 구간도 모두 삭제돼요.")
                .setPositiveButton("삭제", (d, w) -> {
                    course.steps.remove(st);
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───────────────────────── 시간 변경 ─────────────────────────

    private static final String[] SEC_VALUES = {"00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55"};

    private void editTime(Course.Seg seg) {
        NumberPicker min = new NumberPicker(this);
        min.setMinValue(0);
        min.setMaxValue(120);
        min.setValue(seg.sec / 60);

        NumberPicker sec = new NumberPicker(this);
        sec.setMinValue(0);
        sec.setMaxValue(SEC_VALUES.length - 1);
        sec.setDisplayedValues(SEC_VALUES);
        sec.setValue(Math.min((seg.sec % 60) / 5, SEC_VALUES.length - 1));

        LinearLayout box = Ui.horizontal(this);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, Ui.dp(this, 12), 0, 0);
        box.addView(min);
        box.addView(Ui.text(this, " 분   ", 16, R.color.text, false));
        box.addView(sec);
        box.addView(Ui.text(this, " 초", 16, R.color.text, false));

        new AlertDialog.Builder(this)
                .setTitle(Course.typeName(seg.type) + " 시간")
                .setView(box)
                .setPositiveButton("확인", (d, w) -> {
                    int total = min.getValue() * 60 + sec.getValue() * 5;
                    if (total < 5) {
                        Toast.makeText(this, "5초 이상으로 설정해 주세요", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    seg.sec = total;
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───────────────────────── 저장 ─────────────────────────

    private void save() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "코스 이름을 입력해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        // 구간이 하나도 없는 반복 블록은 정리
        course.steps.removeIf(st -> st.segs.isEmpty());
        if (course.totalSec() <= 0) {
            Toast.makeText(this, "구간을 하나 이상 추가해 주세요", Toast.LENGTH_SHORT).show();
            render();
            return;
        }
        course.name = name;
        course.preset = false;
        CourseStore.save(this, course);
        CourseStore.select(this, course.id); // 방금 만든 코스를 바로 선택
        Toast.makeText(this, "저장하고 이 코스를 선택했어요", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ───────────────────────── 작은 부품들 ─────────────────────────

    private TextView label(String s) {
        TextView tv = Ui.text(this, s, 14, R.color.sub, true);
        tv.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 8));
        return tv;
    }

    private TextView addButton(String s, View.OnClickListener l) {
        TextView tv = Ui.chip(this, s);
        tv.setOnClickListener(l);
        return tv;
    }

    private TextView smallChip(String s, View.OnClickListener l) {
        TextView tv = Ui.chip(this, s);
        tv.setPadding(0, 0, 0, 0);
        tv.setOnClickListener(l);
        return tv;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1);
        lp.leftMargin = Ui.dp(this, 3);
        lp.rightMargin = Ui.dp(this, 3);
        return lp;
    }

    private ImageView deleteButton(View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_close);
        iv.setBackgroundResource(R.drawable.bg_icon_btn);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setContentDescription("삭제");
        iv.setOnClickListener(l);
        return iv;
    }
}
