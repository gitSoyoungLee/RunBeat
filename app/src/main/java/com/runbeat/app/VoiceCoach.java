package com.runbeat.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 음성 안내 (런데이·삼성헬스처럼 음악 위에서 말하기).
 *
 * 말하기 직전에 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 로 오디오 포커스를 잠깐 빌린다.
 *   → "잠깐 말할게, 음악은 끄지 말고 소리만 줄여줘" 라는 뜻.
 *   → 유튜브뮤직 소리가 작아졌다가, 말이 끝나고 포커스를 반납하면 원래대로 돌아온다.
 */
public class VoiceCoach implements TextToSpeech.OnInitListener {
    private final TextToSpeech tts;
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final AtomicInteger speaking = new AtomicInteger(0);
    private final List<String> pending = new ArrayList<>();
    private volatile boolean ready;
    private boolean shutdown;
    private int utteranceSeq;

    public VoiceCoach(Context ctx) {
        audioManager = ctx.getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        tts = new TextToSpeech(ctx.getApplicationContext(), this);
        tts.setAudioAttributes(attrs);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { finishOne(); }
            @Override public void onError(String id) { finishOne(); }
        });
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        tts.setLanguage(Locale.KOREAN);
        tts.setSpeechRate(1.05f);
        synchronized (this) {
            ready = true;
            for (String s : pending) doSpeak(s);
            pending.clear();
        }
    }

    public synchronized void speak(String text) {
        if (shutdown) return;
        if (!ready) {
            pending.add(text);
            return;
        }
        doSpeak(text);
    }

    private void doSpeak(String text) {
        if (speaking.getAndIncrement() == 0) {
            audioManager.requestAudioFocus(focusRequest);
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "u" + (utteranceSeq++));
    }

    private void finishOne() {
        if (speaking.decrementAndGet() <= 0) {
            speaking.set(0);
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    public synchronized void shutdown() {
        if (shutdown) return;
        shutdown = true;
        tts.stop();
        tts.shutdown();
        audioManager.abandonAudioFocusRequest(focusRequest);
    }
}
