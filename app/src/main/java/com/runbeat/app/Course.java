package com.runbeat.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 인터벌 코스.
 *
 * 코스 = 단계(Step)들의 목록
 *   단계는 두 종류:
 *   - 단일 구간  : repeat == 0, segs에 구간 1개   (예: 워밍업 걷기 5분)
 *   - 반복 블록  : repeat >= 1, segs에 여러 구간  (예: [달리기 1분, 걷기 2분] × 6)
 *
 * 달릴 때는 flatten()으로 "구간 일렬 목록"으로 펼쳐서 하나씩 진행한다.
 */
public class Course {

    // 구간 종류
    public static final int WARMUP = 0;
    public static final int RUN = 1;
    public static final int WALK = 2;
    public static final int COOLDOWN = 3;

    /** 구간 1개 */
    public static class Seg {
        public int type;
        public int sec;

        public Seg(int type, int sec) {
            this.type = type;
            this.sec = sec;
        }
    }

    /** 단계 (단일 구간 또는 반복 블록) */
    public static class Step {
        public int repeat;                    // 0이면 단일 구간
        public List<Seg> segs = new ArrayList<>();

        public boolean isBlock() {
            return repeat > 0;
        }

        static Step single(int type, int sec) {
            Step s = new Step();
            s.segs.add(new Seg(type, sec));
            return s;
        }

        static Step block(int repeat, Seg... segs) {
            Step s = new Step();
            s.repeat = repeat;
            for (Seg g : segs) s.segs.add(g);
            return s;
        }
    }

    /** 펼쳐진 구간 (달리는 중에 사용) */
    public static class Planned {
        public final int type;
        public final int sec;
        public final int rep;      // 반복 블록 안에서 몇 회차인지 (1부터), 단일 구간이면 0
        public final int repTotal; // 반복 블록의 총 횟수, 단일 구간이면 0

        Planned(int type, int sec, int rep, int repTotal) {
            this.type = type;
            this.sec = sec;
            this.rep = rep;
            this.repTotal = repTotal;
        }

        /** "달리기 2/6" */
        public String label() {
            String n = typeName(type);
            return repTotal > 1 ? n + " " + rep + "/" + repTotal : n;
        }
    }

    public String id;
    public String name;
    public boolean preset;
    public List<Step> steps = new ArrayList<>();

    // ───────────────────────── 계산 ─────────────────────────

    public List<Planned> flatten() {
        List<Planned> out = new ArrayList<>();
        for (Step st : steps) {
            if (!st.isBlock()) {
                for (Seg g : st.segs) out.add(new Planned(g.type, g.sec, 0, 0));
            } else {
                for (int r = 1; r <= st.repeat; r++) {
                    for (Seg g : st.segs) out.add(new Planned(g.type, g.sec, r, st.repeat));
                }
            }
        }
        // 0초짜리 구간은 건너뜀
        out.removeIf(p -> p.sec <= 0);
        return out;
    }

    public int totalSec() {
        int t = 0;
        for (Planned p : flatten()) t += p.sec;
        return t;
    }

    /** "워밍업 5분 · 달리기 1분 + 걷기 2분 × 6 · 쿨다운 5분" */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Step st : steps) {
            if (st.segs.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            if (!st.isBlock()) {
                Seg g = st.segs.get(0);
                sb.append(shortName(g.type)).append(' ').append(Fmt.shortDuration(g.sec));
            } else {
                for (int i = 0; i < st.segs.size(); i++) {
                    Seg g = st.segs.get(i);
                    if (i > 0) sb.append(" + ");
                    sb.append(shortName(g.type)).append(' ').append(Fmt.shortDuration(g.sec));
                }
                if (st.repeat > 1) sb.append(" × ").append(st.repeat);
            }
        }
        return sb.toString();
    }

    public static String typeName(int type) {
        switch (type) {
            case WARMUP: return "워밍업 걷기";
            case RUN: return "달리기";
            case WALK: return "걷기";
            case COOLDOWN: return "쿨다운 걷기";
            default: return "?";
        }
    }

    private static String shortName(int type) {
        switch (type) {
            case WARMUP: return "워밍업";
            case COOLDOWN: return "쿨다운";
            default: return typeName(type);
        }
    }

    public static int typeColorRes(int type) {
        switch (type) {
            case RUN: return R.color.brand;
            case WALK: return R.color.chart;
            default: return R.color.sub;
        }
    }

    public Course copy() {
        try {
            return fromJson(toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    // ───────────────────────── 기본 코스(프리셋) ─────────────────────────

    public static List<Course> presets() {
        List<Course> list = new ArrayList<>();
        list.add(preset("preset_intro", "입문 RUN",
                Step.block(6, new Seg(RUN, 60), new Seg(WALK, 120))));
        list.add(preset("preset_basic", "초보 RUN",
                Step.block(5, new Seg(RUN, 180), new Seg(WALK, 60))));
        list.add(preset("preset_adv", "심화 RUN",
                Step.block(3, new Seg(RUN, 480), new Seg(WALK, 120))));
        list.add(preset("preset_30", "30분 지속 RUN",
                Step.single(RUN, 1800)));
        return list;
    }

    /** 모든 기본 코스는 워밍업 5분 + 본 운동 + 쿨다운 5분 */
    private static Course preset(String id, String name, Step main) {
        Course c = new Course();
        c.id = id;
        c.name = name;
        c.preset = true;
        c.steps.add(Step.single(WARMUP, 300));
        c.steps.add(main);
        c.steps.add(Step.single(COOLDOWN, 300));
        return c;
    }

    // ───────────────────────── JSON ─────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        JSONArray arr = new JSONArray();
        for (Step st : steps) {
            JSONObject so = new JSONObject();
            so.put("repeat", st.repeat);
            JSONArray segs = new JSONArray();
            for (Seg g : st.segs) {
                JSONObject go = new JSONObject();
                go.put("type", g.type);
                go.put("sec", g.sec);
                segs.put(go);
            }
            so.put("segs", segs);
            arr.put(so);
        }
        o.put("steps", arr);
        return o;
    }

    public static Course fromJson(JSONObject o) throws JSONException {
        Course c = new Course();
        c.id = o.getString("id");
        c.name = o.getString("name");
        JSONArray arr = o.getJSONArray("steps");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject so = arr.getJSONObject(i);
            Step st = new Step();
            st.repeat = so.optInt("repeat", 0);
            JSONArray segs = so.getJSONArray("segs");
            for (int j = 0; j < segs.length(); j++) {
                JSONObject go = segs.getJSONObject(j);
                st.segs.add(new Seg(go.getInt("type"), go.getInt("sec")));
            }
            c.steps.add(st);
        }
        return c;
    }
}
