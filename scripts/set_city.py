#!/usr/bin/env python3
"""Finds a city and saves it to .env, for the weather, the local time and the holiday calendar.
Usage: python3 scripts/set_city.py "Pune"     (add the country if the name is common: "Cambridge, US")
Uses Open-Meteo's free place search. Nothing else is sent."""
import json, os, re, sys, urllib.parse, urllib.request
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
if len(sys.argv) < 2: sys.exit('Usage: python3 scripts/set_city.py "City"')
name, _, cc = sys.argv[1].partition(",")
q = {"name": name.strip(), "count": 10, "language": "en", "format": "json"}
r = json.load(urllib.request.urlopen("https://geocoding-api.open-meteo.com/v1/search?" + urllib.parse.urlencode(q), timeout=20)).get("results") or []
if cc.strip(): r = [x for x in r if x.get("country_code", "").lower() == cc.strip().lower()] or r
if not r: sys.exit("Couldn't find " + sys.argv[1] + ". Try the nearest big city.")
p = max(r, key=lambda x: x.get("population") or 0) if not cc.strip() else r[0]
vals = {"CITY": p["name"], "LAT": str(round(p["latitude"], 2)), "LON": str(round(p["longitude"], 2)), "TIMEZONE": p.get("timezone", "UTC")}
s = open(".env").read() if os.path.exists(".env") else ""
if not re.search(r"^COUNTRY=\S", s, re.M): vals["COUNTRY"] = p.get("country_code", "").lower()
for k, v in vals.items():
    s = re.sub(rf"^{k}=.*$", lambda m: f"{k}={v}", s, flags=re.M) if re.search(rf"^{k}=", s, re.M) else s.rstrip("\n") + f"\n{k}={v}\n"
open(".env", "w").write(s)
print(f"Saved {p['name']}, {p.get('admin1', '')} {p.get('country', '')} ({vals['TIMEZONE']}).")
