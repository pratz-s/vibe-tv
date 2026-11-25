package tv.home.app;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.*;
import org.json.JSONObject;

/**
 * Notices what plays on the TV and tells the engine, so taste learns from what was actually watched and heard.
 * Video apps: title, how far in, how long it is, every half minute while it plays. Some apps (Prime Video) don't say what is
 * playing, so when a video app is in front, the remote is idle and no player has reported, the time counts towards the title
 * that was last opened from our screen.
 * Music apps: one "play" per track heard for at least 30 seconds, with artist, album and the playlist it came from, so repeats
 * and favourite playlists show up. YouTube is left out.
 */
final class Watcher {
  private static final Set<String> SKIP = new HashSet<>(Arrays.asList("com.google.android.youtube.tv", "tv.home.app"));
  private static final Set<String> MUSIC = new HashSet<>(Arrays.asList("com.spotify.tv.android", "com.google.android.youtube.tvmusic"));
  static final Set<String> VIDEO = new HashSet<>(Arrays.asList("com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.netflix.ninja", "com.stremio.one", "com.sonyliv", "com.zee5.androidtv"));
  private static final long EVERY = 30000, MIN_PLAY = 30000;
  static volatile String openedId, openedTitle; static volatile long openedAt;
  /** When a video player last reported playing, so the foreground fallback never double counts. */
  static volatile long videoPlayingAt;
  /** Called when a title is opened from our screen, so the next thing that plays can be matched to it exactly. */
  static void opened(String id, String title) { openedId = id; openedTitle = title; openedAt = SystemClock.elapsedRealtime(); }
  private static final Map<String, Object[]> live = new HashMap<>();   // pkg -> {title, show, PendingIntent to the player, when}
  /** Continue watching: if that app still has the player open for this title, go straight back into it. */
  static boolean resume(String pkg, String title) {
    Object[] v; synchronized (live) { v = live.get(pkg); } if (v == null || v[2] == null || SystemClock.elapsedRealtime() - (long) v[3] > 6 * 3600_000L) return false;
    String want = norm(title), a = norm((String) v[0]), b = norm((String) v[1]);
    if (!want.isEmpty() && !(want.equals(a) || want.equals(b) || (!a.isEmpty() && (a.contains(want) || want.contains(a))) || (!b.isEmpty() && (b.contains(want) || want.contains(b))))) return false;
    try { ((android.app.PendingIntent) v[2]).send(); Log.i(HomeService.T, "resumed " + pkg + " '" + title + "' in its player"); return true; } catch (Exception e) { Log.w(HomeService.T, "resume: " + e); return false; }
  }
  private static String norm(String s) { return s == null ? "" : s.toLowerCase(Locale.US).replaceAll("^s\\d+\\s*e\\d+\\s*", "").replaceAll("[^a-z0-9]+", " ").trim(); }
  static boolean openedRecently() { return openedId != null && SystemClock.elapsedRealtime() - openedAt < 6 * 3600_000L; }

  private final Context c; private final Handler h = new Handler(Looper.getMainLooper()); private final Map<MediaController, Track> tracks = new HashMap<>();
  private static final class Track { String title, show, artist, album, queue; long lastTick, played, lastPos; boolean playing; MediaController.Callback cb; }

  Watcher(Context c) { this.c = c; }
  void start() {
    try { MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE); ComponentName me = new ComponentName(c, Media.class); m.addOnActiveSessionsChangedListener(this::sessions, me, h); sessions(m.getActiveSessions(me)); h.postDelayed(tick, EVERY); Log.i(HomeService.T, "watcher on"); }
    catch (Exception e) { Log.w(HomeService.T, "watcher: " + e); }
  }
  private static boolean music(MediaController mc) { return MUSIC.contains(mc.getPackageName()); }
  private void sessions(List<MediaController> list) {
    Set<MediaController> now = new HashSet<>(); if (list != null) for (MediaController mc : list) if (!SKIP.contains(mc.getPackageName())) now.add(mc);
    for (Iterator<Map.Entry<MediaController, Track>> it = tracks.entrySet().iterator(); it.hasNext(); ) { Map.Entry<MediaController, Track> e = it.next(); if (!now.contains(e.getKey())) { end(e.getKey(), e.getValue()); e.getKey().unregisterCallback(e.getValue().cb); it.remove(); } }
    for (MediaController mc : now) if (!tracks.containsKey(mc)) { Track t = new Track(); t.cb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { changed(mc); } @Override public void onMetadataChanged(MediaMetadata md) { changed(mc); } };
      tracks.put(mc, t); mc.registerCallback(t.cb, h); changed(mc); }
  }
  private void changed(MediaController mc) {
    Track t = tracks.get(mc); if (t == null) return; MediaMetadata md = mc.getMetadata(); PlaybackState s = mc.getPlaybackState();
    String title = md == null ? null : first(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), md.getString(MediaMetadata.METADATA_KEY_TITLE));
    String show = md == null ? null : first(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE), md.getString(MediaMetadata.METADATA_KEY_ALBUM), md.getString(MediaMetadata.METADATA_KEY_ARTIST));
    boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING; long now = SystemClock.elapsedRealtime(), pos = s == null ? 0 : s.getPosition();
    if (music(mc)) {
      if (t.playing) { t.played += now - t.lastTick; t.lastTick = now; }
      boolean newTrack = t.title != null && !Objects.equals(t.title, title), restarted = Objects.equals(t.title, title) && t.lastPos > 30000 && pos < 5000;
      if (newTrack || restarted) end(mc, t);
      t.title = title; t.artist = md == null ? null : first(md.getString(MediaMetadata.METADATA_KEY_ARTIST), md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST));
      t.album = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ALBUM); CharSequence q = mc.getQueueTitle(); t.queue = q == null ? null : q.toString();
      if (!t.playing && playing) t.lastTick = now; t.playing = playing; t.lastPos = pos; return;
    }
    if (t.title != null && !Objects.equals(t.title, title)) report(mc, t, true);
    if (t.playing && !playing) report(mc, t, true);
    if (!t.playing && playing) t.lastTick = now;
    t.title = title; t.show = show; t.playing = playing;
  }
  private final Runnable tick = new Runnable() { public void run() {
    long now = SystemClock.elapsedRealtime();
    for (Map.Entry<MediaController, Track> e : tracks.entrySet()) { Track t = e.getValue(); if (!t.playing) continue;
      if (music(e.getKey())) { t.played += now - t.lastTick; t.lastTick = now; PlaybackState s = e.getKey().getPlaybackState(); if (s != null) t.lastPos = s.getPosition(); }
      else report(e.getKey(), t, false); }
    h.postDelayed(this, EVERY); } };
  /** A music track finished, was skipped or restarted: count it as one play if it was heard for long enough. */
  private void end(MediaController mc, Track t) {
    if (!music(mc)) { report(mc, t, true); return; }
    long played = t.played; t.played = 0; if (t.title == null || played < MIN_PLAY) return;
    MediaMetadata md = mc.getMetadata(); long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
    try { JSONObject o = new JSONObject().put("pkg", mc.getPackageName()).put("title", t.title).put("artist", t.artist).put("album", t.album).put("queue", t.queue).put("played", played).put("dur", dur);
      Api.post("/listen", o.toString(), new Api.Done<String>() { public void ok(String b) {} public void fail(String w) { Log.w(HomeService.T, "listen: " + w); } });
      Log.i(HomeService.T, "heard '" + t.title + "' by " + t.artist + " from '" + t.queue + "' " + played / 1000 + "s");
    } catch (Exception e) { Log.w(HomeService.T, "listen: " + e); }
  }
  private void report(MediaController mc, Track t, boolean last) {
    long now = SystemClock.elapsedRealtime(), delta = t.playing && t.lastTick > 0 ? now - t.lastTick : 0; t.lastTick = now;
    String title = t.title, show = t.show;
    // Prime Video and others sometimes play without saying what: use the title they opened from our screen
    if (title == null && show == null) { if (!openedRecently()) return; show = openedTitle; }
    if (t.playing) videoPlayingAt = now;
    PlaybackState s = mc.getPlaybackState(); MediaMetadata md = mc.getMetadata(); long pos = s == null ? 0 : s.getPosition(); if (s != null && s.getState() == PlaybackState.STATE_PLAYING) pos += (long) ((now - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
    long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION);
    synchronized (live) { live.put(mc.getPackageName(), new Object[]{title, show, mc.getSessionActivity(), now}); }
    try { JSONObject o = new JSONObject().put("pkg", mc.getPackageName()).put("title", title).put("show", show).put("pos", pos).put("dur", dur).put("delta", delta);
      if (md != null && last) { JSONObject meta = new JSONObject(); for (String k : md.keySet()) { CharSequence v = md.getText(k); if (v != null && v.length() > 0 && v.length() < 300) meta.put(k.replace("android.media.metadata.", ""), v.toString()); } if (meta.length() > 0) o.put("meta", meta); }
      if (openedRecently()) o.put("id", openedId);
      Api.post("/watch", o.toString(), new Api.Done<String>() { public void ok(String b) {} public void fail(String w) { Log.w(HomeService.T, "watch: " + w); } });
      if (last || delta > 0) Log.i(HomeService.T, "watching " + mc.getPackageName() + " '" + show + " / " + title + "' " + pos / 1000 + "/" + dur / 1000 + "s +" + delta / 1000);
    } catch (Exception e) { Log.w(HomeService.T, "watch: " + e); }
  }
  /** Foreground fallback, called every half minute by the service: a video app in front, the remote idle, no player reporting. */
  static void foreground(String pkg, long lastKeyAt) {
    long now = SystemClock.elapsedRealtime();
    if (pkg == null || !VIDEO.contains(pkg) || !openedRecently() || now - videoPlayingAt < 45000 || now - lastKeyAt < 45000) return;
    try { JSONObject o = new JSONObject().put("pkg", pkg).put("show", openedTitle).put("id", openedId).put("pos", 0).put("dur", 0).put("delta", EVERY).put("guess", true);
      Api.post("/watch", o.toString(), new Api.Done<String>() { public void ok(String b) {} public void fail(String w) { Log.w(HomeService.T, "watch: " + w); } });
      Log.i(HomeService.T, "watching (in front, remote idle) " + pkg + " '" + openedTitle + "' +30s");
    } catch (Exception e) { Log.w(HomeService.T, "watch: " + e); }
  }
  private static String first(String... v) { for (String x : v) if (x != null && !x.trim().isEmpty()) return x; return null; }
}
