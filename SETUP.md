# Setup, for Claude Code

You're setting up Vibe TV for someone who may not be technical. Go one step at a time, and explain each step in a sentence before you do it. Ask one question at a time and wait for the answer.

**Rules for the whole setup:**
- Never ask for a key in the chat, and never print one. The person pastes keys into `.env` themselves.
- Never commit `.env`, `taste.md`, `.private-words` or `worker/wrangler.jsonc`.
- Before anything is pushed to GitHub, run `scripts/check_secrets.sh`.

## 1. Check the tools

They need Node 20 or newer, Python 3, a JDK (17 or newer) and the Android SDK with platform tools. Android Studio brings the last two. Check what's there and offer to install what's missing. Set `ANDROID_HOME` if the SDK isn't in the default place.

Run `git config core.hooksPath .githooks`, so every commit is checked for keys and personal files first.

## 2. Collect the keys

Run `cp .env.example .env`. Then ask them to open `.env` themselves and paste in:

- **An AI key.** `GEMINI_API_KEY` is free from aistudio.google.com/apikey. `ANTHROPIC_API_KEY` from console.anthropic.com is paid per search. One is enough. Tell them plainly that on Gemini's free tier Google may use requests to improve its products. If that bothers them, the Anthropic key avoids it. If neither works, Cloudflare's own free model steps in, with weaker results.
- **`MOTN_KEY`**, from movieofthenight.com/about/api. This is how it knows what's streaming where.

Check the keys are filled in without printing them, for example with `grep -c '^MOTN_KEY=.\+' .env`.

## 3. Where they live

Ask which city they live in. Run `python3 scripts/set_city.py "<city>"`. It saves the city, its coordinates, the time zone and the country. The weather, "tonight" and "this weekend", the holiday calendar and the streaming catalogue all depend on it. If the city name is common, add the country: `"Cambridge, US"`.

## 4. What they watch on

Ask which streaming services they pay for, and which apps they use on the TV. Fill in:

- `SERVICES`, as the app knows them: `netflix`, `prime`, `hotstar`, `sonyliv`
- `CATALOGS`, as Movie of the Night names them, e.g. `netflix`, `prime.subscription`, `hotstar`
- `APPS`, as Android package names, in the order they want them on the TV:

| App | Package |
|---|---|
| Netflix | `com.netflix.ninja` |
| Prime Video | `com.amazon.amazonvideo.livingroom` |
| JioHotstar | `in.startv.hotstar` |
| YouTube | `com.google.android.youtube.tv` |
| Spotify | `com.spotify.tv.android` |
| Stremio | `com.stremio.one` |
| Disney+ | `com.disney.disneyplus` |
| Max | `com.wbd.stream` |
| Apple TV | `com.apple.atve.androidtv.appletv` |
| SonyLIV | `com.sonyliv` |
| ZEE5 | `com.zee5.androidtv` |

If a service they pay for isn't in `serviceName()` and `pkg()` in `tv-app/app/src/main/java/tv/home/app/Title.java`, add it there and to `NICE` in `worker/src/worker.js`. Titles on services they don't have open in Stremio, if they use it.

## 5. Their assistant

Ask what they'd like to call the assistant. The default is Jarvis. Save it as `ASSISTANT_NAME`. They can then say "Hey Jarvis, something funny", and the name is ignored in the request.

## 6. Learning, and their privacy

Explain in two sentences what the TV can learn by itself: which title plays, for how long, which app is open, and which songs play on Spotify or YouTube Music. Everything is stored only on their own Cloudflare account. Ask if they want it on.

- Yes: `LEARN=true` (the default)
- No: `LEARN=false`. It then learns only from what they say and choose.

Either way it learns from what they say ("loved it", "not for me", "save this for later") and from what they open.

## 7. Learn their taste

Have a short, friendly conversation, one question at a time:

1. Who watches, and when? Just them, a partner, family, friends? Weeknights or weekends?
2. What have they loved lately? What do they keep coming back to, and why?
3. What did they think was just OK? What did they not like at all?
4. What do they avoid? For example: horror, slow films, confusing plots, later seasons.
5. What's on their list to watch? What are they in the middle of right now?
6. Which languages do they watch in, and how do they talk to the TV? Do they mix languages?
7. **What have they already seen?** Go through big franchises one by one and ask which they've seen: Marvel, DC, Star Wars, Harry Potter, The Lord of the Rings, James Bond, Mission: Impossible, Fast & Furious, X-Men, Pixar, and any local favourites. Write out the actual titles.

Write the answers to `taste.md`, following `taste.example.md`. Keep its `##` headings exactly: the engine reads the lists under Loved, It was OK, Didn't like and Already seen. Read the file back to them and fix anything they want changed.

## 8. Set up the cloud

Run `scripts/setup_cloud.sh`. The first time, it opens a browser to log in to Cloudflare. It creates the database, the search index and three private keys, then deploys the engine. Then run `scripts/push_taste.sh`.

## 9. Fill the catalogue

Run `scripts/fill_catalog.sh`. Tell them first that it uses their Movie of the Night quota and takes a while. It's safe to run again if it stops.

## 10. Connect the TV

Walk them through it on the TV:

1. Settings, System, About, then press Android TV OS build seven times to turn on developer options
2. Settings, System, Developer options, then turn on USB debugging or Wireless debugging
3. Find the TV's IP address in the network settings

Put it in `.env` as `TV_IP` (with the port, if Wireless debugging shows one). Run `scripts/tv_install.sh`. The first time, they need to accept the prompt on the TV.

## 11. The voice button

Ask which remote button they want for voice. A button they don't use much works best, like ☰ Menu or a colour button. The mic button already works on the home screen.

Run `scripts/pick_voice_key.sh` and ask them to press the button once. Then run `scripts/tv_install.sh` again. If they choose the Google Assistant button, warn them that Google may still answer it inside other apps.

## 12. The phone remote (optional)

Run `scripts/remote_link.sh`. It shows a QR code for their private remote link. Ask them to scan it, then add the page to their home screen. It works on iPhone and Android. Tell them the link works like a password. If it gets out, `scripts/rotate_keys.sh` makes a new one.

## 13. Spotify (optional)

If they want to play music from the TV, they need a Spotify app at developer.spotify.com. Ask them to paste its client ID into `.env` as `SPOTIFY_CLIENT_ID`. Add the redirect URI `<WORKER_URL>/spotify/callback` in the Spotify app's settings. Then run `scripts/setup_cloud.sh` again.

## 14. Try it

Ask them to press Home, then press the voice button and ask for something in their own words. If it fails, check `adb logcat -s TVHOME` and `npx wrangler tail` from `worker/`.

## Later

- Edited `taste.md`: run `scripts/push_taste.sh`
- Copy what the TV has learned into `taste.md`: `scripts/pull_taste.sh`
- New keys: `scripts/rotate_keys.sh`
- Forget everything: `scripts/delete_my_data.sh`
- Take the app off the TV: `scripts/tv_remove.sh`
- Change the look: edit `design/tokens.json`, run `python3 design/build_tokens.py`, then `scripts/tv_install.sh`
