package com.runbeat.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 모든 화면의 부모 클래스.
 *
 * [왜 필요할까?]
 * Android 15부터는 앱 화면이 상태바(위)와 내비게이션 바(아래) "뒤까지" 강제로 늘어난다.
 * 그래서 아무 처리를 안 하면 맨 아래 버튼이 갤럭시 내비게이션 바에 가려진다.
 *
 * [해결]
 * 시스템이 알려주는 "가려지는 영역 크기(insets)"만큼 화면 안쪽에 여백(padding)을 준다.
 * → 상태바와 내비게이션 바 사이 공간만 RunBeat 화면이 된다.
 * 키보드가 올라오면 키보드 높이만큼도 비워준다.
 *
 * 또, 기본 액션바(제목 줄) 대신 직접 만든 헤더를 화면 맨 위에 "쌓아서" 내용을 덮지 않게 한다.
 */
public abstract class BaseActivity extends Activity {

    private TextView headerTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 모든 안드로이드 버전에서 똑같이 "바 뒤까지 그리고, 여백은 직접 계산" 방식으로 통일
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    /** 헤더(뒤로가기 + 제목)를 보여줄지. 홈 화면은 자기만의 헤더가 있어서 false */
    protected boolean showHeader() {
        return true;
    }

    @Override
    public void setContentView(int layoutResID) {
        setContentView(getLayoutInflater().inflate(layoutResID, null));
    }

    @Override
    public void setContentView(View content) {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(getColor(R.color.bg)); // 여백(바 아래) 부분도 앱 배경색
        if (showHeader()) root.addView(buildHeader());
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                l = bars.left;
                t = bars.top;
                r = bars.right;
                b = Math.max(bars.bottom, ime.bottom);
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });
        super.setContentView(root);
        root.requestApplyInsets();
    }

    private View buildHeader() {
        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 16), 0);
        bar.setMinimumHeight(Ui.dp(this, 56));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(R.drawable.bg_icon_btn);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("뒤로");
        back.setOnClickListener(v -> finish());
        bar.addView(back, Ui.lp(Ui.dp(this, 48), Ui.dp(this, 48)));

        headerTitle = Ui.text(this, String.valueOf(getTitle()), 20, R.color.text, true);
        headerTitle.setGravity(Gravity.CENTER_VERTICAL);
        headerTitle.setSingleLine(true);
        headerTitle.setPadding(Ui.dp(this, 4), 0, 0, 0);
        bar.addView(headerTitle, Ui.weight(1));
        return bar;
    }

    /** setTitle("...")을 부르면 헤더 글자도 바뀌게 */
    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        if (headerTitle != null) headerTitle.setText(title);
    }
}
