#!/usr/bin/env python3
"""Merge Vietnamese glosses from a locally supplied Oxford vocabulary PDF.

Usage: python3 scripts/merge_oxford_vietnamese.py /path/to/3000.pdf
The PDF provides Vietnamese meanings but no example sentences. This updates the
source TSV; rows without a matching gloss remain blank and are reported.
"""
import csv
import re
import subprocess
import sys
from pathlib import Path
from import_oxford3000 import topic

ROOT = Path(__file__).resolve().parent.parent
TSV = ROOT / "data/vocab/oxford3000-topics.tsv"
B2_TSV = ROOT / "data/vocab/b2-essential.tsv"

def norm(s):
    return re.sub(r"[^a-z0-9]+", " ", s.casefold()).strip()

def read_pdf(path):
    text = subprocess.check_output(["pdftotext", "-raw", str(path), "-"], text=True)
    label = r"(?:modal\s+verb|modal\s+v|auxiliary\s+v|vauxiliary|nprep|modal|pro|n|v|adj|adv|prep|conj|pron|det|exclam|number)"
    pos = rf"\b{label}\b\.?"
    row_re = re.compile(rf"^(.+?)\s+({pos}(?:(?:\s*[,/]\s*|\s+)+{pos})*)\s*(.*)$", re.I)
    ipa_re = re.compile(r"[\u0250-\u02ffæɑɔʌɪʊŋθð]|[':]", re.I)
    viet_re = re.compile(r"[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]", re.I)
    entries, current = [], None
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith(("Oxford", "Trang ", "3000 TỪ", "No.", "http", "©")):
            continue
        number = re.match(r"^(\d{1,4})(?:\s+(.*))?$", line)
        if number:
            if current:
                entries.append(current)
            # Skip the centered document title, which begins with 3000.
            content = number.group(2) or ""
            current = [] if content else []
            if content:
                current.append(content)
        elif current is not None and not line.startswith(("EEFC", "www.", "WWW.", "Effortless")):
            current.append(line)
    if current:
        entries.append(current)

    result = {}
    for lines in entries:
        if not lines:
            continue
        m = row_re.match(re.sub(r"\s+", " ", lines[0]).strip())
        if not m:
            continue
        word, tail = m.group(1).strip(), m.group(3).strip()
        tokens = tail.split()
        i = 0
        while i < len(tokens):
            token = tokens[i].strip(",;:()")
            # IPA is one token in most rows; some entries list two or three variants.
            if not token or token in {"-", "–", "—", "/"} or ipa_re.search(token) or (i == 0 and len(tokens) > 1 and not viet_re.search(token)):
                i += 1
            else:
                break
        tail_words = []
        for token in tokens[i:]:
            clean = token.strip("()[]{}.,;:—–-")
            if not clean or clean.casefold().rstrip(".") in {
                "n", "v", "adj", "adv", "prep", "conj", "pron", "det", "exclam", "modal", "verb", "auxiliary", "pro", "nprep", "also", "name", "bre", "ame"
            } or ipa_re.search(clean):
                continue
            tail_words.append(token)
        continuations = []
        for line in lines[1:]:
            words = []
            for token in line.split():
                clean = token.strip("()[]{}.,;:—–-`")
                if not clean or clean.casefold().rstrip(".") in {
                    "n", "v", "adj", "adv", "prep", "conj", "pron", "det", "exclam", "modal", "auxiliary", "pro"
                } or ipa_re.search(clean):
                    continue
                words.append(token)
            if words:
                continuations.append(" ".join(words))
        meaning = " ".join(tail_words + continuations).strip()
        meaning = re.sub(r"\s+", " ", meaning).strip(" `´'\t")
        if meaning:
            aliases = {norm(word)}
            # The older list annotates some headwords (e.g. ``arrive (at, in)``)
            # or combines degree forms (``better, best``); index their plain forms too.
            plain = re.sub(r"\s*\([^)]*\)", "", word).strip()
            if "money" not in word.lower():
                aliases.update(norm(part) for part in plain.split(",") if norm(part))
            for alias in aliases:
                result[alias] = meaning
    return result

def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__.splitlines()[2])
    glosses = read_pdf(Path(sys.argv[1]))
    with TSV.open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        fields, rows = reader.fieldnames, list(reader)
    matched = 0
    for row in rows:
        row["meaning_vi"] = ""
        key = norm(row["word"])
        options = [key]
        value = next((glosses[k] for k in options if k in glosses), "")
        if value:
            row["meaning_vi"] = value
            matched += 1
    for row in rows:
        if row["word"].casefold() in {"a, an", "an"}:
            row["meaning_vi"] = "một (mạo từ không xác định, dùng trước danh từ số ít)"
    # Reuse reviewed project cards that overlap the Oxford list. They already
    # contain a Vietnamese gloss and a bilingual example sentence.
    reviewed = {}
    with B2_TSV.open(encoding="utf-8", newline="") as f:
        for item in csv.DictReader(f, delimiter="\t"):
            reviewed[norm(item["word"])] = item
    reviewed_matches = 0
    for row in rows:
        item = reviewed.get(norm(row["word"]))
        if item:
            row["meaning_vi"] = item["meaning"]
            row["example_en"] = item["example"]
            row["example_vi"] = item["example_vi"]
            row["ipa"] = item["ipa"].strip("/[]")
            reviewed_matches += 1
    for row in rows:
        row["topic"], row["topic_vi"] = topic(row["word"], row["meaning_vi"], row["part_of_speech"])
    with TSV.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    examples = sum(bool(r["example_en"]) and bool(r["example_vi"]) for r in rows)
    print(f"PDF glosses: {len(glosses)}; matched: {matched}/{len(rows)}; reviewed project cards: {reviewed_matches}")
    print(f"Bilingual examples available: {examples}/{len(rows)}. The PDFs contain no example sentences.")

if __name__ == "__main__":
    main()
