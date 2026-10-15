#!/usr/bin/env python3
"""Final-round API regression (stdlib only): python3 scripts/api-smoke.py. Does not consume seeded demo codes.
Logs in as the admin account seeded from .env: ADMIN_EMAIL / ADMIN_PASSWORD must be set in the environment."""
import json, os, sys, time, struct, math, urllib.request, urllib.error

GW = "http://localhost:8000"
FE = "http://localhost:3000"
results = []


def req(method, path, body=None, token=None, raw=None, ctype="application/json", base=GW, headers=None):
    url = base + path
    data = None
    h = dict(headers or {})
    if raw is not None:
        data = raw
        h["Content-Type"] = ctype
    elif body is not None:
        data = json.dumps(body).encode()
        h["Content-Type"] = "application/json"
    if token:
        h["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            b = resp.read()
            return resp.status, b, dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers)


def j(b):
    try:
        return json.loads(b)
    except Exception:
        return None


def check(cid, desc, ok, info=""):
    results.append((cid, ok))
    print(("PASS" if ok else "FAIL"), cid, desc, "|", str(info)[:160])


ts = str(int(time.time()))
pw = "Passw0rd!smoke"
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "admin@parroto.com")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD") or sys.exit("set ADMIN_PASSWORD (the admin password from .env)")
ea = f"final_a_{ts}@test.local"
s, b, _ = req("POST", "/auth/register", {"email": ea, "password": pw, "cefrLevel": "A2"})
check("AUTH-REG", "register user A", s == 201, s)
A = j(b) or {}
TA = A.get("token")
s, b, _ = req("POST", "/auth/login", {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
if s != 200:
    s, b, _ = req("POST", "/auth/register", {"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD})
check("AUTH-ADMIN", "register/login admin", s in (200, 201), s)
TADM = (j(b) or {}).get("token")

s, b, _ = req("GET", "/auth/profile", token=TA)
check("AUTH-PROFILE", "profile", s == 200 and (j(b) or {}).get("email") == ea, s)
s, b, _ = req("GET", "/auth/profile", token=TADM)
check("AUTH-PROFILE-ADMIN", "admin profile isAdmin", s == 200 and (j(b) or {}).get("isAdmin") is True, b[:120])
s, _, _ = req("GET", "/practice/stats", token=A.get("refreshToken"))
check("AUTH-02-GW", "refresh token as Bearer -> 401", s == 401, s)
s, _, _ = req("GET", "/practice/stats")
check("AUTH-NOTOKEN", "no token -> 401", s == 401, s)
s, b, _ = req("POST", "/auth/login-google", {"googleToken": "x"})
check("AUTH-04", "login-google with placeholder client id -> 503", s == 503, s)

s, b, _ = req("GET", "/subscriptions/plans")
prices = sorted(p.get("price") for p in ((j(b) or {}).get("plans") or []))
check("SUB-PLANS", "plans 79000/169000/499000", prices == [79000, 169000, 499000], prices)

s, b, _ = req("GET", "/content/lessons")
lessons = j(b)
items = lessons.get("data") or lessons.get("content") or lessons.get("items") or lessons.get("lessons") if isinstance(lessons, dict) else lessons
check("LES-LIST", "lessons list", s == 200 and items, f"{s} n={len(items or [])}")
lid = items[0]["id"]
s, b, _ = req("GET", f"/content/lessons/{lid}")
lesson = j(b) or {}
sents = lesson.get("sentences") or []
check("LES-DETAIL", "lesson detail sentences", s == 200 and len(sents) > 0, len(sents))
s1 = sents[0]

for diff, exp in (("Easy", 60), ("Normal", 80), ("Hard", 100)):
    s, b, _ = req("POST", "/practice/dictation/attempt",
                  {"lessonId": lid, "sentenceId": s1["id"], "userInput": s1["text"], "difficulty": diff}, token=TA)
    sc = (j(b) or {}).get("score")
    check(f"DIC-07-{diff}", f"perfect {diff} -> {exp}", s == 201 and sc is not None and abs(sc - exp) < 0.01, sc)

# shadowing: 1s 16k mono WAV tone
n = 16000
pcm = b"".join(struct.pack("<h", int(8000 * math.sin(2 * math.pi * 220 * i / 16000))) for i in range(n))
wav = b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, 16000, 32000, 2, 16) + b"data" + struct.pack("<I", len(pcm)) + pcm
s, b, _ = req("POST", "/practice/shadowing/request-upload-url", {"lessonId": lid, "sentenceId": s1["id"]}, token=TA)
up = j(b) or {}
check("SHA-URL", "request upload url", s == 200 and up.get("recordingId"), up.get("storage"))
s, _, _ = req("PUT", up.get("uploadUrl", ""), raw=wav, ctype="audio/wav", token=TA)
check("SHA-PUT", "PUT wav -> 204", s == 204, s)
s, b, _ = req("POST", "/practice/shadowing/attempt", {"lessonId": lid, "sentenceId": s1["id"], "recordingId": up.get("recordingId"), "duration": 1.0}, token=TA)
sh = j(b) or {}
check("SHA-SUBMIT", "submit -> mock with wordScores", s == 201 and sh.get("scorer") == "mock" and sh.get("wordScores"), f"{s} {sh.get('scorer')}")
s, b, _ = req("GET", "/practice/stats/activity?days=90", token=TA)
check("PRA-03", "activity 90", s == 200 and len(j(b) or []) == 90, s)
s, b, _ = req("GET", "/practice/streak", token=TA)
check("PRA-07", "streak >= 1", s == 200 and (j(b) or {}).get("currentStreak", 0) >= 1, b[:80])
s, b, _ = req("GET", "/practice/attempts", token=TA)
check("PRA-05", "attempts list", s == 200, s)

# vocab
s, b, _ = req("GET", "/vocabs/decks")
decks = j(b)
dl = decks.get("data") or decks.get("content") or decks.get("items") or decks.get("decks") if isinstance(decks, dict) else decks
free = [d for d in dl if not d.get("isProOnly")]
check("VOC-DECKS", "decks", s == 200 and free, len(dl))
s, b, _ = req("GET", f"/vocabs/decks/{free[0]['id']}/cards", token=TA)
cd = j(b)
cards = cd.get("data") or cd.get("content") or cd.get("items") or cd.get("cards") if isinstance(cd, dict) else cd
check("VOC-CARDS", "deck cards", s == 200 and cards, len(cards or []))
s, b, _ = req("POST", "/vocabs/user/add-from-lesson", {"cardId": cards[0]["id"], "source": "deck"}, token=TA)
uv = (j(b) or {}).get("id") or (j(b) or {}).get("userVocabId")
check("VOC-ADD", "add card", s in (200, 201) and uv, b[:100])
s, b, _ = req("POST", "/vocabs/user/review", {"userVocabId": uv, "quality": 4}, token=TA)
check("VOC-REVIEW", "review q4", s == 200 and (j(b) or {}).get("interval") == 1, b[:100])
s, b, _ = req("GET", "/vocabs/user/learned", token=TA)
lw = j(b)
lwl = lw.get("data") or lw.get("content") or lw.get("items") or lw.get("words") if isinstance(lw, dict) else lw
f0 = (lwl or [{}])[0]
check("FIX-VOC-05", "learned includes nextReviewAt/intervalDays/easeFactor",
      s == 200 and all(k in f0 for k in ("nextReviewAt", "intervalDays", "easeFactor")), {k: f0.get(k) for k in ("nextReviewAt", "intervalDays", "easeFactor")})
pro = [d for d in dl if d.get("isProOnly")]
if pro:
    s, b, _ = req("GET", f"/vocabs/decks/{pro[0]['id']}/cards", token=TA)
    pc = j(b)
    pcl = (pc.get("data") or pc.get("content") or pc.get("items") or pc.get("cards")) if isinstance(pc, dict) else pc
    if s == 200 and pcl:
        s2, _, _ = req("POST", "/vocabs/user/add-from-lesson", {"cardId": pcl[0]["id"]}, token=TA)
        check("VOC-PRO", "free user add pro card -> 403", s2 == 403, s2)
    else:
        check("VOC-PRO", "free user pro deck cards -> 403", s == 403, s)

# FIX-R1 error mapping
for cid, path, exp in (("FIX-ERR-LES", "/content/lessons/abc", 400), ("FIX-ERR-DECK", "/vocabs/decks/xyz/cards", 400),
                       # P2-C: deck detail (with groups) is a real endpoint now
                       ("FIX-ERR-DECKGET", "/vocabs/decks/22222222-2222-2222-2222-222222222201", 200),
                       ("FIX-ERR-404", "/content/no-such-route", 404), ("FIX-ERR-404V", "/vocabs/nope/nope", 404),
                       ("FIX-ERR-GW404", "/nonexistent-route", 404)):
    s, b, _ = req("GET", path, token=TA)
    ok = (s == exp) if exp else (s in (404, 405))
    check(cid, f"GET {path} -> {exp or '404/405'}", ok and s != 500, f"{s} {b[:80]}")

# admin codes (don't use seeded demo codes here)
s, b, _ = req("POST", "/subscriptions/admin/activation-codes", {"planId": "plan-1month", "quantity": 2}, token=TADM)
codes = [c["code"] for c in (j(b) or {}).get("codes", [])]
check("ADM-06-CREATE", "admin creates codes", s == 201 and len(codes) == 2, s)
s, _, _ = req("POST", "/subscriptions/admin/activation-codes", {"planId": "plan-1month", "quantity": 1}, token=TA)
check("ADM-06-403", "non-admin -> 403", s == 403, s)
s, b, _ = req("POST", "/subscriptions/activate-code", {"code": codes[0]}, token=TA)
check("SUB-05-ACT", "activate admin code", s == 200, s)
s, b, _ = req("POST", "/subscriptions/activate-code", {"code": codes[0]}, token=TA)
check("SUB-05-REUSE", "reuse -> 400", s == 400, b[:80])
s, b, _ = req("GET", "/subscriptions/user", token=TA)
check("SUB-USER", "isPremium", (j(b) or {}).get("isPremium") is True, b[:60])

# FIX-R1 logout revokes refresh token
s, b, _ = req("POST", "/auth/login", {"email": ea, "password": pw})
L = j(b) or {}
s, b, _ = req("POST", "/auth/logout", {"refreshToken": L.get("refreshToken")}, token=L.get("token"))
check("AUTH-03-LOGOUT", "logout with refreshToken body", s == 200, s)
s, _, _ = req("GET", "/practice/stats", token=L.get("token"))
check("AUTH-03-ACCESS", "old access -> 401", s == 401, s)
s, b, _ = req("POST", "/auth/refresh-token", {"refreshToken": L.get("refreshToken")})
check("AUTH-03-REFRESH", "old refresh after logout -> 401", s == 401, s)
# logout without body still works
s, b, _ = req("POST", "/auth/login", {"email": ea, "password": pw})
L2 = j(b) or {}
s, _, _ = req("POST", "/auth/logout", token=L2.get("token"))
check("AUTH-03-NOBODY", "logout without body -> 200", s == 200, s)
s, b, _ = req("POST", "/auth/refresh-token", {"refreshToken": L2.get("refreshToken")})
check("AUTH-REFRESH-OK", "refresh token of another session still works when not sent (expected 200)", s == 200, s)

for p, exp in (("/", 200), ("/login", 200), ("/lessons", 200), ("/pricing", 200), ("/no-such-page", 404)):
    s, _, _ = req("GET", p, base=FE)
    check("FE" + p, f"FE {p}", s == exp, s)

np_ = sum(1 for _, ok in results if ok)
print(f"\nTOTAL {len(results)} PASS {np_} FAIL {len(results) - np_}")
sys.exit(0 if np_ == len(results) else 1)
