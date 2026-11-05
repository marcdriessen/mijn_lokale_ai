# Datasets op Hugging Face (gecontroleerd op 4 nov 2026)

Doel: bruikbare sets voor de **Vraagbaak** (vraag/antwoord), de **Taalcoach** (NL/BG-zinsparen)
en **Leerkaarten**. Alle sets hieronder bestaan echt; kolommen en aantallen zijn gecontroleerd
via de Hugging Face "dataset viewer".

Let op:
- De meeste Nederlandse medische sets zijn **machinaal vertaald** uit het Engels (MariaNMT of GPT).
  De taal is soms houterig en de inhoud is Amerikaans (geen Nederlandse richtlijnen).
- Er bestaat vrijwel **niets over verslavingszorg in het Nederlands**. Eigen documenten
  (Trimbos, NHG, KNMP, GGZ-standaarden) in de bibliotheek blijven de beste bron.
- Gebruik deze sets als oefen- en zoekmateriaal, **niet als klinisch advies**.
- Licentie "gpl-3.0" of "cc-by" = vrij te gebruiken voor eigen gebruik. "unknown"/"other" = eerst de kaart lezen.

## 1. Taalcoach (Bulgaars ⇄ Nederlands)

| Dataset | Wat | Rijen | Kolommen | Oordeel |
|---|---|---|---|---|
| `qanastek/EMEA-V3`, config **bg-nl** | Zinnen uit Europese bijsluiters/EPAR's (geneesmiddelen) | 354.000 | `translation.bg`, `translation.nl` | **Beste keus.** Medische taal in beide talen, bv. "Abilify kan zowel met als zonder voedsel worden ingenomen." |
| `Helsinki-NLP/europarl`, config **bg-nl** | Toespraken Europees Parlement | 388.000 | `translation.bg`, `translation.nl` | Goed Bulgaars, maar formeel/politiek. |
| `Helsinki-NLP/tatoeba` (bul–nld) | Korte alledaagse zinnen | klein | – | Ideaal niveau (A1–B1), maar viewer werkt niet; zelf downloaden via tatoeba.org is makkelijker. |

## 2. Vraagbaak / Leerkaarten (Nederlands, medisch)

| Dataset | Wat | Rijen | Kolommen | Oordeel |
|---|---|---|---|---|
| `UMCU/MedicalFlashCards_Dutch_translated_with_MariaNMT` | Korte medische vraag + antwoord | 32.900 | `input` (vraag), `output` (antwoord) | **Direct bruikbaar als leerkaarten.** |
| `UMCU/MedQA_SymptomDisease_small_Dutch` | "Wat zijn de symptomen van …?" | 10.100 | `instruction`, `answer` | Goed voor leerkaarten/vraagbaak. |
| `UMCU/MedQA_Dutch` | Casusvragen (Amerikaans artsexamen) met antwoord | 14.400 | `question`, `answer` | Lange casussen; licentie onbekend. |
| `UMCU/MedicalMMLU_Dutch_translated_with_MariaNMT` | Meerkeuzevragen (A/B/C/D) | 3.600 | `input`, `output` | Voor een quiz-functie. |
| `UMCU/DutchMedicalTermPairs` | Synoniemen van medische termen | 10 miljoen | `term1`, `term2` | Handig om vaktaal ⇄ gewone taal te oefenen; filteren nodig. |
| `UMCU/dutch_guidelines` | Teksten uit Nederlandse richtlijnen/codering | 364.000 | `text`, `src` | Als zoekbibliotheek; geen vraag/antwoord. |

## 3. Psycho-educatie (eenvoudige taal)

| Dataset | Wat | Rijen | Kolommen |
|---|---|---|---|
| `BramVanroy/chatgpt-dutch-simplification` | Moeilijke zin → makkelijke zin | 1.300 | `source`, `target` |
| `igornishka/dutch-municipal-sentence-simplification` | Gemeentetaal vereenvoudigd | – | – |

Nuttig als voorbeelden voor B1/A2-hand-outs (licentie cc-by-nc-sa: alleen niet-commercieel).

## 4. Zoektermen die werken op huggingface.co/datasets

Zet links het filter **Languages** op `nl` (of `bg`) en zoek dan op:

- `UMCU` — de Utrechtse groep met bijna alle Nederlandse medische sets
- `dutch medical`, `medqa dutch`, `flashcards dutch`
- `EMEA` (bijsluiters, veel talenparen), `europarl`, `opus` + `bg-nl`
- `bulgarian medical`, `bulgarian` (filter Languages = bg)
- `dutch simplification`, `B1`
- Engels (zelf vertalen nodig): `MedQuAD`, `mental health counseling`, `addiction`, `substance use`

Niet zinvol bleek: `CEFR dutch`, `dutch grammar`, `medication dutch` (geen resultaten);
`addiction` levert vooral Engelse statistiek of social-media-verslaving op.

## 5. Hoe dit in de app past (open punt "Dataset importeren")

De app zou een CSV/JSON/Parquet-bestand kunnen inlezen met een **kolomkeuze**:

| Doel | Kolom A | Kolom B | Voorbeeld-set |
|---|---|---|---|
| Taalcoach | `translation.nl` | `translation.bg` | EMEA-V3 bg-nl |
| Leerkaarten | `input` | `output` | MedicalFlashCards_Dutch |
| Vraagbaak | `instruction` / `question` | `answer` | MedQA_SymptomDisease_small_Dutch |

Downloaden kan als Parquet via de knop "Files and versions" op de datasetpagina, of als CSV
via de viewer (`https://datasets-server.huggingface.co/rows?dataset=...`). Neem een **steekproef**
(bv. 500–2.000 rijen): de volledige sets zijn te groot voor de telefoon en niet nodig.

## 6. Kennisbank GGZ & verslaving (gebouwd)

Uit de UMCU-sets is een offline kennisbank gemaakt voor de Vraagbaak:

- Script: `tools/maak_kennisbank.py` (stdlib + `pyarrow`). Proefje: `--max-miriad 100000`.
- Workflow: `.github/workflows/kennisbank.yml` draait bij een wijziging van het script (of handmatig) en zet
  `kennisbank.sqlite.gz` op de release **kennisbank**.
- Inhoud: uit **MIRIAD4.4.nl** (4,4 mln paren, met artikeltitel en link) alles uit de specialismen *Psychiatry* en
  *Psychology & Behavioral Health*, plus vragen uit andere specialismen waarvan de vraag of titel over middelen,
  verslaving of psychiatrische beelden gaat (dierproeven overgeslagen). Daarnaast dezelfde filter op Medical
  Flashcards, WikiDoc, WikiDoc patiëntinformatie, MedQA symptomen en MediQA.
- In de app: Vraagbaak → bibliotheek-icoon → **Kennisbank** → Downloaden (of Bestand importeren). Daarna staat de
  knop "Kennisbank" aan en komen treffers als bron [n] in het antwoord.
- Leerkaarten: Werk → Leerkaarten → "Uit de kennisbank": onderwerp kiezen of willekeurige mix; per stapel
  een quiz met 4 antwoordmogelijkheden.
- Trefwoorden aanpassen = de lijsten `VERSLAVING` en `PSYCHIATRIE` in het script; na een push bouwt de workflow
  automatisch een nieuwe versie.
