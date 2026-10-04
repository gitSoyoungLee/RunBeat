package com.runbeat.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * 코스 저장소.
 * - 기본 코스(프리셋)는 코드에 들어 있어서 수정·삭제 불가
 * - 내가 만든 코스는 SharedPreferences에 JSON 배열로 저장
 */
public final class CourseStore {
    private static final String TAG = "CourseStore";
    private static final String PREFS = "runbeat_courses";
    private static final String KEY = "user_courses";

    private CourseStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Course> loadUser(Context c) {
        List<Course> list = new ArrayList<>();
        String json = prefs(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) list.add(Course.fromJson(arr.getJSONObject(i)));
        } catch (JSONException e) {
            Log.e(TAG, "load failed", e);
        }
        return list;
    }

    private static void saveAll(Context c, List<Course> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Course k : list) arr.put(k.toJson());
        } catch (JSONException e) {
            Log.e(TAG, "save failed", e);
            return;
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }

    /** 같은 id가 있으면 덮어쓰고, 없으면 맨 뒤에 추가 */
    public static void save(Context c, Course course) {
        List<Course> list = loadUser(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(course.id)) {
                list.set(i, course);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(course);
        saveAll(c, list);
    }

    public static void delete(Context c, String id) {
        List<Course> list = loadUser(c);
        list.removeIf(k -> k.id.equals(id));
        saveAll(c, list);
    }

    /** id로 코스 찾기 (프리셋 → 내 코스 순서). 없으면 null */
    public static Course find(Context c, String id) {
        if (id == null) return null;
        for (Course k : Course.presets()) if (k.id.equals(id)) return k;
        for (Course k : loadUser(c)) if (k.id.equals(id)) return k;
        return null;
    }

    /** 선택된 코스. 아무것도 없으면 첫 번째 프리셋 */
    public static Course selected(Context c) {
        Course k = find(c, c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString("courseId", null));
        return k != null ? k : Course.presets().get(0);
    }

    public static void select(Context c, String id) {
        c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .edit().putString("courseId", id).apply();
    }

    public static final String DEFAULT_NAME = "커스텀 코스";

    /**
     * 파일 복사할 때처럼 겹치지 않는 이름 만들기.
     * "커스텀 코스"가 없으면 그대로, 있으면 "커스텀 코스(1)", "커스텀 코스(2)" ... 중 비어 있는 가장 작은 번호.
     * (사용자가 직접 바꾸는 이름은 겹쳐도 괜찮음)
     */
    public static String uniqueName(Context c) {
        return uniqueName(namesOf(loadUser(c)), DEFAULT_NAME);
    }

    static String uniqueName(java.util.Set<String> existing, String base) {
        if (!existing.contains(base)) return base;
        for (int n = 1; ; n++) {
            String candidate = base + "(" + n + ")";
            if (!existing.contains(candidate)) return candidate;
        }
    }

    private static java.util.Set<String> namesOf(List<Course> list) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Course k : list) names.add(k.name);
        return names;
    }

    public static String newId() {
        return "user_" + System.currentTimeMillis();
    }
}
