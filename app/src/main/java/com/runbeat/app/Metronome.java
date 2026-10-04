package com.runbeat.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

/**
 * 케이던스 메트로놈.
 *
 * 핵심 1) 오디오 포커스를 요청하지 않는다.
 *   → 유튜브뮤직 같은 음악 앱이 멈추지 않고, "딱" 소리가 음악 위에 섞여서 들린다.
 *
 * 핵심 2) 박자 오차가 쌓이지 않게 "다음에 울릴 절대 시각"을 기준으로 기다린다.
 *   Thread.sleep(353)을 반복하면 매번 몇 ms씩 밀려서 박자가 점점 늦어진다.
 *   대신 next += interval 로 목표 시각을 계속 더해가면 오차가 누적되지 않는다.
 */
public class Metronome {
    private static final String TAG = "Metronome";

    private final SoundPool pool;
    private int soundId;
    private volatile boolean loaded;
    private volatile boolean running;
    private volatile int bpm;
    private Thread thread;

    public Metronome(Context ctx) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((p, id, status) -> loaded = (status == 0));
        try {
            File f = writeClickWav(ctx);
            soundId = pool.load(f.getAbsolutePath(), 1);
        } catch (IOException e) {
            Log.e(TAG, "click sound failed", e);
        }
    }

    public void setBpm(int bpm) {
        this.bpm = bpm;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "metronome");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            LockSupport.unpark(thread);
            thread = null;
        }
        pool.release();
    }

    private void loop() {
        long next = System.nanoTime();
        int lastBpm = -1;
        while (running) {
            int b = bpm;
            if (b <= 0) {                       // 메트로놈 꺼짐 → 잠깐 쉬었다가 다시 확인
                LockSupport.parkNanos(200_000_000L);
                lastBpm = -1;
                continue;
            }
            long interval = 60_000_000_000L / b; // 한 박자 길이 (나노초)
            if (b != lastBpm) {                  // bpm이 바뀌면 지금부터 새로 시작
                next = System.nanoTime();
                lastBpm = b;
            }
            long wait;
            while (running && (wait = next - System.nanoTime()) > 0) {
                LockSupport.parkNanos(wait);
            }
            if (!running) break;
            if (loaded && bpm > 0) {
                pool.play(soundId, 1f, 1f, 1, 0, 1f);
            }
            next += interval;
            // 시스템이 바빠서 한참 밀렸다면 따라잡으려고 연타하지 말고 기준을 다시 잡는다.
            if (System.nanoTime() - next > interval) next = System.nanoTime();
        }
    }

    /** "딱" 소리 WAV 파일을 코드로 만들어서 저장한다. (리소스 파일 불필요) */
    private static File writeClickWav(Context c) throws IOException {
        int sampleRate = 44100;
        int n = sampleRate * 35 / 1000; // 35ms
        ByteBuffer bb = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        bb.putInt(36 + n * 2);
        bb.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        bb.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        bb.putInt(16);
        bb.putShort((short) 1);              // PCM
        bb.putShort((short) 1);              // mono
        bb.putInt(sampleRate);
        bb.putInt(sampleRate * 2);
        bb.putShort((short) 2);
        bb.putShort((short) 16);
        bb.put("data".getBytes(StandardCharsets.US_ASCII));
        bb.putInt(n * 2);
        for (int i = 0; i < n; i++) {
            double t = i / (double) sampleRate;
            double env = Math.exp(-t * 120);  // 빠르게 사라지는 소리
            double s = (Math.sin(2 * Math.PI * 1800 * t) + 0.4 * Math.sin(2 * Math.PI * 3600 * t)) * env * 0.65;
            s = Math.max(-1, Math.min(1, s));
            bb.putShort((short) (s * 32767));
        }
        File f = new File(c.getCacheDir(), "click.wav");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bb.array());
        }
        return f;
    }
}
