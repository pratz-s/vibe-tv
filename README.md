# Vibe TV

**It gets your vibe.** A home screen for Google TV that learns your taste from what you actually watch, across every app.

Netflix will never tell you the best thing tonight is on Prime. Vibe TV will.

Every streaming app's home screen pushes its own shows. Vibe TV sits on top of Google TV and sees everything you watch, on every app. It recommends from all of them, and one press opens the show in the right app.

## What it does

- **Talk to it like a friend.** English, Hindi or Hinglish: "something light, under two hours", "kuch spicy dikhao", "Paatal Lok lagao", "Jarvis, save this for later". Misheard names still work, so "Coffee with Current" finds Koffee with Karan.
- **It learns from what you do.** What you finish, skip, rewatch and save, what you say out loud ("loved it", "not for me"), and even the playlists you keep on repeat.
- **Your taste is a file you can read.** No black box: everything it knows lives in `taste.md`, in plain sentences, and you can edit it.
- **Who's watching.** "Watching with my partner" or "friends are over" changes the picks, and your own profile stays yours.
- **Continue watching** takes you back into what you were in the middle of.
- **Comfort rewatches** get their own row, so old favourites don't pretend to be new.
- **Save for later, Seen it, Not for me** on every title.
- **A phone remote**, private to you, that works on iPhone and Android, with voice.
- **The little things.** The weather and holidays where you live, a butler with opinions, and a pixel boot animation.

## Setting it up

You need:

- A TV running Google TV or Android TV, with developer options switched on
- A Mac or Linux computer with [Claude Code](https://claude.com/claude-code)
- A free [Cloudflare](https://cloudflare.com) account (the paid Workers plan is worth it once you use it every day)
- An AI key: [Gemini](https://aistudio.google.com/apikey) (free) or [Anthropic](https://console.anthropic.com) (paid per search)
- A [Movie of the Night](https://www.movieofthenight.com/about/api) key, for what's streaming where

Clone this repo, open Claude Code inside it, and paste:

```
Read SETUP.md and set this up for me.
```

Claude goes one step at a time and asks:
- where you live (for weather, local time and holidays)
- which apps you watch on
- which remote button starts voice
- what to call your assistant
- whether the TV may learn from what plays

Then it has a short chat about what you like, and writes your taste file from that.

## Privacy

Everything runs on your own accounts. There are no shared servers, and nobody else's data is in here.

**What it records**, only if you leave learning on (`LEARN=true`):
- which title plays and for how long
- which app is open
- songs heard on Spotify or YouTube Music
- what you ask by voice

**Where it's stored.** Only in your own Cloudflare database.

**Who else sees it:**
- Your AI provider sees your taste file and what you asked, each time you search. On Gemini's free tier, Google may use those requests to improve its products. Use an Anthropic key if that bothers you.
- Movie of the Night and Cinemeta see which titles are looked up, but nothing about you.
- Speech recognition on the TV is Google's own. Voice from the phone remote uses your phone browser's speech recognition, or Cloudflare's Whisper on your own account.

**What runs on the TV.** The app uses an accessibility service to take over the Home button and to notice which app is open. It doesn't read what's on your screen.

**To take it all back:**
- `scripts/delete_my_data.sh` wipes everything your engine knows about you.
- `scripts/tv_remove.sh` takes the app off the TV.

## Security

- **Three separate keys,** generated during setup and stored as Cloudflare secrets:
  - one for the TV
  - one for your scripts on your computer (only this one can change your taste or the catalogue)
  - one for the phone remote link
- **The TV's key is built into the app you install, so don't share your built app.** Treat the phone remote link like a password. If either gets out, `scripts/rotate_keys.sh` makes new keys and turns the old ones off.
- **Requests are size-limited** and keys are checked in constant time.
- **Every commit runs `scripts/check_secrets.sh`,** which blocks keys, private IDs, home network addresses and your personal files from being published. Put your own names in `.private-words`, which is never committed, to catch those too.

## Making it yours

| If you want to change | Go here |
|---|---|
| What it knows about you | `taste.md`, then `scripts/push_taste.sh` |
| What it has learned, copied into your taste file | `scripts/pull_taste.sh` |
| Your city | `python3 scripts/set_city.py "City"`, then `scripts/setup_cloud.sh` |
| The voice button | `scripts/pick_voice_key.sh`, then `scripts/tv_install.sh` |
| Colours, type, spacing, motion | `design/tokens.json`, then `python3 design/build_tokens.py` |
| The UI sounds | `design/build_sounds.py` |

## Plugging in your own memory

If you use a note taker, a journal or a notes app, send it one line at a time about the films and shows that come up:

```bash
scripts/add_note.sh "A friend recommended Dark and we both sounded keen."
```

The newest 60 notes are read on every search. Only send notes about films and shows, and ask anyone who shows up in them first.

## How it works

```
your words -> speech on the TV -> your Cloudflare Worker -> Gemini or Claude (Workers AI as backup)
                                          |
                     Movie of the Night (where it streams)
                     Cinemeta (ratings, trailers, posters)
                                          |
             rows on your TV -> opens in Netflix, Prime, JioHotstar, Stremio...
```

- `tv-app/` is the Android app that runs on the TV
- `worker/` is the engine: Cloudflare Workers, D1, Vectorize and Workers AI
- `design/` is the design system. `design/SPEC.md` has all of it

## Before you get too excited

- **It has been tested on one TV,** a Google TV on Android 14. The Home button takeover reads Google TV's own screens through an accessibility service, so other models may need some fixing.
- **It doesn't play video from add-ons.** Titles open in the apps on your TV, and what you run inside them is your call. Please respect the rules of the services you use.
- **Trailers come through [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor),** which reads YouTube directly. YouTube can break that at any time.

## Credits

Vibe TV started from **[Non-Annoying TV](https://github.com/atulkhola/non-annoying-tv) by Atul Khola**: a calmer TV home he designed in a weekend and open-sourced. The design system, the home screen and the voice search come from there. Vibe TV taught it to learn.

Streaming availability comes from the [Streaming Availability API](https://www.movieofthenight.com/about/api) by Movie of the Night. Their terms ask for this credit, so the app also shows it on its About screen. Ratings and artwork come through Cinemeta. The type is the [Geist](https://vercel.com/font) family, under the SIL Open Font License. The weather comes from [Open-Meteo](https://open-meteo.com).

Not affiliated with Google, Netflix, Amazon, JioHotstar, Spotify, Stremio, Anthropic or Cloudflare.

## Licence

GPL-3.0, like Non-Annoying TV, because the app includes NewPipe Extractor. See `LICENSE`.
