# Local LLM/AI – uitleg in het Nederlands

Dit document legt uit wat de app doet, welke schermen en functies er zijn en hoe de code in elkaar zit.
Alles hieronder is gebaseerd op wat in de code staat (niet op beloftes uit de README).
Waar de README iets anders zegt dan de code, staat dat er apart bij.

---

## 1. Wat doet de app en voor wie?

**Local LLM/AI** is een Android-app waarmee je een AI-taalmodel (een "LLM", zoals Qwen, DeepSeek of Gemma)
**op je eigen telefoon** draait. Je chat met het model zoals met een chatbot, maar het model rekent
op het toestel zelf. Na het downloaden van een model is geen internet nodig, behalve als je zelf
"Web Search" aanzet.

De app is bedoeld voor mensen die:

- privé met een AI willen chatten, zonder dat gesprekken naar een cloudserver gaan;
- een redelijk moderne Android-telefoon hebben (ARM64; de modellen vragen 2 tot 8 GB RAM);
- het niet erg vinden om eerst een groot modelbestand (ongeveer 0,5 tot 3,7 GB) te downloaden.

De app werkt niet op een emulator (de code geeft dan de melding dat een fysiek ARM64-toestel nodig is).

---

## 2. Schermen en functies

De app heeft drie schermen: **Chat** (startscherm), **Modelbeheer** ("LOCAL LLM MANAGER") en
**Instellingen** ("APP SETTINGS"). Alle teksten in de app zijn Engels.

### 2.1 Chatscherm (startscherm)

**Bovenbalk**

- Menuknop (☰): opent de zijlade met gesprekken.
- Titel "Local LLM/AI" met daaronder een gekleurd statuslabel:
  - groen: naam van het geladen model plus de gebruikte rekeneenheid (NPU, GPU of CPU);
  - geel: "Loading Model..." (model wordt geladen);
  - rood: "Engine Error";
  - grijs: "No model (Offline)".
  Tik op het label om naar Modelbeheer te gaan.
- Als er berichten zijn: **Delen** (stuurt het hele gesprek als tekst via het Android-deelmenu,
  in de vorm `[User]: …` / `[AI]: …`) en **Wissen** (prullenbak, maakt het huidige gesprek leeg).
- Knop naar **Modelbeheer** en knop naar **Instellingen**.

**Zijlade "Conversations" (gesprekken)**

- Knop **New Chat** begint een nieuw gesprek.
- Lijst met alle gesprekken, met titel en aantal berichten. Tik om te wisselen.
- Per gesprek een potlood (**Rename**: titel wijzigen in een venster) en een prullenbak
  (**Delete**: verwijderen na bevestiging "This action cannot be undone").
- Onderaan knoppen naar **Models** en **Settings**.
- Een nieuw gesprek krijgt automatisch een titel: de eerste regel van je eerste bericht
  (maximaal 28 tekens, daarna "...").

**Leeg gesprek (welkomstscherm)**

- "Welcome to Local LLM/AI" met korte uitleg.
- Is er een model geladen? Dan zie je drie voorbeeldvragen om aan te tikken
  (programmeren, natuurkunde, ontbijtideeën).
- Is er nog geen model? Dan zie je "Local LLM Required" met een knop
  "Go to Downloader" (geen modellen op het toestel) of "Select & Load Model" (wel bestanden, nog niet geladen).
- Bij een fout: "Local LLM Engine Error" met de foutmelding en een knop "Manage Models".

**Berichten**

- Jouw berichten en de antwoorden van de AI staan als tekstballonnen onder elkaar.
- Antwoorden verschijnen woord voor woord (streaming). Terwijl je wacht op het eerste woord zie je drie bewegende puntjes.
- De lijst scrolt mee met het antwoord, maar alleen als je al onderaan stond.
- **Denkproces**: bij "redeneermodellen" (zoals DeepSeek-R1 en Qwen 3) zet het model zijn gedachten tussen
  `<think>` en `</think>`. De app toont dat in een inklapbaar blok "Thinking Process"
  ("Thinking in progress…" tijdens het schrijven).
- **Kopiëren**: onder een afgerond AI-antwoord staat een kopieerknop.
- **Bronnen**: als er op internet is gezocht, staat onder het antwoord een inklapbaar blok
  "Sources (n)" met de verstuurde zoekvraag en klikbare links.
- **Bijlagen in een bericht**: afbeelding wordt getoond; video en document tonen een kaartje
  ("Tap to play" / "Tap to open") dat het bestand opent in een andere app.
  Uit bijlagen gehaalde tekst staat in een inklapbaar blok "Extracted Text (OCR)".

**Invoerbalk** (alleen zichtbaar als er een model geladen is; anders een knop
"Download or Load local LLM to start Chatting")

- Tekstveld "Ask Local LLM/AI...".
- **Paperclip / bijlage**: opent een menu "Add to Conversation" met:
  - **Attach Image** – afbeelding kiezen; de app leest er offline tekst uit (OCR met Google ML Kit).
  - **Attach Video** – video kiezen.
  - **Attach Document** – bestand kiezen (zie 3.2 voor welke soorten).
  Boven de invoer zie je een voorbeeld van de bijlage met de status
  ("Processing attachment...", "Extracted N characters", "Ready to send" of een fout) en een kruisje om te verwijderen.
- **Microfoon**: spraak omzetten naar tekst via de spraakherkenning van Android (vraagt de eerste keer
  toestemming voor de microfoon). Gebruikt de taal van je telefoon. Tik nogmaals om te stoppen.
- **Wereldbol (Web Search)**: wisselt per bericht tussen Off → Auto → Always.
  De eerste keer dat je zoeken aanzet, verschijnt een privacywaarschuwing
  ("Web Search Privacy Disclosure") met de keuze "Enable Search" of "Keep Offline".
  Tijdens het zoeken zie je "Searching web for …".
- **Verzenden** (pijl) of, tijdens het genereren, **Stop** (stopt het antwoord).

### 2.2 Modelbeheer ("LOCAL LLM MANAGER")

- Uitleg dat modellen in de app-opslag worden bewaard en offline blijven.
- **Filterknoppen**: All, ⚡ Turbo, 🧠 Reasoning, 👁️ Vision, 4GB RAM, Uncensored, 💻 Coding.
- **Modelkaarten** ("Recommended Mobile Presets") per model met naam, beschrijving, grootte en benodigd RAM.
  Afhankelijk van de situatie zie je:
  - **Download (grootte)** – start het downloaden;
  - tijdens downloaden een voortgangsbalk "Downloading: N%" en **Cancel**;
  - bij een mislukte download "Download Failed" met **Retry Download** en **Dismiss**;
  - na het downloaden "Ready Offline" met **Load Model** en **Delete**;
  - is het model actief, dan het label **ACTIVE** en een knop **Open Chat**;
  - tijdens het laden "Loading…" met de huidige stap (bijv. "Compiling GPU shaders…") en een annuleerknop;
  - na eerder laden een schatting van de laadtijd ("⚡ Load: ~… (device est.)").
- **Verwijderen** vraagt eerst om bevestiging en noemt hoeveel ruimte vrijkomt.
- **Sideloaded Custom Models**: eigen modelbestanden (.litertlm of .task) die niet in de lijst staan,
  met een knop om ze te verwijderen.
- **Import Local Model File**: knop "Select & Import File" om een modelbestand van je telefoon te kopiëren
  naar de app, met voortgangsbalk en een melding of het gelukt is.

### 2.3 Instellingen ("APP SETTINGS")

1. **Hugging Face Authentication** – een Hugging Face-token invullen (verbergen/tonen), controleren
   ("Validate Access"), opslaan ("Save Token") en verwijderen ("Remove Saved Token").
   Status "Configured ✓" of "Not Configured". Met een link naar `huggingface.co/settings/tokens`.
   Het token wordt versleuteld opgeslagen. (Let op: in de huidige modellijst heeft geen enkel model een token nodig.)
2. **Online Web Search** – met privacywaarschuwing.
   - *Trigger Mode*: Off (nooit), Auto (de app bepaalt of actuele informatie nodig is), Always (altijd zoeken).
   - *Search Provider*:
     - Built-in Free (DuckDuckGo & Wikipedia) – geen sleutel nodig;
     - Brave Search (aanbevolen) – eigen API-sleutel nodig;
     - Serper (Google Search) – eigen API-sleutel nodig.
   - Veld voor de API-sleutel (opslaan/verwijderen) bij Brave of Serper.
   - *Max Search Results*: schuifje van 3 tot 8 bronnen.
   - **Test Search Connection** – test het zoeken en toont het resultaat.
   - Alleen de korte zoekvraag gaat naar internet; het gesprek zelf niet. Plak je een webadres in je bericht,
     dan haalt de gratis variant de tekst van die pagina op via Jina Reader (r.jina.ai).
3. **Appearance** – thema: System, Light, Dark of Amoled Dark.
4. **Hardware Acceleration Backend** – kiezen waarop het model rekent:
   Auto (NPU → GPU → CPU, aanbevolen), GPU Only (Vulkan), NPU Only (alleen Snapdragon met Hexagon-chip;
   anders uitgeschakeld) of CPU Only.
5. **System Diagnostics & Hardware** – toont processor (SoC), of er een Hexagon NPU is, NPU-status,
   Vulkan-ondersteuning, hoeveelheid RAM en processorarchitectuur.
6. **Chat Configuration**
   - *Context Window Limit*: schuifje 4 tot 12 beurten (hoeveel recente berichten het model meekrijgt; standaard 6).
   - *Custom System Instructions*: de basisinstructie voor de AI aanpassen (knop "Apply Instructions").
     Standaard (Engels): "You are Local LLM/AI, a helpful, intelligent offline AI …".
7. **Inference & Generation Parameters**
   - Temperature 0,0–1,5 (standaard 0,7) – hoe creatief/willekeurig;
   - Top-P 0,1–1,0 (standaard 0,9);
   - Top-K 1–100 (standaard 40);
   - Repetition Penalty 1,0–1,5 (standaard 1,1) – minder herhaling;
   - **Reasoning / Thinking Mode** aan/uit – of redeneermodellen hun denkproces mogen tonen;
   - **Reset Defaults**.

### 2.4 Op de achtergrond

- **Downloaden in de achtergrond**: een Android-dienst met melding ("Downloading Model") houdt de download
  gaande, ook als het scherm uit gaat. Een afgebroken download gaat verder waar hij was.
  Na afloop wordt het bestand gecontroleerd (SHA-256-controlecijfer) voordat het gebruikt wordt.
- **Laden van het model**: de app probeert (bij "Auto") eerst de NPU, dan de GPU, dan de CPU.
  Op eenvoudige GPU's (bijv. Adreno 610) slaat Auto de GPU over. Als de NPU een keer niet werkt voor
  een model, onthoudt de app dat. Na 75 seconden GPU-voorbereiding geeft de app het op en valt terug.
- **Vriendelijke foutmeldingen**: technische fouten worden vertaald naar uitleg, zoals
  "Not enough device/GPU memory …" of "The model file appears to be incomplete or corrupted …".
- **Meetgegevens**: de app meet intern de tijd tot het eerste woord en het aantal woorden per seconde,
  maar dit wordt in de huidige schermen **niet** getoond (de README zegt van wel).
- **Toestemmingen** (uit AndroidManifest): internet, voorgronddienst/datasynchronisatie, meldingen,
  microfoon, en "wake lock" (telefoon wakker houden tijdens downloaden).

---

## 3. Ondersteunde modellen en bestandstypen

### 3.1 Ingebouwde modellen (bestand `data/ModelPreset.kt`)

Alle modellen worden gedownload van Hugging Face in het `.litertlm`-formaat.

| Model | Grootte | Min. RAM | Context | Afbeeldingen begrijpen | Kenmerk |
|---|---|---|---|---|---|
| Qwen 3 0.6B (Pocket Turbo) | 497 MB | 2 GB | 2.048 tokens | nee | Turbo, redeneren |
| Qwen 2.5 1.5B Instruct | 1,60 GB | 4 GB | 4.096 | nee | Meertalig |
| Qwen 3 1.7B (Dynamic INT4) | 977 MB | 4 GB | 4.096 | nee | Redeneren |
| Qwen 2.5 Coder 1.5B Instruct | 1,12 GB | 4 GB | 4.096 | nee | Programmeren |
| DeepSeek-R1 Distill Qwen 1.5B | 1,83 GB | 6 GB | 4.096 | nee | Redeneren (denkproces standaard aan) |
| Google Gemma 4 E2B Instruct | 2,59 GB | 6 GB | 8.192 | **ja** | Vision, redeneren |
| Gemma 4 E2B Uncensored MAX | 2,55 GB | 6 GB | 8.192 | nee* | Uncensored |
| SuperGemma 4 E4B Abliterated | 3,65 GB | 8 GB | 8.192 | nee* | Uncensored |
| Qwen 3 4B (Hybrid Reasoning) | 2,66 GB | 8 GB | 2.048 | nee | Redeneren |
| Google Gemma 4 E4B Instruct | 3,66 GB | 8 GB | 8.192 | **ja** | Vision, redeneren |

\* In de README staan deze twee als "Vision: Yes", maar in de code staat `supportsVision = false`
("vision unconfirmed until tested").

Daarnaast kun je **zelf een modelbestand importeren** (`.litertlm` of `.task`).
Let op: de tekst in het scherm noemt ook `.bin`, maar de code weigert `.bin`-bestanden bij importeren.

### 3.2 Bijlagen in de chat

| Soort | Wat de app ermee doet |
|---|---|
| Afbeelding | Tekst eruit halen met OCR (offline, Google ML Kit). Bij een model dat afbeeldingen begrijpt (Gemma 4 E2B/E4B) gaat ook de afbeelding zelf (verkleind tot max. 1024 px) naar het model. |
| Video | Bij een model dat afbeeldingen begrijpt: één beeld uit het midden van de video wordt naar het model gestuurd. Anders alleen de bestandsnaam. |
| PDF | De eerste **3 pagina's** worden als afbeelding gemaakt en met OCR omgezet naar tekst. |
| Ander bestand (bijv. .txt, .md, code) | Gelezen als tekst, maximaal 24.000 tekens. |

Bij niet-Gemma-modellen wordt de uitgelezen tekst in je bericht gezet en zo nodig ingekort.

---

## 4. App bouwen en installeren

**Benodigd** (volgens `app/build.gradle.kts`, `gradle/` en de CI-workflow):

- JDK 17 (de build vraagt een Java 17-toolchain);
- Android SDK met platform 36 (`compileSdk = 36`);
- internet voor het downloaden van Gradle (versie 9.8.2 via de wrapper) en de bibliotheken.

**Bouwen** (in de hoofdmap van de repo):

```bash
./gradlew assembleDebug
```

Het resultaat staat in `app/build/outputs/apk/debug/app-debug.apk`.
Voor een release-versie: `./gradlew assembleRelease` (wordt ondertekend met de debug-sleutel,
resultaat in `app/build/outputs/apk/release/`). Tests draaien met `./gradlew testDebugUnitTest`.

**Automatisch bouwen**: bij elke push of pull request naar `main` bouwt GitHub Actions
(`.github/workflows/android.yml`) de debug-APK, draait de tests en lint, en zet de APK als download bij de workflow-run.

**Installeren**: zet de APK op een Android-telefoon (minimaal Android 7.0 / API 24, processor arm64-v8a)
en installeer hem, of gebruik `adb install app-debug.apk` met een USB-kabel.
Open de app, ga naar Modelbeheer, download een model en tik op **Load Model**.

---

## 5. Technische opzet in grote lijnen

De app is geschreven in **Kotlin** met **Jetpack Compose** (Material 3) voor de schermen.
Het rekenen gebeurt met Google's **LiteRT-LM** (`litertlm-android` 0.18.1).
Overige bibliotheken: OkHttp (downloaden en zoeken), ML Kit (OCR), Coil (afbeeldingen tonen),
kotlinx-serialization (JSON), AndroidX Security Crypto (versleutelde opslag).

```
app/src/main/
├── AndroidManifest.xml          toestemmingen, startactiviteit, downloaddienst
├── jniLibs/arm64-v8a/           meegeleverde native bibliotheken (LiteRT, Qualcomm QNN voor de NPU)
├── res/                         pictogrammen, thema, app-naam (strings.xml bevat alleen "Local LLM/AI")
└── java/com/example/auralocalai/
    ├── MainActivity.kt          start de app, vraagt meldingstoestemming, zet het thema
    ├── Navigation.kt            wisselen tussen de drie schermen
    ├── ui/
    │   ├── LlmViewModel.kt      het "brein" van de app: status, berichten versturen, bijlagen, instellingen
    │   └── screens/
    │       ├── ChatScreen.kt          chatscherm, zijlade, bijlagen, spraak
    │       ├── ModelManagerScreen.kt  modelbeheer
    │       └── SettingsScreen.kt      instellingen en diagnose
    ├── data/
    │   ├── ModelPreset.kt             lijst met ingebouwde modellen
    │   ├── LlmInferenceEngine.kt      model laden (NPU → GPU → CPU) en tekst genereren
    │   ├── ModelDownloader.kt         downloaden met hervatten en controle
    │   ├── ModelDownloadService.kt    achtergronddienst met melding
    │   ├── ModelSafetyValidator.kt    controleert of een modelbestand geldig is
    │   ├── ChatRepository.kt          gesprekken opslaan
    │   ├── SocDetector.kt             herkent de processor en of er een NPU is
    │   ├── SearchIntentDetector.kt    bepaalt (bij "Auto") of er gezocht moet worden
    │   ├── SearchContextBuilder.kt    zet zoekresultaten netjes in de prompt
    │   ├── WebSearch.kt / WebSearchClient.kt   algemene zoeklogica
    │   └── providers/                 Free (DuckDuckGo/Wikipedia/Jina), Brave, Serper
    ├── security/                versleutelde opslag van token en API-sleutel
    └── theme/                   kleuren, lettertypen, thema's
```

**Waar wordt wat opgeslagen?**

- Modellen: in de externe app-map, submap `models` (`getExternalFilesDir/models`).
- Gesprekken: in de interne app-map: `chat_sessions.json` (lijst) en `sessions/<id>.json` (per gesprek).
- Instellingen: Android SharedPreferences (`app_settings`).
- Hugging Face-token en zoek-API-sleutel: versleuteld (AES-256 via de Android Keystore);
  lukt dat niet, dan valt de app terug op gewone opslag en onthoudt dat.

**Overige mappen en bestanden**

- `app/src/test/` – unit-tests (o.a. downloader, chatopslag, zoeklogica, `<think>`-verwerking).
- `app/src/androidTest/` – tests op een echt toestel die de gedownloade modellen laden en uitproberen.
- `scripts/verify_model_catalog.py` – controleert de modellijst tegen de bestanden op Hugging Face.
- `tools/validate_device.ps1` – PowerShell-script dat via ADB controleert of een aangesloten toestel geschikt is (processor, RAM, GPU, NPU) en de debug-APK kan installeren.
- `push_release.ps1`, `release_notes.md` – hulpmiddelen voor het uitbrengen van een release.
