package com.runbeat.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
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

    /** 이 화면을 만들 때 적용한 테마 설정값. 나중에 설정이 바뀌었는지 비교하려고 기억해 둔다 */
    private String appliedTheme;

    /**
     * [테마 적용]
     * 화면(Activity)이 만들어지기 "직전"에 불리는 메서드.
     * 여기서 넘겨받은 Context(base)를 "밤 모드 비트만 바꾼 Context"로 바꿔 끼우면,
     * 이 화면은 리소스를 고를 때 values/ 또는 values-night/ 중 우리가 정한 쪽을 쓰게 된다.
     * (색, 테마, 다이얼로그, NumberPicker 같은 기본 위젯까지 모두 따라온다)
     *
     * onCreate()에서 하면 이미 늦다. 그때는 리소스가 한 번 정해진 뒤라서다.
     */
    @Override
    protected void attachBaseContext(Context base) {
        appliedTheme = AppSettings.of(base).theme();
        Configuration current = base.getResources().getConfiguration();
        int uiMode = ThemeMode.applyToUiMode(appliedTheme, current.uiMode);
        if (uiMode == current.uiMode) {
            // "시스템 따라가기"이거나, 폰 설정이 이미 원하는 모드와 같으면 바꿀 필요 없음
            super.attachBaseContext(base);
            return;
        }
        Configuration override = new Configuration(current); // 원본은 건드리지 않고 복사본만 수정
        override.uiMode = uiMode;
        super.attachBaseContext(base.createConfigurationContext(override));
    }

    /**
     * 설정 화면에서 테마를 바꾸고 "뒤로"로 돌아오면, 뒤에 있던 화면은 다시 만들어지지 않고
     * onResume()만 불린다. 그래서 여기서 바뀌었는지 확인하고 recreate()로 다시 그린다.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!AppSettings.of(this).theme().equals(appliedTheme)) recreate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 모든 안드로이드 버전에서 똑같이 "바 뒤까지 그리고, 여백은 직접 계산" 방식으로 통일
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            // 주의: 기존 값에 |로 "더해야" 한다.
            // 라이트 테마의 windowLightStatusBar(상태바 아이콘을 어둡게)도 같은 플래그 묶음에 들어 있어서,
            // 새 값으로 덮어쓰면 그 플래그가 지워진다 → 흰 배경에 흰 아이콘이 돼 시계·배터리가 안 보인다.
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
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
