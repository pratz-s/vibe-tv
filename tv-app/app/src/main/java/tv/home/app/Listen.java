package tv.home.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import java.util.ArrayList;

/**
 * Our own voice search through the remote's mic, using Google's speech service (the only one the TV routes the remote's mic to).
 * It runs one continuous ("segmented") session so pauses don't end it; it ends when the person presses OK, stays quiet
 * for a few seconds, or talks for a long time. If the service doesn't support continuous sessions, it falls back to
 * restarting after each pause and joining the pieces. Indian English, with Hindi allowed.
 */
final class Listen {
  interface Ear { void hearing(String soFar); void heard(String text); void missed(String why); default void level(float rmsDb) {} }
  private static final long QUIET_MS = 5000, MAX_MS = 45000; long waitMs = 10000;
  private final Context c; private final Handler h = new Handler(Looper.getMainLooper());
  // the remote's mic sometimes doesn't open on the first request (its red light stays off): if the speech service isn't
  // ready soon after we ask, or fails straight away, quietly ask again, up to twice, before telling the person
  private int tries; private long askedAt, readyAt; private static final int RETRIES = 2; private static final long READY_MS = 1600;
  private SpeechRecognizer sr; private boolean on; private Ear ear; private final StringBuilder kept = new StringBuilder(); private String partial = ""; private long startedAt, lastSpeech;
  Listen(Context c) { this.c = c; }
  boolean active() { return on; }

  void start(Ear e) {
    stop(); ear = e; kept.setLength(0); partial = "";
    if (!SpeechRecognizer.isRecognitionAvailable(c)) { e.missed("Voice search isn't available on this TV."); return; }
    on = true; tries = 0; startedAt = lastSpeech = System.currentTimeMillis(); session(); watch();
  }
  /** OK pressed, or quiet long enough: search with what we have. */
  void done() { if (!on) return; String t = text(); end(); Log.i(HomeService.T, "listen done: " + t); if (t.isEmpty()) ear.missed("I didn't catch that. Try again."); else ear.heard(t); }
  void stop() { end(); }

  private String text() { String p = partial.trim(); return (kept + (kept.length() > 0 && !p.isEmpty() ? " " : "") + p).trim(); }
  private void end() { on = false; h.removeCallbacksAndMessages(null); if (sr != null) { try { sr.cancel(); } catch (Exception ignored) {} sr.destroy(); sr = null; } }

  /** Our own clock decides when they've finished, since the service's silence settings aren't reliable. */
  private void watch() { h.postDelayed(new Runnable() { public void run() { if (!on) return; long now = System.currentTimeMillis();
    if (text().isEmpty() && now - startedAt > waitMs) { end(); ear.missed("I didn't hear anything. Try again."); return; }
    if (!text().isEmpty() && now - lastSpeech > QUIET_MS) { done(); return; }
    if (now - startedAt > MAX_MS) { done(); return; }
    h.postDelayed(this, 250); } }, 250); }

  private boolean early() { return text().isEmpty() && System.currentTimeMillis() - askedAt < 2500; }
  /** Throw the recognizer away and ask again, a moment later, so the remote opens its mic on the fresh request. */
  private void retry(String why) { tries++; Log.i(HomeService.T, "listen: mic not open (" + why + "), asking again " + tries + "/" + RETRIES);
    if (sr != null) { try { sr.cancel(); } catch (Exception ignored) {} sr.destroy(); sr = null; } startedAt = lastSpeech = System.currentTimeMillis(); h.postDelayed(this::session, 300); }
  private void session() {
    if (!on) return;
    if (sr != null) { sr.destroy(); sr = null; }
    askedAt = System.currentTimeMillis(); readyAt = 0;
    h.postDelayed(() -> { if (on && readyAt == 0 && text().isEmpty() && tries < RETRIES) retry("not ready"); }, READY_MS);
    sr = SpeechRecognizer.createSpeechRecognizer(c);
    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
      .putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"hi-IN"})
      .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
      .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
      // One continuous session that survives pauses; it reports each stretch of speech as a segment.
      .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
      .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MAX_MS)
      .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, QUIET_MS)
      .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, QUIET_MS)
      .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
    sr.setRecognitionListener(new RecognitionListener() {
      private String best(Bundle b) { ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); return r == null || r.isEmpty() ? "" : r.get(0); }
      public void onReadyForSpeech(Bundle b) { readyAt = System.currentTimeMillis(); Log.i(HomeService.T, "listen: ready after " + (readyAt - askedAt) + "ms"); }
      public void onBeginningOfSpeech() { lastSpeech = System.currentTimeMillis(); }
      public void onRmsChanged(float v) { if (v > 6) lastSpeech = System.currentTimeMillis(); if (on) ear.level(v); }
      public void onBufferReceived(byte[] b) {}
      public void onEndOfSpeech() {}
      public void onEvent(int t, Bundle b) {}
      public void onPartialResults(Bundle b) { if (!on) return; String t = best(b); if (!t.isEmpty()) { partial = t; lastSpeech = System.currentTimeMillis(); ear.hearing(text()); } }
      public void onSegmentResults(Bundle b) { if (!on) return; Log.i(HomeService.T, "listen segment"); keep(best(b)); }
      public void onEndOfSegmentedSession() { if (!on) return; Log.i(HomeService.T, "listen segmented session ended"); keep(partial); done(); }
      public void onResults(Bundle b) { if (!on) return; String t = best(b); if (t.isEmpty()) t = partial; keep(t); restart(); }
      public void onError(int e) { if (!on) return; Log.i(HomeService.T, "listen session ended " + e);
        if (early() && tries < RETRIES && (e == SpeechRecognizer.ERROR_CLIENT || e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || e == SpeechRecognizer.ERROR_AUDIO)) { retry("error " + e); return; }
        keep(partial);
        if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { end(); ear.missed("Allow the microphone for this app in Settings."); return; }
        if (e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || e == SpeechRecognizer.ERROR_SERVER) { if (!text().isEmpty()) done(); else { end(); ear.missed("Voice search needs the internet. Try again."); } return; }
        restart(); }
    });
    sr.startListening(i);
  }
  private void keep(String t) { t = t == null ? "" : t.trim(); partial = ""; if (t.isEmpty()) return; if (kept.length() > 0) kept.append(' '); kept.append(t); lastSpeech = System.currentTimeMillis(); ear.hearing(text()); }
  /** Fallback when the service ends a plain session at a pause: start again straight away and keep the words. */
  private void restart() { if (!on) return; Log.i(HomeService.T, "listen restarting (no continuous mode)"); h.post(this::session); }
}
