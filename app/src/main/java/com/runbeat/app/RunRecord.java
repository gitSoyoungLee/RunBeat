package com.runbeat.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 러닝 기록 1회분. JSON 파일로 저장/불러오기 한다. */
public class RunRecord {

    /** 1km 구간 기록 */
    public static class Split {
        public int km;        // 몇 번째 km
        public long cumMs;    // 출발부터 이 km 도달까지 걸린 시간
        public long splitMs;  // 이 1km 구간에 걸린 시간 (= 구간 페이스)
        public int cadence;   // 이 구간 평균 케이던스

        public Split(int km, long cumMs, long splitMs, int cadence) {
            this.km = km;
            this.cumMs = cumMs;
            this.splitMs = splitMs;
            this.cadence = cadence;
        }
    }

    public String id;
    public long startTime;
    public long durationMs;
    public double distanceM;
    public double kcal;
    public double weightKg;
    public int steps;
    public int avgCadence;
    public boolean finished;
    public String courseName; // 코스 달리기면 코스 이름, 자유 달리기면 null
    public List<Split> splits = new ArrayList<>();
    /** 경로: [위도, 경도, 위도, 경도, ...] 형태로 일렬로 저장 */
    public double[] route = new double[0];

    public double avgPace() {
        return distanceM > 0 ? (durationMs / 1000.0) / (distanceM / 1000.0) : 0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("start", startTime);
        o.put("durationMs", durationMs);
        o.put("distanceM", distanceM);
        o.put("kcal", kcal);
        o.put("weightKg", weightKg);
        o.put("steps", steps);
        o.put("avgCadence", avgCadence);
        o.put("finished", finished);
        if (courseName != null) o.put("course", courseName);

        JSONArray sp = new JSONArray();
        for (Split s : splits) {
            JSONObject j = new JSONObject();
            j.put("km", s.km);
            j.put("cumMs", s.cumMs);
            j.put("splitMs", s.splitMs);
            j.put("cad", s.cadence);
            sp.put(j);
        }
        o.put("splits", sp);

        JSONArray r = new JSONArray();
        for (double v : route) r.put(v);
        o.put("route", r);
        return o;
    }

    public static RunRecord fromJson(JSONObject o) throws JSONException {
        RunRecord r = new RunRecord();
        r.id = o.getString("id");
        r.startTime = o.getLong("start");
        r.durationMs = o.getLong("durationMs");
        r.distanceM = o.getDouble("distanceM");
        r.kcal = o.optDouble("kcal", 0);
        r.weightKg = o.optDouble("weightKg", 0);
        r.steps = o.optInt("steps", 0);
        r.avgCadence = o.optInt("avgCadence", 0);
        r.finished = o.optBoolean("finished", true);
        r.courseName = o.has("course") ? o.getString("course") : null;

        JSONArray sp = o.optJSONArray("splits");
        if (sp != null) {
            for (int i = 0; i < sp.length(); i++) {
                JSONObject j = sp.getJSONObject(i);
                r.splits.add(new Split(j.getInt("km"), j.getLong("cumMs"),
                        j.getLong("splitMs"), j.optInt("cad", 0)));
            }
        }
        JSONArray rt = o.optJSONArray("route");
        if (rt != null) {
            r.route = new double[rt.length()];
            for (int i = 0; i < rt.length(); i++) r.route[i] = rt.getDouble(i);
        }
        return r;
    }
}
