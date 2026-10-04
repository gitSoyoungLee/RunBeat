package com.runbeat.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 코스 고르기 화면.
 * - 카드를 누르면 그 코스를 선택하고 홈으로 돌아감
 * - 기본 코스: 오른쪽 [복사] 버튼 → "커스텀 코스"로 복사
 * - 커스텀 코스: 오른쪽 ⋯ 버튼 → 수정 / 복사 / 삭제
 */
public class CourseListActivity extends BaseActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("코스 고르기");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        content = Ui.vertical(this);
        int p = Ui.dp(this, 16);
        content.setPadding(p, 0, p, Ui.dp(this, 28));
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(); // 편집 화면에서 돌아오면 다시 그림
    }

    private void render() {
        content.removeAllViews();
        String selectedId = CourseStore.selected(this).id;

        content.addView(section("기본 코스"));
        for (Course c : Course.presets()) content.addView(card(c, c.id.equals(selectedId)));

        content.addView(section("커스텀 코스"));
        List<Course> mine = CourseStore.loadUser(this);
        if (mine.isEmpty()) {
            TextView none = Ui.text(this, "기본 코스의 복사 버튼을 누르거나, 아래에서 새로 만들어 보세요.",
                    13, R.color.sub, false);
            none.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
            content.addView(none);
        }
        for (Course c : mine) content.addView(card(c, c.id.equals(selectedId)));

        TextView add = Ui.text(this, "+  새 코스 만들기", 16, R.color.brand, true);
        add.setGravity(Gravity.CENTER);
        add.setBackgroundResource(R.drawable.bg_ghost);
        add.setClickable(true);
        add.setOnClickListener(v -> openEditor(newEmptyCourse(), true));
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        lp.topMargin = Ui.dp(this, 14);
        content.addView(add, lp);
    }

    private View section(String title) {
        TextView tv = Ui.text(this, title, 14, R.color.sub, true);
        tv.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), 0, Ui.dp(this, 8));
        return tv;
    }

    private View card(Course c, boolean selected) {
        LinearLayout row = Ui.horizontal(this);
        row.setBackgroundResource(selected ? R.drawable.bg_card_selected : R.drawable.bg_card_click);
        row.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            CourseStore.select(this, c.id);
            finish();
        });

        LinearLayout texts = Ui.vertical(this);
        TextView name = Ui.text(this, c.name, 17, R.color.text, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END); // 이름이 길면 "…"
        texts.addView(name);
        TextView total = Ui.text(this, "총 " + Fmt.shortDuration(c.totalSec()), 13, R.color.brand, true);
        total.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(total);
        TextView sum = Ui.text(this, c.summary(), 13, R.color.sub, false);
        sum.setPadding(0, Ui.dp(this, 2), 0, 0);
        texts.addView(sum);
        row.addView(texts, Ui.weight(1));

        // 기본 코스 → 복사 버튼 / 커스텀 코스 → ⋯ 메뉴
        ImageView btn = new ImageView(this);
        btn.setBackgroundResource(R.drawable.bg_icon_btn);
        btn.setScaleType(ImageView.ScaleType.CENTER);
        if (c.preset) {
            btn.setImageResource(R.drawable.ic_copy);
            btn.setContentDescription("복사");
            btn.setOnClickListener(v -> copyCourse(c));
        } else {
            btn.setImageResource(R.drawable.ic_more);
            btn.setContentDescription("코스 메뉴");
            btn.setOnClickListener(v -> showMenu(c));
        }
        row.addView(btn, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(lp);
        return row;
    }

    /** 커스텀 코스 메뉴 */
    private void showMenu(Course c) {
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new String[]{"수정 (이름 · 구간)", "복사", "삭제"}, (d, which) -> {
                    if (which == 0) openEditor(c, false);
                    else if (which == 1) copyCourse(c);
                    else confirmDelete(c);
                })
                .show();
    }

    /** 복사 → "커스텀 코스", "커스텀 코스(1)" ... 이름으로 바로 저장하고 목록에 추가 */
    private void copyCourse(Course c) {
        Course copy = c.copy();
        copy.id = CourseStore.newId();
        copy.name = CourseStore.uniqueName(this);
        copy.preset = false;
        CourseStore.save(this, copy);
        Toast.makeText(this, "'" + copy.name + "'(으)로 복사했어요. ⋯ 버튼에서 이름과 구간을 바꿀 수 있어요",
                Toast.LENGTH_LONG).show();
        render();
    }

    private void confirmDelete(Course c) {
        new AlertDialog.Builder(this)
                .setTitle("'" + c.name + "' 코스를 삭제할까요?")
                .setMessage("러닝 기록은 지워지지 않아요.")
                .setPositiveButton("삭제", (d, w) -> {
                    CourseStore.delete(this, c.id);
                    Toast.makeText(this, "코스를 삭제했어요", Toast.LENGTH_SHORT).show();
                    render();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private Course newEmptyCourse() {
        Course c = new Course();
        c.id = CourseStore.newId();
        c.name = CourseStore.uniqueName(this);
        c.steps.add(Course.Step.single(Course.WARMUP, 300));
        c.steps.add(Course.Step.block(4, new Course.Seg(Course.RUN, 120), new Course.Seg(Course.WALK, 60)));
        c.steps.add(Course.Step.single(Course.COOLDOWN, 300));
        return c;
    }

    private void openEditor(Course c, boolean isNew) {
        try {
            Intent i = new Intent(this, CourseEditActivity.class);
            i.putExtra(CourseEditActivity.EXTRA_JSON, c.toJson().toString());
            i.putExtra(CourseEditActivity.EXTRA_NEW, isNew);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "코스를 열지 못했어요", Toast.LENGTH_SHORT).show();
        }
    }
}
