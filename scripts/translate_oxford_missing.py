#!/usr/bin/env python3
"""Fill unmatched Oxford Vietnamese glosses through the project's MyMemory fallback.

Run after import_oxford3000.py and merge_oxford_vietnamese.py. Uses only rows that
still lack a meaning, saves successful translations, and leaves failed/ambiguous
terms blank. Translations from this step are machine-generated and should be reviewed.
"""
import csv
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from import_oxford3000 import topic

ROOT = Path(__file__).resolve().parent.parent
TSV = ROOT / "data/vocab/oxford3000-topics.tsv"
ENDPOINT = "https://api.mymemory.translated.net/get"
MANUAL = {
    "i": "tôi, tao; mình (đại từ ngôi thứ nhất số ít)",
    "be": "là, thì, ở (động từ to be)",
    "blog": "blog, nhật ký hoặc trang thông tin trực tuyến",
    "ok": "được, ổn; đồng ý",
    "online": "trực tuyến, trên mạng",
    "tv": "truyền hình; ti vi",
    "app": "ứng dụng (trên điện thoại hoặc máy tính)",
    "jazz": "nhạc jazz",
    "last1 taking time": "kéo dài",
    "album": "album; tuyển tập (nhạc hoặc ảnh)",
    "competitor": "đối thủ cạnh tranh; người dự thi",
    "donate": "quyên góp; hiến tặng",
    "podcast": "chương trình âm thanh hoặc video trực tuyến (podcast)",
    "virtual": "ảo; trực tuyến",
    "vitamin": "vitamin",
}

def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", s.casefold()).strip()

def translate(word):
    query_word = re.sub(r"\d+$", "", word).strip()
    params = urllib.parse.urlencode({"q": query_word, "langpair": "en|vi"})
    req = urllib.request.Request(ENDPOINT + "?" + params, headers={"User-Agent": "Parroto-Oxford3000-import/1.0"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                body = json.load(response)
            if body.get("quotaFinished"):
                return word, ""
            text = body.get("responseData", {}).get("translatedText", "").strip()
            if body.get("responseStatus") == 200 and text and text.casefold() != query_word.casefold() and "MYMEMORY WARNING" not in text:
                return word, text
            return word, ""
        except Exception:
            if attempt < 2:
                time.sleep(1.5 * (attempt + 1))
    return word, ""

def main():
    with TSV.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        fields, rows = reader.fieldnames, list(reader)
    if "meaning_source" not in fields:
        fields = fields + ["meaning_source"]
    missing = {}
    for row in rows:
        row.setdefault("meaning_source", "")
        if row["meaning_vi"]:
            row["meaning_source"] = row["meaning_source"] or "provided-dictionary"
        else:
            key = norm(row["word"])
            if key in MANUAL:
                row["meaning_vi"] = MANUAL[key]
                row["meaning_source"] = "manual"
            else:
                missing.setdefault(key, row["word"])
    results = {}
    with ThreadPoolExecutor(max_workers=5) as pool:
        futures = [pool.submit(translate, word) for word in missing.values()]
        for fut in as_completed(futures):
            word, meaning = fut.result()
            if meaning:
                results[norm(word)] = meaning
    for row in rows:
        if not row["meaning_vi"] and norm(row["word"]) in results:
            row["meaning_vi"] = results[norm(row["word"])]
            row["meaning_source"] = "mymemory-machine-translation"
        elif row["meaning_vi"] and not row["meaning_source"]:
            row["meaning_source"] = "manual"
        row["topic"], row["topic_vi"] = topic(row["word"], row["meaning_vi"], row["part_of_speech"])
    with TSV.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"Translated {len(results)} unique missing terms; {len(missing)-len(results)} remain blank.")

if __name__ == "__main__":
    main()
