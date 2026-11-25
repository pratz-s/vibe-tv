package tv.home.app;
import android.os.*; import java.io.*; import java.util.*; import java.util.concurrent.*; import okhttp3.*; import org.json.*;
/** Talks to the engine. Callbacks arrive on the main thread. */
final class Api {
  static final String BASE=BuildConfig.API_BASE;
  private static final OkHttpClient C=new OkHttpClient.Builder().readTimeout(60,TimeUnit.SECONDS).build();
  private static final Handler H=new Handler(Looper.getMainLooper()); private static final ExecutorService E=Executors.newCachedThreadPool();
  interface Done<T>{ void ok(T v); void fail(String why); }
  interface Ask{ void heart(String words); void line(String words); void reply(String words); void pick(Title t); void picks(List<Title> t); void fail(String why); }
  static final class Row{ String name,form,section,note,why; List<Title> items=new ArrayList<>(); }
  private static Request.Builder req(String p){ return new Request.Builder().url(BASE+p).header("authorization","Bearer "+BuildConfig.APP_TOKEN); }
  static String feedRaw() throws IOException { try(Response r=C.newCall(req("/feed").build()).execute()){ if(!r.isSuccessful()) throw new IOException("feed "+r.code()); return r.body().string(); } }
  /** Who's watching, from the engine: "Just me" (the default) or "With partner" / "With friends" / "Guest". */
  static volatile String viewing = "me", viewingLabel = "Just me";
  static List<Row> parseFeed(String s) throws JSONException { JSONObject root=new JSONObject(s); JSONObject v=root.optJSONObject("viewing"); if(v!=null){ viewing=v.optString("mode","me"); viewingLabel=v.optString("label","Just me"); }
    List<Row> out=new ArrayList<>(); JSONArray a=root.getJSONArray("rows"); for(int i=0;i<a.length();i++){ JSONObject o=a.getJSONObject(i); Row r=new Row(); r.name=o.getString("name"); r.form=o.optString("form","wide"); r.section=o.isNull("section")?null:o.optString("section"); r.note=o.isNull("note")?null:o.optString("note"); r.why=o.isNull("why")?null:o.optString("why",null); JSONArray it=o.getJSONArray("items"); for(int j=0;j<it.length();j++) r.items.add(Title.from(it.getJSONObject(j))); if(!r.items.isEmpty()) out.add(r); } return out; }
  static void feed(Done<String> d){ E.execute(()->{ try{ String s=feedRaw(); H.post(()->d.ok(s)); }catch(Exception e){ H.post(()->d.fail(String.valueOf(e.getMessage()))); } }); }
  static void event(String id,String kind){ E.execute(()->{ try{ JSONObject b=new JSONObject().put("id",id).put("kind",kind); C.newCall(req("/event").post(RequestBody.create(b.toString(),MediaType.get("application/json"))).build()).execute().close(); }catch(Exception ignored){} }); }
  /** Spotify through the engine: connection status, a sign-in link, the record crate, and putting a record on. */
  private static final OkHttpClient LONG=new OkHttpClient.Builder().readTimeout(40,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
  /** Blocking GET for background threads (the remote's long wait). */
  static String getSync(String path) throws IOException { try(Response r=LONG.newCall(req(path).build()).execute()){ if(!r.isSuccessful()) throw new IOException(path+" "+r.code()); return r.body().string(); } }
  static void get(String path,Done<String> d){ E.execute(()->{ try(Response r=C.newCall(req(path).build()).execute()){ String b=r.body().string(); if(!r.isSuccessful()) throw new IOException(path+" "+r.code()); H.post(()->d.ok(b)); }catch(Exception e){ H.post(()->d.fail(String.valueOf(e.getMessage()))); } }); }
  static void post(String path,String json,Done<String> d){ E.execute(()->{ try(Response r=C.newCall(req(path).post(RequestBody.create(json,MediaType.get("application/json"))).build()).execute()){ String b=r.body().string(); if(!r.isSuccessful()) throw new IOException(path+" "+r.code()); H.post(()->d.ok(b)); }catch(Exception e){ H.post(()->d.fail(String.valueOf(e.getMessage()))); } }); }
  /** Today's weather for the TV's area, worked out by the engine. */
  static void weather(Done<String> d){ E.execute(()->{ try(Response r=C.newCall(req("/weather").build()).execute()){ String b=r.body().string(); if(!r.isSuccessful()) throw new IOException("weather "+r.code()); H.post(()->d.ok(b)); }catch(Exception e){ H.post(()->d.fail(String.valueOf(e.getMessage()))); } }); }
  /** Sends a recording to the engine, which returns what was said. */
  static void transcribe(byte[] wav,Done<String> d){ E.execute(()->{ try(Response r=C.newCall(req("/transcribe").post(RequestBody.create(wav,MediaType.get("audio/wav"))).build()).execute()){ String b=r.body().string(); if(!r.isSuccessful()) throw new IOException("transcribe "+r.code()+" "+b); String t=new JSONObject(b).optString("text",""); H.post(()->d.ok(t)); }catch(Exception e){ H.post(()->d.fail(String.valueOf(e.getMessage()))); } }); }
  static void ask(String text,Ask l){ E.execute(()->{ try{ JSONObject b=new JSONObject().put("text",text);
      try(Response r=C.newCall(req("/ask").post(RequestBody.create(b.toString(),MediaType.get("application/json"))).build()).execute()){ if(!r.isSuccessful()) throw new IOException("ask "+r.code()); BufferedReader in=new BufferedReader(new InputStreamReader(r.body().byteStream())); String line;
        while((line=in.readLine())!=null){ JSONObject o=new JSONObject(line); String st=o.optString("stage");
          if("heart".equals(st)){ String m=o.optString("heart"); H.post(()->l.heart(m)); }
          else if("line".equals(st)){ String m=o.optString("line"); H.post(()->l.line(m)); }
          else if("reply".equals(st)){ String m=o.optString("reply"); H.post(()->l.reply(m)); }
          else if("pick".equals(st)){ Title t=Title.from(o.getJSONObject("pick")); H.post(()->l.pick(t)); }
          else if("picks".equals(st)){ List<Title> t=new ArrayList<>(); JSONArray a=o.getJSONArray("picks"); for(int i=0;i<a.length();i++) t.add(Title.from(a.getJSONObject(i))); H.post(()->l.picks(t)); }
          else if("viewing".equals(st)){ viewing=o.optString("mode","me"); viewingLabel=o.optString("label","Just me"); }
          else if("error".equals(st)){ String m=o.optString("message"); H.post(()->l.fail(m)); } } }
    }catch(Exception e){ H.post(()->l.fail(String.valueOf(e.getMessage()))); } }); }
}
