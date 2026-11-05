#!/usr/bin/env python3
"""Bouwt de offline kennisbank (GGZ & verslaving) voor de Vraagbaak.

Haalt Nederlandstalige vraag/antwoord-paren van Hugging Face (UMCU), houdt alleen psychiatrie en
verslaving over en schrijft één SQLite-bestand met een FTS4-zoekindex. De app opent dit bestand
read-only en zoekt erin zonder internet.

Gebruik:
    pip install pyarrow          # alleen nodig voor de kleinere parquet-sets
    python3 tools/maak_kennisbank.py --uit kennisbank.sqlite
    python3 tools/maak_kennisbank.py --uit test.sqlite --max-miriad 200000   # snel proefje

Bronnen (allemaal machinaal vertaald uit het Engels; geen klinisch advies):
- UMCU/MIRIAD4.4.nl: 4,4 mln vraag/antwoord-paren uit wetenschappelijke artikelen (met titel en link).
  We nemen de specialismen "Psychiatry" en "Psychology & Behavioral Health" helemaal, plus vragen uit
  andere specialismen waarvan de vraag of artikeltitel over middelen, verslaving of psychiatrische
  ziektebeelden gaat (dierproeven daar overgeslagen).
- UMCU/MedicalFlashCards, WikiDoc, WikiDocPatientInformation, MedQA_SymptomDisease, MediQA:
  gefilterd op dezelfde trefwoorden in de vraag.

Schema (moet overeenkomen met data/medical/KnowledgeBase.kt):
    meta(sleutel TEXT PRIMARY KEY, waarde TEXT)
    kennis: FTS4(zoek, vraag, antwoord, titel, bron, specialisme, url, jaar), alleen `zoek` geïndexeerd.
`zoek` = normaliseer(vraag + titel + antwoord): kleine letters, zonder accenten, alleen letters/cijfers.
"""
import argparse
import io
import json
import os
import re
import sqlite3
import sys
import time
import unicodedata
import urllib.request

SCHEMA_VERSIE = "1"

MIRIAD_URL = "https://huggingface.co/datasets/UMCU/MIRIAD4.4.nl/resolve/main/miriad_qa_dutch.jsonl"
PARQUET_SETS = {
    # naam: (vraagkolom, antwoordkolom, nette bronnaam)
    "MedicalFlashCards_Dutch_translated_with_MariaNMT": ("input", "output", "Medical Flashcards (UMCU, vertaald)"),
    "WikiDocPatientInformation_Dutch_translated_with_MariaNMT": ("input", "output", "WikiDoc patiëntinformatie (UMCU, vertaald)"),
    "WikiDoc_Dutch_translated_with_MariaNMT": ("input", "output", "WikiDoc (UMCU, vertaald)"),
    "MedQA_SymptomDisease_small_Dutch": ("instruction", "answer", "MedQA symptomen (UMCU, vertaald)"),
    "MediQA_Dutch_translated_with_MariaNMT": ("instruction", "output", "MediQA (UMCU, vertaald)"),
}

# Woordbegin-trefwoorden (na normaliseren: zonder accenten, kleine letters).
VERSLAVING = [
    "verslav", "addict", "afhankelijkheid", "middelengebruik", "middelenmisbruik", "middelenstoornis",
    "substance", "drugsgebruik", "drugsmisbruik", "druggebruik", "drugsverslaving",
    "alcohol", "drank", "drinkgedrag", "binge", "delier", "delirium tremens", "ontwenning", "onthouding",
    "withdrawal", "detox", "korsakov", "wernicke",
    "opioid", "opiaat", "opiat", "heroine", "morfine", "methadon", "buprenorf", "naloxon", "naltrexon",
    "fentanyl", "oxycodon", "tramadol", "codeine",
    "cocaine", "amfetamine", "amphetamine", "methamfetamine", "methamphetamine", "xtc", "mdma",
    "ecstasy", "cannabis", "marihuana", "marijuana", "hasj", "thc", "ghb", "ketamine", "lachgas",
    "benzodiazepine", "diazepam", "oxazepam", "lorazepam", "alprazolam", "clonazepam", "chlordiazepoxide",
    "nicotine", "roken", "rokers", "tabak", "sigaret", "vapen", "e sigaret",
    "gokken", "gokstoornis", "gokverslaving", "gaming disorder", "gameverslaving",
    "acamprosaat", "acamprosate", "disulfiram", "nalmefeen", "baclofen", "varenicline",
    "overdosis", "overdose", "intoxicatie", "harm reduction", "schadebeperking", "motiverende gespreksvoering",
    "motivational interviewing", "craving", "terugval", "relapse",
]
PSYCHIATRIE = [
    "psychiatr", "psycholog", "psychose", "psychot", "schizofren", "schizo", "bipolair", "bipolar", "manisch", "manische",
    "depressie", "depressiev", "depressive", "angststoornis", "angstklacht", "paniek", "fobie", "ocd", "dwangstoornis",
    "ptss", "ptsd", "posttraumat", "adhd", "autisme", "autism", "persoonlijkheidsstoorn",
    "borderline", "antisociale", "suicid", "zelfmoord", "zelfbeschadig", "eetstoorn", "anorexia", "boulimia",
    "slapeloosheid", "insomnia", "antipsychotic", "antipsychotica", "antidepressiv", "antidepressant",
    "ssri", "lithium", "clozapine", "olanzapine", "quetiapine", "risperidon", "aripiprazol", "haloperidol",
    "dementie", "delier", "cognitieve gedragstherapie", "emdr", "ggz", "geestelijke gezondheid",
    "mental health",
]
VOLLEDIG = {"Psychiatry", "Psychology & Behavioral Health"}
DIERPROEF = re.compile(r"\b(rat|ratten|muis|muizen|knaagdier|knaagdieren|rodent|rodents|mice|dierproef|dierstudie|zebravis)\b")


def normaliseer(tekst: str) -> str:
    """Kleine letters, zonder accenten, alleen letters en cijfers (rest wordt spatie).

    Moet gelijk blijven aan KnowledgeBase.normalize() in de app."""
    t = unicodedata.normalize("NFD", tekst.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^\w]+", " ", t, flags=re.UNICODE).replace("_", " ")
    return " ".join(t.split())


def maak_patroon(woorden):
    delen = sorted({normaliseer(w) for w in woorden}, key=len, reverse=True)
    return re.compile(r"(?:^| )(?:" + "|".join(re.escape(d) for d in delen) + ")")


PATROON_VERSLAVING = maak_patroon(VERSLAVING)
PATROON_PSYCHIATRIE = maak_patroon(PSYCHIATRIE)


def onderwerp(zoek: str):
    """'verslaving', 'psychiatrie' of None."""
    if PATROON_VERSLAVING.search(zoek):
        return "verslaving"
    if PATROON_PSYCHIATRIE.search(zoek):
        return "psychiatrie"
    return None


def open_url(url, pogingen=6):
    for i in range(pogingen):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "kennisbank-bouwer"}), timeout=120)
        except Exception as e:  # noqa: BLE001
            if i == pogingen - 1:
                raise
            print(f"  opnieuw na fout: {e}", file=sys.stderr)
            time.sleep(2 ** (i + 1))


def maak_db(pad):
    if os.path.exists(pad):
        os.remove(pad)
    db = sqlite3.connect(pad)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    db.execute("CREATE TABLE meta(sleutel TEXT PRIMARY KEY, waarde TEXT)")
    db.execute(
        "CREATE VIRTUAL TABLE kennis USING fts4(zoek, vraag, antwoord, titel, bron, specialisme, url, jaar, "
        "notindexed=vraag, notindexed=antwoord, notindexed=titel, notindexed=bron, notindexed=specialisme, "
        "notindexed=url, notindexed=jaar)"
    )
    return db


class Schrijver:
    def __init__(self, db):
        self.db = db
        self.buffer = []
        self.gezien = set()
        self.tellers = {}

    def voeg_toe(self, vraag, antwoord, titel, bron, specialisme, url, jaar, soort):
        vraag, antwoord = (vraag or "").strip(), (antwoord or "").strip()
        if len(vraag) < 8 or len(antwoord) < 20:
            return
        sleutel = hash(normaliseer(vraag))
        if sleutel in self.gezien:
            return
        self.gezien.add(sleutel)
        zoek = normaliseer(f"{vraag} {titel} {antwoord}")
        self.buffer.append((zoek, vraag, antwoord, titel or "", bron, specialisme or "", url or "", jaar or ""))
        self.tellers[(bron, soort)] = self.tellers.get((bron, soort), 0) + 1
        if len(self.buffer) >= 5000:
            self.flush()

    def flush(self):
        if self.buffer:
            self.db.executemany("INSERT INTO kennis VALUES (?,?,?,?,?,?,?,?)", self.buffer)
            self.db.commit()
            self.buffer.clear()


def lees_miriad(schrijver, maximum):
    print("MIRIAD4.4.nl streamen (±4,9 GB)…", file=sys.stderr)
    specialismen = {}
    with open_url(MIRIAD_URL) as resp:
        for n, regel in enumerate(io.TextIOWrapper(resp, encoding="utf-8")):
            if maximum and n >= maximum:
                break
            if n % 250_000 == 0 and n:
                print(f"  {n:,} regels gelezen, {len(schrijver.gezien):,} bewaard", file=sys.stderr)
            try:
                r = json.loads(regel)
            except ValueError:
                continue
            spec = (r.get("specialty") or "").strip()
            specialismen[spec] = specialismen.get(spec, 0) + 1
            vraag, antwoord = r.get("question") or "", r.get("answer") or ""
            kern = normaliseer(f"{vraag} {r.get('paper_title') or ''}")
            if spec in VOLLEDIG:
                soort = onderwerp(kern) or "psychiatrie"
            else:
                soort = onderwerp(kern)
                if soort is None or DIERPROEF.search(kern + " " + normaliseer(antwoord)):
                    continue
            jaar = str(r.get("year") or "").split(".")[0]
            schrijver.voeg_toe(vraag, antwoord, r.get("paper_title"), "MIRIAD (UMCU, vertaald)", spec,
                               r.get("paper_url"), jaar, soort)
    return specialismen


def lees_parquet_sets(schrijver):
    try:
        import pyarrow.parquet as pq
    except ImportError:
        print("pyarrow ontbreekt: kleinere sets overgeslagen (pip install pyarrow)", file=sys.stderr)
        return
    for naam, (vkol, akol, bron) in PARQUET_SETS.items():
        print(f"{naam}…", file=sys.stderr)
        with open_url(f"https://huggingface.co/api/datasets/UMCU/{naam}/parquet/default/train") as resp:
            bestanden = json.load(resp)
        for url in bestanden:
            with open_url(url) as resp:
                tabel = pq.read_table(io.BytesIO(resp.read()), columns=[vkol, akol])
            for vraag, antwoord in zip(tabel.column(vkol).to_pylist(), tabel.column(akol).to_pylist()):
                soort = onderwerp(normaliseer(vraag or ""))
                if soort:
                    schrijver.voeg_toe(vraag, antwoord, "", bron, "", "", "", soort)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--uit", default="kennisbank.sqlite")
    p.add_argument("--max-miriad", type=int, default=0, help="alleen de eerste N regels van MIRIAD (0 = alles)")
    p.add_argument("--zonder-miriad", action="store_true")
    a = p.parse_args()

    db = maak_db(a.uit)
    schrijver = Schrijver(db)
    specialismen = {} if a.zonder_miriad else lees_miriad(schrijver, a.max_miriad)
    lees_parquet_sets(schrijver)
    schrijver.flush()

    print("Index optimaliseren…", file=sys.stderr)
    db.execute("INSERT INTO kennis(kennis) VALUES('optimize')")
    totaal = db.execute("SELECT count(*) FROM kennis").fetchone()[0]
    bronnen = sorted({b for (b, _) in schrijver.tellers})
    meta = {
        "schema": SCHEMA_VERSIE,
        "gebouwd": time.strftime("%Y-%m-%d"),
        "aantal": str(totaal),
        "bronnen": json.dumps(bronnen, ensure_ascii=False),
        "tellers": json.dumps({f"{b} / {s}": n for (b, s), n in sorted(schrijver.tellers.items())}, ensure_ascii=False),
    }
    db.executemany("INSERT INTO meta VALUES (?,?)", meta.items())
    db.commit()
    db.execute("VACUUM")
    db.close()

    print(f"\nKlaar: {a.uit} — {totaal:,} vraag/antwoord-paren, {os.path.getsize(a.uit) / 1e6:.0f} MB")
    for (b, s), n in sorted(schrijver.tellers.items()):
        print(f"  {b:45s} {s:12s} {n:>8,}")
    if specialismen:
        print("\nSpecialismen in MIRIAD (top 25):")
        for s, n in sorted(specialismen.items(), key=lambda x: -x[1])[:25]:
            print(f"  {s:40s} {n:>9,}")


if __name__ == "__main__":
    main()
