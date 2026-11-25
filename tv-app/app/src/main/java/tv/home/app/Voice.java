package tv.home.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The TV's spoken voice: a greeting when it wakes, and its short replies. Indian English. */
final class Voice {
  private final Handler h = new Handler(Looper.getMainLooper()); private TextToSpeech tts; private boolean ready; private final Map<String, Runnable> after = new HashMap<>(); private int n;
  private String pending; private Runnable pendingDone;
  private final Context ctx;
  Voice(Context c) {
    ctx = c.getApplicationContext();
    tts = new TextToSpeech(c.getApplicationContext(), s -> {
      if (s != TextToSpeech.SUCCESS) { Log.w(HomeService.T, "voice: no text-to-speech"); return; }
      int r = tts.setLanguage(new Locale("en", "IN")); if (r < 0) tts.setLanguage(Locale.UK); try { for (android.speech.tts.Voice v : tts.getVoices()) if (v.getLocale().getLanguage().equals("en")) Log.i(HomeService.T, "tts voice " + v.getName() + " " + v.getLocale() + " q" + v.getQuality() + (v.isNetworkConnectionRequired() ? " net" : "") + " " + v.getFeatures()); } catch (Exception ignored) {}
      pickVoice(); tts.setSpeechRate(0.95f); tts.setPitch(1.0f); ready = true;
      tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
        public void onStart(String id) {}
        public void onDone(String id) { Runnable a = after.remove(id); if (a != null) h.post(a); }
        public void onError(String id) { onDone(id); }
        @Override public void onStop(String id, boolean interrupted) { after.remove(id); }
      });
      if (pending != null) { String p = pending; Runnable d = pendingDone; pending = null; pendingDone = null; say(p, d); }
    });
  }
  /** Speaks, then runs done (if any) once finished. Speaking again cuts off what's being said. */
  void say(String text, Runnable done) {
    if (text == null || text.trim().isEmpty()) { if (done != null) done.run(); return; }
    String clip = clipFor(text); if (clip != null && playClip("voice/b_" + clip + ".ogg", done)) return; if (clip != null) text = BUTLER.get(clip);
    if (!ready) { pending = text; pendingDone = done; h.postDelayed(() -> { if (pending != null && !ready) { pending = null; Runnable d = pendingDone; pendingDone = null; if (d != null) d.run(); } }, 4000); return; }
    String id = "u" + (++n); if (done != null) after.put(id, done); tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
  }
  /** A natural female Indian English voice: Google's online voices sound far less robotic than the on-device ones. */
  private void pickVoice() {
    try { java.util.Set<android.speech.tts.Voice> all = tts.getVoices(); if (all == null) return;
      String chosen = ctx.getSharedPreferences(HomeService.PREFS, 0).getString("voice", "en-gb-x-rjs-network");
      for (String want : new String[]{chosen, chosen.replace("-network", "-local"), "en-in-x-ena-network"})
        for (android.speech.tts.Voice v : all) if (v.getName().equals(want) && !v.getFeatures().contains("notInstalled")) { tts.setVoice(v); Log.i(HomeService.T, "voice: " + want); return; }
    } catch (Exception e) { Log.w(HomeService.T, "voice: " + e); }
  }
  /** Plays each Indian English voice in turn so the person can choose one by ear. */
  /** British male voices closest to the recorded butler (instant, no generation), then the previous voice. Numbering stays fixed even if one is missing. */
  static final String[] CHOICES = {"en-gb-x-rjs-network", "en-gb-x-gbd-network", "en-gb-x-rjs-local", "en-gb-x-gbd-local", "en-in-x-enc-network"};
  void samples() { if (!ready) return; tts.stop(); try { java.util.Set<android.speech.tts.Voice> all = tts.getVoices(); int i = 0;
    for (String name : CHOICES) { i++; for (android.speech.tts.Voice v : all) if (v.getName().equals(name)) { tts.setVoice(v); Bundle p = new Bundle(); tts.speak("Voice " + i + ". Very good. Here is what I found for you tonight.", i == 1 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, p, "s" + i); tts.playSilentUtterance(700, TextToSpeech.QUEUE_ADD, "g" + i); } }
    } catch (Exception e) { Log.w(HomeService.T, "samples: " + e); } h.postDelayed(this::pickVoice, 100); }
  /** Saves the chosen voice (1 to 5). */
  void choose(int n) { if (n < 1 || n > CHOICES.length) return; ctx.getSharedPreferences(HomeService.PREFS, 0).edit().putString("voice", CHOICES[n - 1]).apply(); pickVoice(); say("Okay. I'll speak like this from now on.", null); }
  private boolean playClip(String asset, Runnable done) {
    try { android.content.res.AssetFileDescriptor f = ctx.getAssets().openFd(asset); android.media.MediaPlayer mp = new android.media.MediaPlayer();
      mp.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength()); f.close();
      mp.setOnCompletionListener(m -> { m.release(); if (done != null) h.post(done); }); mp.setOnErrorListener((m, a, b) -> { m.release(); if (done != null) h.post(done); return true; });
      if (ready) tts.stop(); mp.prepare(); mp.start(); return true; } catch (Exception e) { return false; } }
  void quiet() { after.clear(); pending = null; pendingDone = null; if (ready) tts.stop(); }
  boolean speaking() { return ready && tts.isSpeaking(); }
  // ---- the butler: witty fixed lines recorded into the app (instant, offline); live replies stay on the text-to-speech voice ----
  static final Map<String, String> BUTLER = new HashMap<>();
  static { String[][] l = {{"welcome", "Welcome back. Shall I find you something worth watching, or are we scrolling for sport tonight?"}, {"paused", "Paused. Take your time, the plot will wait."},
    {"playing", "Resuming. Do try to keep up."}, {"next", "Next one. Onwards and upwards."}, {"previous", "Rewinding. Missed something, did we?"}, {"louder", "Louder. The neighbours will be thrilled."},
    {"quieter", "Quieter. Very civilised."}, {"muted", "Muted. Blissful silence."}, {"soundon", "Sound restored. Back to the noise."}, {"home", "Home it is."},
    {"open_prime", "Prime Video. An excellent choice."}, {"open_hotstar", "JioHotstar, coming right up."}, {"open_youtube", "YouTube. Just one video, I'm sure."}, {"open_netflix", "Netflix. See you in six episodes."},
    {"open_spotify", "Spotify. Shall we set the mood?"}, {"open_stremio", "Stremio. Opening the vault."}, {"weather_off", "I'm afraid the weather is keeping its secrets just now."}};
    for (String[] x : l) BUTLER.put(x[0], x[1]); }
  /** Which recorded butler line answers a command's plain reply ("Paused", "Opening Netflix"...), or null. */
  static String clipFor(String text) {
    switch (text) { case "Paused": return "paused"; case "Playing": return "playing"; case "Next": return "next"; case "Previous": return "previous"; case "Louder": return "louder"; case "Quieter": return "quieter";
      case "Muted": return "muted"; case "Sound on": return "soundon"; case "Home": return "home"; case "I couldn't get the weather right now.": return "weather_off"; }
    if (text.startsWith("Opening ")) { String a = text.toLowerCase(Locale.US); if (a.contains("prime")) return "open_prime"; if (a.contains("hotstar")) return "open_hotstar"; if (a.contains("youtube")) return "open_youtube";
      if (a.contains("netflix")) return "open_netflix"; if (a.contains("spotify")) return "open_spotify"; if (a.contains("stremio")) return "open_stremio"; }
    return null; }
  static String greeting() { return BUTLER.get("welcome"); }
  /** A short, plain butler line when search results appear (never commentary on what was asked). */
  private static final String[] RESULT_LINES = {"Here's what I found.", "Right then. Your results.", "Here you are."};
  static String resultLine() { return RESULT_LINES[(int) (Math.random() * RESULT_LINES.length)]; }
  /** Plays the recorded greeting (no network, no delay); falls back to the normal voice if it can't. */
  void greet(Runnable done) {
    try { android.content.res.AssetFileDescriptor f = ctx.getAssets().openFd("voice/b_welcome.ogg"); android.media.MediaPlayer mp = new android.media.MediaPlayer();
      mp.setDataSource(f.getFileDescriptor(), f.getStartOffset(), f.getLength()); f.close();
      mp.setOnCompletionListener(m -> { m.release(); if (done != null) h.post(done); }); mp.setOnErrorListener((m, a, b) -> { m.release(); say(greeting(), done); return true; });
      mp.prepare(); mp.start();
    } catch (Exception e) { Log.w(HomeService.T, "greet: " + e); say(greeting(), done); } }
  void release() { if (tts != null) tts.shutdown(); }
}
