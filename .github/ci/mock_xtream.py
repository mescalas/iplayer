#!/usr/bin/env python3
"""Tiny fake Xtream Codes server used by the UI test workflow (emulator reaches it at 10.0.2.2)."""
import json, os, time, datetime, random
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

HERE = os.path.dirname(os.path.abspath(__file__))
MEDIA = os.environ.get("MEDIA_DIR", "/tmp/media")
HOST = os.environ.get("PUBLIC_BASE", "http://10.0.2.2:8000")
random.seed(4)

LIVE_CATS = [("1", "Généralistes"), ("2", "Information"), ("3", "Sport"), ("4", "Cinéma"), ("5", "Jeunesse"), ("6", "Documentaires")]
NAMES = ["Une", "Deux", "Trois", "Info 24", "Monde Info", "Éco News", "Sport 1", "Sport 2", "Foot+", "Ciné Club", "Ciné Max",
         "Action HD", "Kids TV", "Toons", "Planète Doc", "Nature HD", "Histoire", "Musique Live", "Comédie+", "Séries Club"]
CHANNELS = []
for i, n in enumerate(NAMES):
    CHANNELS.append({"num": i + 1, "name": n + (" HD" if i % 3 == 0 else ""), "stream_type": "live", "stream_id": 100 + i,
                     "stream_icon": f"{HOST}/img/logo{i}.png" if i % 4 != 3 else "", "epg_channel_id": f"ch{i}.fr",
                     "category_id": LIVE_CATS[min(i // 4, 5)][0], "tv_archive": 1 if i % 2 == 0 else 0, "tv_archive_duration": 3})
VOD_CATS = [("10", "Nouveautés"), ("11", "Action"), ("12", "Comédie"), ("13", "Drame")]
TITLES = ["Le Dernier Horizon", "Nuit Blanche", "La Cité Perdue", "Code Rouge", "L'Échappée", "Les Rivages", "Mission Atlas",
          "Le Grand Saut", "Ombres", "Après la Pluie", "Vertige", "Le Passager", "Sous la Glace", "Équinoxe", "Le Phare",
          "Contre-Courant", "La Traversée", "Tempête", "Le Secret", "Fréquence", "Lumière Noire", "Le Silence", "Odyssée", "Mirage"]
MOVIES = [{"num": i + 1, "name": f"{t} ({2015 + i % 10})", "stream_type": "movie", "stream_id": 500 + i,
           "stream_icon": f"{HOST}/img/poster{i}.png", "rating": str(round(5 + (i * 37 % 45) / 10, 1)),
           "added": str(int(time.time()) - i * 86400), "category_id": VOD_CATS[i % 4][0], "container_extension": "mp4"}
          for i, t in enumerate(TITLES)]
SER_CATS = [("20", "Séries françaises"), ("21", "Séries US"), ("22", "Animation")]
SERIES_T = ["Les Gardiens", "Brigade Nord", "Station 9", "Héritages", "Le Bureau des Ombres", "Saison Froide", "Dynasties", "Atlas"]
SERIES = [{"num": i + 1, "name": t, "series_id": 900 + i, "cover": f"{HOST}/img/poster{(i + 7) % 24}.png",
           "plot": "Une série originale palpitante, entre mystère et rebondissements.", "genre": "Drame, Thriller",
           "releaseDate": f"{2016 + i}-01-01", "rating": str(7 + i % 3), "backdrop_path": [f"{HOST}/img/backdrop{i % 4}.png"],
           "last_modified": str(int(time.time()) - i * 3600), "category_id": SER_CATS[i % 3][0]} for i, t in enumerate(SERIES_T)]


def series_info(sid):
    s = next((x for x in SERIES if x["series_id"] == sid), SERIES[0])
    seasons, episodes = [], {}
    for season in range(1, 3):
        seasons.append({"season_number": season, "name": f"Saison {season}", "cover": s["cover"]})
        episodes[str(season)] = [{"id": str(sid * 100 + season * 10 + e), "episode_num": e, "title": f"Épisode {e}",
                                  "container_extension": "mp4", "season": season,
                                  "info": {"movie_image": f"{HOST}/img/backdrop{(e + season) % 4}.png",
                                           "plot": "Résumé de l'épisode.", "duration_secs": 2700}} for e in range(1, 7)]
    return {"seasons": seasons, "info": {"name": s["name"], "plot": s["plot"], "cast": "A. Martin, C. Durand",
                                         "genre": s["genre"], "releaseDate": s["releaseDate"], "rating": s["rating"],
                                         "backdrop_path": s["backdrop_path"]}, "episodes": episodes}


def xmltv():
    now = datetime.datetime.now(datetime.timezone.utc).replace(minute=0, second=0, microsecond=0)
    out = ['<?xml version="1.0" encoding="UTF-8"?>', "<tv>"]
    for c in CHANNELS:
        out.append(f'<channel id="{c["epg_channel_id"]}"><display-name>{c["name"]}</display-name></channel>')
    shows = ["Le Journal", "Magazine Découverte", "Film : La Grande Évasion", "Match en direct", "Documentaire animalier",
             "Talk-show du soir", "Série : Enquêtes", "Dessins animés", "Météo", "Concert exceptionnel"]
    for ci, c in enumerate(CHANNELS):
        t = now - datetime.timedelta(hours=26)
        k = ci
        while t < now + datetime.timedelta(hours=30):
            dur = datetime.timedelta(minutes=random.choice([30, 45, 60, 90]))
            fmt = lambda d: d.strftime("%Y%m%d%H%M%S +0000")
            out.append(f'<programme start="{fmt(t)}" stop="{fmt(t + dur)}" channel="{c["epg_channel_id"]}">'
                       f'<title lang="fr">{shows[k % len(shows)]}</title><desc lang="fr">Description du programme {shows[k % len(shows)].lower()}.</desc></programme>')
            t += dur
            k += 1
    out.append("</tv>")
    return "\n".join(out).encode()


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, body, ctype="application/json", code=200):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def file(self, path, ctype):
        size = os.path.getsize(path)
        rng = self.headers.get("Range")
        start, end = 0, size - 1
        if rng and rng.startswith("bytes="):
            a, b = rng[6:].split("-")
            start = int(a) if a else 0
            end = int(b) if b else size - 1
            self.send_response(206)
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        self.end_headers()
        with open(path, "rb") as f:
            f.seek(start)
            remaining = end - start + 1
            try:
                while remaining > 0:
                    chunk = f.read(min(65536, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        p = u.path
        if p == "/player_api.php":
            a = q.get("action")
            data = {
                None: {"user_info": {"auth": 1, "status": "Active", "exp_date": "1893456000", "max_connections": "2"},
                       "server_info": {"timezone": "Europe/Paris", "url": "10.0.2.2", "port": "8000"}},
                "get_live_categories": [{"category_id": i, "category_name": n} for i, n in LIVE_CATS],
                "get_live_streams": CHANNELS,
                "get_vod_categories": [{"category_id": i, "category_name": n} for i, n in VOD_CATS],
                "get_vod_streams": MOVIES,
                "get_series_categories": [{"category_id": i, "category_name": n} for i, n in SER_CATS],
                "get_series": SERIES,
            }.get(a)
            if a == "get_series_info":
                data = series_info(int(q.get("series_id", "900")))
            if a == "get_vod_info":
                data = {"info": {"plot": "Un thriller haletant au cœur d'une ville en ébullition, où chaque choix compte.",
                                 "cast": "Léa Bernard, Hugo Petit", "director": "Claire Moreau", "genre": "Action, Thriller",
                                 "releasedate": "2021-06-02", "rating": "7.6", "duration_secs": 6300,
                                 "backdrop_path": [f"{HOST}/img/backdrop{int(q.get('vod_id', '0')) % 4}.png"]},
                        "movie_data": {"stream_id": q.get("vod_id"), "container_extension": "mp4"}}
            return self.send(json.dumps(data if data is not None else []).encode())
        if p == "/xmltv.php":
            return self.send(xmltv(), "application/xml")
        if p.startswith("/img/"):
            f = os.path.join(MEDIA, os.path.basename(p))
            if os.path.exists(f):
                return self.file(f, "image/png")
            return self.send(b"", "text/plain", 404)
        if p.startswith("/live/") or p.startswith("/timeshift/"):
            return self.file(os.path.join(MEDIA, "live.ts"), "video/mp2t")
        if p.startswith("/movie/") or p.startswith("/series/"):
            return self.file(os.path.join(MEDIA, "movie.mp4"), "video/mp4")
        self.send(b"not found", "text/plain", 404)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8000), H).serve_forever()
