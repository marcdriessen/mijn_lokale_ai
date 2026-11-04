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

De app heeft vijf schermen: **Chat** (startscherm), **Nieuwsbriefings**, **Vraagbaak GGZ & verslaving**, **Modelbeheer** ("LOCAL LLM MANAGER") en
**Instellingen** ("APP SETTINGS"). De app is er in het Engels, Nederlands en Bulgaars. Standaard volgt hij de taal van je
telefoon; bij Instellingen → Appearance kun je ook zelf kiezen. Het model krijgt de opdracht om in de taal van de app
te antwoorden (tenzij jij duidelijk in een andere taal schrijft). In dit document staan de Engelse namen.

Het uiterlijk is rustig en redactioneel: warm gebroken wit (of donker), donkergroene tekst en accenten, en
serif-koppen zoals in een krant. Modelbeschrijvingen staan in de gekozen taal. In de chat staan antwoorden als
gewone tekst over de volle breedte en je eigen berichten in een zachte ballon.

**Dashboard en mini-apps**: de app opent op een **dashboard** met een begroeting, een kaart van het **actieve
model** (gedeeld door alle mini-apps; tik om te kiezen/laden, knop om te ontladen) en tegels voor de mini-apps:
**Chat, Nieuws, Werk, Vertalen, Taalcoach, Modellen en Instellingen**. Elke mini-app heeft zijn eigen kop (kleine
bovenregel, grote titel, terugpijl naar het dashboard, ronde knoppen) en eigen tabbladen. Teksten, links en foto's
die je vanuit een andere app **deelt naar Local LLM/AI** openen in Vertalen.

### 2.0 Privacy & beveiliging (Instellingen)

- **App-slot**: vingerafdruk of schermvergrendeling bij het openen.
- **Privacymodus**: zet alle internetfuncties uit (webzoeken, nieuws, PubMed, Brave).
- **Automatisch anonimiseren** (standaard aan): namen (na aanhef/rol of met voorletters), BSN (met elfproef),
  datums, telefoonnummers, e-mail, postcodes, adressen, IBAN en cliënt-/dossiernummers worden vervangen door labels
  zoals [NAAM] en [BSN]. Als er een model geladen is, zoekt dat model nog naar overgebleven namen, die daarna ook
  worden vervangen (het model herschrijft de tekst niet). Rapportages en de bibliotheek worden altijd
  geanonimiseerd; voor chatbijlagen en vertalen geldt deze schakelaar. Controleer altijd zelf: geen automatisch
  systeem is 100% waterdicht.
- **Back-up**: gesprekken, briefings, bibliotheek en leerkaarten in één zip-bestand (zonder modellen), en terugzetten.

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
- **Geheugenkaart** bovenaan: hoeveel werkgeheugen (RAM) er nu vrij is, ververst elke 2 seconden.
  Is er een model geladen, dan staat hier de knop **Model ontladen**.
- **Model ontladen** staat ook op de kaart van het actieve model, naast **Open Chat**.
- **Wisselen van model**: laad je een ander model terwijl er al een geladen is, dan ontlaadt de app eerst het oude model
  en wacht tot Android het geheugen weer vrij meldt. Pas daarna wordt gecontroleerd of het nieuwe model past.
- **Te weinig geheugen**: lukt laden niet door geheugentekort, dan zie je hoeveel GB vrij is en hoeveel nodig is,
  met de knop **Toch proberen** (op eigen risico: Android kan de app sluiten als het geheugen opraakt).
  Op toestellen met 8 GB of meer hoeft 70% van de berekende hoeveelheid vrij te zijn.
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
3. **Appearance** – thema: System, Light, Dark of Amoled Dark; en taal: taal van het toestel, English of Nederlands
   (het scherm wordt direct opnieuw opgebouwd in de gekozen taal).
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
   - Repetition Penalty 1,0–1,5 (standaard 1,0) – minder herhaling;
   - **Reasoning / Thinking Mode** aan/uit – of redeneermodellen hun denkproces mogen tonen;
   - **Reset Defaults**.

### 2.4 Nieuwsbriefings

Tabblad **Nieuws**. Opgezet als een moderne nieuwsapp (geïnspireerd op NU.nl, DW en Ground News):

- **Kop**: datum + "Nieuws", met ronde knoppen voor **briefing bewerken**, **nieuwe briefing** (+) en een **menu**
  (nieuws opnieuw ophalen, voorlezen, delen, eerdere briefings, bronnen).
- **Briefings als pillen** (gekozen = donker gevuld; klokje = automatisch gepland).
- **Jouw selectie** met rechts **Onderwerpen bewerken**, de status ("bijgewerkt … geleden · volgende uitvoering …") en
  de knop **Genereer briefing / Nu bijwerken**.
- **IN FOCUS**-regel om op onderwerp te filteren (Alles, Ggz, Verslavingszorg, …).
- **Hoofdbericht** als grote foto met de kop erover, daarna **kaarten** (onderwerp en bron, vette kop met vierkante foto,
  bron · tijd en een menu met "lees artikel" en "delen"), en de rest als **Net binnen**-tijdlijn ("26 min. geleden in
  Ggz", kop en foto).
- Tik op een bericht voor de **detailpagina** met grote foto, vertaalde kop (plus originele kop), samenvatting,
  **"Betekenis voor jouw werk"**, **Lees het artikel bij …**, **Vraag verder** en **Ground News**.
- **Vertalen** gebeurt in twee stappen: eerst vertaalt **Google ML Kit** (offline op de telefoon; taalpakketten van
  ~30 MB worden één keer gedownload) de kop en de korte tekst van elk Engels of Bulgaars bericht naar de taal van de
  briefing. Daarna schrijft het geladen model (als dat er is) de samenvatting en de betekenis, op basis van de vertaalde
  tekst. Zonder model krijg je dus toch Nederlandse koppen en tekst.
- **Op de achtergrond klaar**: bij een geplande briefing (bijv. 08:00) begint de app **30 minuten eerder**, haalt het
  nieuws op, vertaalt het, laadt zelf het **laatst gebruikte model** om samen te vatten (als het geheugen dat toelaat)
  en stuurt een melding zodra de briefing **helemaal klaar** is. In de app zie je een nieuwe briefing ook pas als hij
  af is; tot die tijd blijft de vorige staan.
- **Taal van de briefing** per briefing in te stellen (Nederlands, English, Български of de taal van de app; standaard
  Nederlands voor de ggz-briefing). Het model vertaalt Engelse berichten naar die taal. Berichten die (nog) niet door
  het model zijn verwerkt, krijgen het label "origineel".
- **Meerdere briefings (profielen)** bovenaan als knoppen, plus **Nieuwe briefing**. Standaard:
  "Dagelijks nieuws over ggz en verslavingszorg" en "Wereldnieuws".
- **Artikelkaart**: afbeelding (uit de feed of de artikelpagina) met onderwerp-label, bron en datum, een titel en
  korte samenvatting door het lokale model, een blok **"Betekenis voor jouw werk"** (op basis van je instructies),
  **Lees het artikel bij …**, **Vraag verder** (chat) en **Ground News**.
- **Genereer briefing** / **nieuws opnieuw ophalen**, **voorlezen**, **delen** en **eerdere briefings** (klok-icoon).
- **Briefing bewerken** (potlood-icoon), opgezet als een geplande taak:
  - naam en **instructies** (vrije tekst, bijv. "toegespitst op mijn werk als psychiatrisch verpleegkundige");
  - **triggers**: automatisch uitvoeren aan/uit, **dagelijks / werkdagen / wekelijks** (met weekdag), **tijdstip**
    en **begindatum**; bovenaan staat wanneer de volgende uitvoering is;
  - **geavanceerd**: meldingen aan/uit, nieuws via **nieuwsfeeds, webzoeken of beide**, websites voor webzoeken
    (bijv. nos.nl, ggznieuws.nl; via Brave, telt mee voor de maandlimiet), aantal artikelen (3–12);
  - **onderwerpen** met zoekwoorden (aan/uit, bewerken, verwijderen, toevoegen, vertalen met AI naar NL/EN/BG).
- **Bronnen** (RSS-icoon, gedeeld door alle briefings): aan/uit, testen, verwijderen, toevoegen. Naast DW, Al Jazeera,
  Reuters en AP zijn er Nederlandse bronnen: NOS, GGZ Nieuws, Skipr, Zorgvisie en Trimbos (via Google News) en een
  Nederlandse Google Nieuws-zoekfeed.
- **Op de achtergrond** haalt een geplande briefing alleen het nieuws op (via nieuwsfeeds) en stuurt een melding; het
  model schrijft de artikelen als je de briefing opent.

### 2.4b Nieuws: extra's

- **Weekoverzicht** (menu): het model maakt een overzicht van de briefings van de afgelopen 7 dagen.
- **Widget** op je startscherm met de koppen van de laatste briefing.

### 2.5 Werk (GGZ & verslaving)

De mini-app **Werk** heeft tabbladen: **Vraagbaak** (hieronder), **Psycho-educatie** (hand-outs voor cliënten op
B1/A2-niveau in NL/BG/EN), **Meetinstrumenten** (offline optellen voor CIWA-Ar, COWS en AUDIT, met de gebruikelijke
indeling; geen advies over doseringen), **Rapportage** (dicteren, scannen of een document inlezen: PDF (tekstherkenning op de telefoon,
max. 20 pagina's), Word (.docx), OpenDocument (.odt), RTF, HTML of tekstbestand (.txt, .md, .csv) →
automatisch geanonimiseerd → SOEP, SBAR of SOAP → desgewenst opslaan als Word-document; oude .doc-bestanden eerst
in Word als .docx opslaan; ook de bestandsnaam wordt geanonimiseerd) en **Leerkaarten** (uit je bibliotheek of eigen tekst, oefenen met een
Leitner-schema).

### 2.5a Vraagbaak GGZ & verslaving

Bereikbaar via de knop in de zijlade. Bedoeld voor verpleegkundigen en artsen in de psychiatrie, verslavingszorg en somatiek.

- **Waarschuwing** bovenaan: geen patiëntgegevens invoeren; antwoorden ondersteunen het professionele oordeel.
- Je stelt een vraag en kiest waar gezocht wordt (knoppen):
  - **Mijn bibliotheek**: eigen PDF-, Word- (.docx), ODT-, RTF-, HTML-, TXT- of Markdown-bestanden (bibliotheek-icoon). PDF's worden met OCR gelezen
    (max. 40 pagina's) en in tekstdelen opgeknipt; zoeken gebeurt offline op de telefoon;
  - **Vertrouwde sites** via Brave (één zoekopdracht per vraag, beperkt tot de ingestelde sites): GGZ Standaarden,
    Akwa GGZ, Trimbos, NISPA, Farmacotherapeutisch Kompas, Richtlijnendatabase, NHG en Vilans. Sites zijn aan/uit te
    zetten en toe te voegen. Werkt alleen als Brave bij Instellingen als zoekdienst met API-sleutel is ingesteld;
  - **PubMed** (gratis): het model maakt van je vraag Engelse zoektermen; titels en samenvattingen worden opgehaald.
- Het model antwoordt **alleen op basis van de gevonden bronnen**, met nummers zoals [1], en zegt het als de bronnen
  de vraag niet beantwoorden. Het noemt **nooit doseringen**; de knop **Farmacotherapeutisch Kompas** zoekt het
  onderwerp op die site.
- Onder het antwoord staan de genummerde bronnen (tik = tekst tonen en bron openen), plus **Vraag verder** (chat) en
  **Kopiëren**.
- **Teller** voor Brave-zoekopdrachten per maand met een instelbare limiet (standaard 1000); daarna zoekt de
  vraagbaak alleen nog in PubMed en je bibliotheek.
- **Eerdere vragen** (klok-icoon): de laatste 30 worden bewaard.
- Inhoud van de DSM-5-TR en Vilans-protocollen is niet vrij beschikbaar; die kun je alleen gebruiken door je eigen
  (toegestane) documenten aan je bibliotheek toe te voegen.

### 2.5b Vertalen

Offline vertalen met ML Kit naar Nederlands, Engels, Bulgaars, Pools, Duits, Frans, Spaans, Italiaans, Portugees,
Roemeens, Turks, Arabisch, Oekraïens of Russisch: getypte tekst, een **foto** (camera of galerij, tekstherkenning voor
Latijns schrift), een **gedeelde pagina/tekst/foto** of een **document** (PDF, Word .docx, ODT, RTF, HTML of
tekstbestand). Bij documenten blijven kopjes, lijsten en witregels op hun plek (regel voor regel vertaald); de
brontaal wordt één keer voor het hele document herkend.

- **Vertalen met AI-model**: trager, maar vaak natuurlijker (bijv. Nederlands → Bulgaars). Lange documenten worden in
  delen vertaald (tot ca. 24.000 tekens).
- **Opslaan als Word**: de vertaling wordt als .docx opgeslagen (met titel en doeltaal).
- Als **anonimiseren** aan staat, worden persoonsgegevens in documenten en foto's eerst vervangen.
- Daarna eventueel met AI: samenvatten, eenvoudig (B1) of een e-mail maken.

### 2.5c Taalcoach

Oefengesprekken in Bulgaars, Engels, Duits of Nederlands op niveau A1–B2, met situaties (kennismaken, winkel, dokter,
familie, reizen). De coach geeft een **correctie** met korte uitleg en een **antwoord** met een vraag; antwoorden
kunnen worden voorgelezen.

### 2.5d Chat: extra's

- **Snelkoppelingen** boven de invoer: samenvatten, naar Nederlands, eenvoudig (B1), e-mail, naar Bulgaars.
- **Spraakmodus** (spreker-icoon): antwoorden worden automatisch voorgelezen.
- **Modeladvies** in Modellen: welk model het best past bij jouw telefoon en taak.

### 2.6 Op de achtergrond

- **Downloaden in de achtergrond**: een Android-dienst met melding ("Downloading Model") houdt de download
  gaande, ook als het scherm uit gaat. Een afgebroken download gaat verder waar hij was.
  Na afloop wordt het bestand gecontroleerd (SHA-256-controlecijfer) voordat het gebruikt wordt.
- **Automatisch ontladen** (Instellingen → Chat Configuration): na 5, 15 of 30 minuten zonder gebruik wordt het
  model ontladen om geheugen vrij te maken (standaard uit).
- **Google Tensor (Pixel)**: Qwen-, DeepSeek- en Q8-modellen draaien bij "Auto" op de CPU, omdat ze op de Mali-GPU
  wartaal geven. Met "GPU Only" kun je de GPU nog testen.
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
├── res/                         pictogrammen, thema en alle teksten (values = Engels, values-nl = Nederlands, values-bg = Bulgaars)
└── java/com/example/auralocalai/
    ├── MainActivity.kt          start de app, vraagt meldingstoestemming, zet het thema
    ├── Navigation.kt            wisselen tussen de schermen
    ├── AppLocale.kt             taalkeuze in de app (toestel / English / Nederlands / Български)
    ├── ui/
    │   ├── LlmViewModel.kt      het "brein" van de app: status, berichten versturen, bijlagen, instellingen, geheugen
    │   ├── BriefingViewModel.kt logica van de dagelijkse briefing
    │   ├── MedicalViewModel.kt  logica van de vraagbaak (bibliotheek, Brave, PubMed, antwoord)
    │   └── screens/
    │       ├── ChatScreen.kt          chatscherm, zijlade, bijlagen, spraak
    │       ├── BriefingScreen.kt      dagelijkse briefing
    │       ├── MedicalScreen.kt       vraagbaak GGZ & verslaving
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
    │   ├── providers/                 Free (DuckDuckGo/Wikipedia/Jina), Brave, Serper
    │   ├── briefing/                  briefing: modellen, RSS-parser, zoekwoordfilter, ophalen, prompts, opslag, achtergrondtaak
    │   └── medical/                   vraagbaak: PubMed, bibliotheek-zoeken, prompts, opslag en Brave-teller
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
