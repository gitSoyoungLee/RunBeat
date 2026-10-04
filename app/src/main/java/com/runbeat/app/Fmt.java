package com.runbeat.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 화면 표시용, 음성 안내용 문자열을 만드는 도우미 모음 */
public final class Fmt {
    private Fmt() {}

    /** 3723000ms -> "1:02:03", 185000ms -> "03:05" */
    public static String duration(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /** 370초/km -> 6'10" */
    public static String pace(double secPerKm) {
        if (Double.isNaN(secPerKm) || secPerKm <= 0 || secPerKm >= 3600) return "-'--\"";
        int s = (int) Math.round(secPerKm);
        return String.format(Locale.US, "%d'%02d\"", s / 60, s % 60);
    }

    /** 코스 요약용: 300초 -> "5분", 90초 -> "1분 30초", 45초 -> "45초" */
    public static String shortDuration(int sec) {
        int m = sec / 60, s = sec % 60;
        if (m == 0) return s + "초";
        return s == 0 ? m + "분" : m + "분 " + s + "초";
    }

    public static String km(double meters) {
        return String.format(Locale.US, "%.2f", meters / 1000.0);
    }

    /** 음성용: "1시간 2분 3초" */
    public static String speakDuration(long ms) {
        long s = Math.round(ms / 1000.0);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("시간 ");
        if (m > 0) sb.append(m).append("분 ");
        if (sec > 0 || (h == 0 && m == 0)) sb.append(sec).append("초");
        return sb.toString().trim();
    }

    /** 음성용: "6분 10초" */
    public static String speakPace(double secPerKm) {
        int s = (int) Math.round(secPerKm);
        int m = s / 60, r = s % 60;
        return r == 0 ? m + "분" : m + "분 " + r + "초";
    }

    public static String speakKm(double meters) {
        return String.format(Locale.US, "%.2f킬로미터", meters / 1000.0);
    }

    public static String date(long millis) {
        return new SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.KOREAN).format(new Date(millis));
    }
}
