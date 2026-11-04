# Local LLM/AI – hoe de app werkt

*Uitleg voor mensen en voor andere AI's. Stand: november 2026.*

Dit document beschrijft de Android-app "Local LLM/AI". Het gaat vooral over **hoe de app nieuws en
informatie verzamelt en verwerkt**. Aan het eind staat waar andere zoekmethodes kunnen worden
aangesloten.

---

## 1. Doel

De app is een **persoonlijke AI-assistent die op de telefoon zelf draait** (Pixel 8 Pro, 12 GB RAM),
gemaakt voor een psychiatrisch verpleegkundige in de Nederlandse verslavingszorg die ook Bulgaars
spreekt.

Uitgangspunten:

- **Privacy eerst.** Het taalmodel draait offline op de telefoon. Cliëntgegevens gaan nooit naar
  internet. Teksten die gedicteerd, gescand of ingelezen worden, worden automatisch
  **geanonimiseerd** (namen, BSN/ЕГН, datums, adressen, telefoonnummers, e-mail, IBAN,
  dossiernummers).
- **Eén model, veel mini-apps.** Op een dashboard staan mini-apps: Chat, Nieuws, Werk, Vertalen,
  Taalcoach, Modellen en Instellingen. Ze gebruiken allemaal hetzelfde geladen model; dat wordt maar
  één keer in het geheugen geladen.
- **Drie talen**: Nederlands, Engels en Bulgaars (ook Cyrillisch schrift).
- **Betrouwbaar boven slim.** Het model krijgt altijd de brontekst mee en mag alleen feiten daaruit
  gebruiken. Bronnen worden genummerd getoond. Er worden nooit doseringen genoemd.

## 2. Techniek in het kort

| Onderdeel | Gebruikt |
|---|---|
| Taalmodel | LiteRT-LM (Google) met o.a. Gemma 4 E2B/E4B en Qwen; draait op GPU, of automatisch op CPU als de GPU faalt |
| Vertalen (offline) | Google ML Kit Translate + Language ID (taalpakketten van ca. 30 MB, eenmalig gedownload) |
| Tekstherkenning | ML Kit (Latijns schrift); Tesseract met Bulgaarse data (Cyrillisch, ca. 13 MB eenmalig) |
| Spraak | Android SpeechRecognizer (bij voorkeur on-device/offline) en Text-to-Speech |
| Achtergrondtaken | WorkManager (dagelijkse briefing, ook als de app dicht is) |
| Documenten | Eigen lezer voor PDF (via tekstherkenning), Word (.docx), ODT, RTF, HTML, tekst en foto's; schrijft .docx |

Kleine modellen zijn in het Nederlands en Bulgaars zwakker dan in het Engels. Daarom heeft de chat
een keuze **"Via Engels"**. De vraag wordt offline naar het Engels vertaald, het model antwoordt in
het Engels, en het antwoord wordt teruggezet naar de taal van de app. Codeblokken en opmaak blijven
daarbij intact.

## 3. De mini-apps

| Mini-app | Wat het doet |
|---|---|
| **Chat** | Gesprekken met het lokale model. Bijlagen (foto, PDF, Word…) worden gelezen en geanonimiseerd. Je kunt dicteren en antwoorden laten voorlezen. Optioneel webzoeken (zie §5.3) en "Via Engels". |
| **Nieuws** | Dagelijkse, gepersonaliseerde nieuwsbriefing per profiel (zie §4). |
| **Werk** | *Vraagbaak* (vakvragen met bronnen, zie §5.1)<br>*Psycho-educatie* (hand-outs op B1/A2, NL/BG/EN)<br>*Meetinstrumenten* (CIWA-Ar, COWS, AUDIT met uitleg per score; de app telt alleen op en adviseert niet)<br>*Rapportage* (dicteren/scannen/document → anonimiseren → spelling verbeteren → SOEP/SBAR/SOAP → opslaan als Word)<br>*Leerkaarten* (uit eigen documenten) |
| **Vertalen** | Tekst, foto's, documenten en gedeelde webpagina's naar 14 talen. Offline met ML Kit, of nauwkeuriger met het AI-model. Opslaan als Word en voorlezen. |
| **Taalcoach** | Oefengesprekken (vooral Bulgaars) met verbetering, uitleg, antwoord en vertaling. Je kunt spreken en laten terugspreken. |

## 4. Nieuwsvergaring (mini-app "Nieuws")

### 4.1 Profielen

De gebruiker maakt **profielen**, bijvoorbeeld "Dagelijks nieuws over ggz en verslavingszorg" en
"Wereldnieuws". Een profiel bevat:

- **instructies** in gewone taal ("toegespitst op mijn werk als verpleegkundige…");
- **onderwerpen** met **trefwoorden** in NL, EN en BG. De app kan trefwoorden automatisch laten
  aanvullen door het model, met synoniemen in alle drie de talen;
- de **bronmodus**: `RSS`, `WEB` (zoeken op gekozen sites) of `BOTH`;
- het **maximum aantal artikelen** (1–15), de **taal** van de briefing, het **schema** (tijd,
  dagelijks/werkdagen/wekelijks, startdatum) en of er een melding komt.

### 4.2 Bronnen

**RSS/Atom-feeds** (de basis):

| Soort | Bronnen |
|---|---|
| Direct en gratis (internationaal) | DW, Al Jazeera, BBC World, The Guardian (World), France 24 |
| Direct en gratis (Nederland) | NOS, NOS Binnenland, NU.nl, NU.nl Gezondheid |
| Direct en gratis (Bulgarije) | Дневник, Novinite |
| Via Google Nieuws | Reuters, AP, GGZ Nieuws, Skipr, Zorgvisie, Trimbos |

Google Nieuws wordt gebruikt voor sites die zelf geen bruikbare feed hebben, met de vorm
`news.google.com/rss/search?q=site:<domein>+when:1d`.

**Zoekfeeds:** een feed-URL met `{q}` wordt per trefwoord ingevuld, bijvoorbeeld Google Nieuws
zoeken in NL, EN of BG. De app doet maximaal 12 zoekopdrachten per run.

**Eigen bronnen:** de gebruiker kan elke RSS-URL toevoegen, testen en aan/uit zetten. Wordt een
gewoon website-adres ingevoerd, dan zoekt de app zelf de aangekondigde feed (`<link rel="alternate">`).

**Toegevoegd na het integratieplan:** БТА (Bulgarije), Факти, Факти – България, IGJ (nieuws en
toezichtdocumenten), NISPA en GGZ Standaarden via Google Nieuws; Hacker News en XDA (standaard uit).

**Betaalmuur-filter (standaard aan):** artikelen van sites waar de meeste artikelen achter een
betaalmuur zitten, worden overgeslagen. Voorbeelden: NRC, Volkskrant, FD, Trouw, Telegraaf, AD,
NYT, WSJ, FT, Bloomberg, Economist en Капитал. Bij Google Nieuws-items leest de app de echte
uitgever uit het `<source url=…>`-veld.

### 4.3 Stappen bij het maken van een briefing

1. **Ophalen.** Alle actieve feeds worden tegelijk opgehaald (OkHttp, max. 2 MB per feed). Het
   resultaat wordt 3 uur bewaard; bij "vernieuwen" haalt de app alles opnieuw op. Bij bronmodus
   `WEB`/`BOTH` komen daar zoekresultaten bij (§4.4).
2. **Filteren.** Betaalde sites worden verwijderd.
3. **Selecteren in twee trappen.** Trap 1 (zonder model): een kop of samenvatting telt als treffer
   als een trefwoord van het onderwerp erin voorkomt, in welke taal dan ook; dubbelen worden
   samengevoegd, nieuwste eerst, onderwerpen om de beurt (tot 2× het maximum). Trap 2: het lokale
   model geeft in één korte opdracht elke kandidaat een relevantiescore 0–10 voor dit profiel; de
   beste worden gekozen. Zonder model of tijd blijft de volgorde van trap 1.
   **Zelfde gebeurtenis:** berichten van andere bronnen met (bijna) dezelfde kop worden aan het
   gekozen bericht gekoppeld ("+2 bronnen"). Herpublicaties van dezelfde bron tellen niet mee. Met
   **Vergelijk bronnen** zet het model op een rij: feiten die meerdere bronnen melden, extra details
   per bron, tegenstrijdigheden en beweringen van maar één bron.
   **Onderzoek achter het nieuws:** staat er een DOI in het artikel, dan zoekt de app de studie op
   (Europe PMC, anders Crossref) en noemt die in de samenvatting.
   **Fora:** bronnen met categorie "Forum" worden als discussie/ervaring getoond en zo aan het model
   gegeven, nooit als geverifieerde kennis. Elke bron heeft een categorie (Nieuws, Vakbron,
   Wetenschap, Techniek, Forum).
4. **Volledige tekst en afbeelding.** Voor elk gekozen artikel gebruikt de app de volledige tekst
   uit de feed (`content:encoded`) of leest de artikelpagina (hoofdtekst + `og:image`). Een
   kwaliteitscontrole wijst cookiemeldingen, inlogmuren en navigatie af. Lukt het niet, dan krijgt
   het artikel de status "alleen intro" en mag het model niets aanvullen. De volledige tekst wordt
   alleen tijdelijk gebruikt, niet opgeslagen.
5. **Vertalen.** Kop en intro worden offline vertaald met ML Kit naar de taal van de briefing. Het
   origineel blijft zichtbaar.
6. **Schrijven.** Het lokale model schrijft per artikel een **titel**, een **samenvatting** en
   **"wat betekent dit voor jou"**. Daarbij gebruikt het de instructies van het profiel en alleen de
   tekst uit de feed: het mag niets verzinnen. Zonder geladen model blijft het bij koppen en intro's.
7. **Opslaan en tonen.** De briefing verschijnt pas als hij helemaal klaar is. Hij gaat in de
   geschiedenis (met weekoverzicht) en naar de widget op het startscherm. Onder de briefing staat
   **Alle resultaten**: alle andere gevonden berichten (max. 60), zodat niets ongezien verdwijnt.

### 4.4 Webmodus (Brave Search)

Bij bronmodus `WEB` of `BOTH` zoekt de app met de **Brave Search API**. De gebruiker heeft daarvoor
een eigen sleutel, die versleuteld op de telefoon staat.

- Per onderwerp (maximaal 4) maakt de app een zoekopdracht van de trefwoorden, beperkt tot de
  gekozen sites: `(trefwoord1 OR trefwoord2 OR trefwoord3) (site:a.nl OR site:b.nl …)`.
- Er geldt een **maandlimiet van 1000 Brave-zoekopdrachten**. Die wordt in de app geteld en gedeeld
  met de Vraagbaak.

### 4.5 Op de achtergrond

Elk profiel met een schema krijgt een eigen WorkManager-taak. Die start **30 minuten vóór** de
gekozen tijd en draait alleen met internetverbinding. De taak:

- laadt het **laatst gebruikte model** in de achtergrond;
- maakt de complete briefing, met hoogstens **8 minuten** modeltijd;
- geeft dan een melding ("Je briefing staat klaar") en ververst de widget.

In **privacymodus** gebeurt er niets via internet.

### 4.6 Bekende beperkingen

- RSS levert vaak alleen een kop en een korte intro. De samenvatting is dus zo goed als die intro.
  De app haalt de volledige artikeltekst niet op.
- Google Nieuws-links zijn doorverwijzingen; het echte artikel opent pas na een klik.
- Selectie gebeurt op trefwoorden, niet op betekenis (geen embeddings).
- Kleine modellen (E2B) maken soms fouten in het Nederlands of Bulgaars.
- Ground News heeft geen open koppeling. De knop kopieert de kop en opent de Ground News-app, waar
  de gebruiker de kop in de zoekbalk plakt om de berichtgeving te vergelijken.

### 4.6b Extra's (november 2026)

- **Google Nieuws-links** worden omgezet naar de echte link van de uitgever (oude links: uit de
  base64-code; nieuwe: via dezelfde aanvraag die de Google Nieuws-website doet). Daardoor kan ook van
  deze berichten de volledige tekst gelezen worden.
- **Status per bron**: laatst opgehaald, aantal berichten, fout en laatst gelukt (Bronnen-scherm).
  Een feed telt pas als werkend als hij antwoordt én bruikbare berichten levert.
- **Nieuw wetenschappelijk onderzoek** per profiel (aan/uit): Europe PMC (inclusief PubMed), Engelse
  trefwoorden van de onderwerpen, laatste 30 dagen, als categorie "Wetenschap".
- **Bewaren in bibliotheek**: een artikel kan op verzoek (volledige tekst, anders de samenvatting)
  in de bibliotheek van de Vraagbaak worden gezet.
- **Vergelijk bronnen** geeft per bewering een status: ondersteund, deels ondersteund,
  tegenstrijdig, één bron, onvoldoende informatie, weerlegd.

### 4.7 Richtlijnen bewaken

Via Nieuws → menu → **Richtlijnen bewaken** volgt de app openbare pagina's (bijv. GGZ Standaarden,
Akwa GGZ, NISPA, Trimbos, Richtlijnendatabase). Dagelijks (WorkManager, alleen met internet, niet in
privacymodus) wordt de hoofdtekst gelezen, genormaliseerd (datums, tijden en korte menuregels
tellen niet mee), gehasht (SHA-256) en per alinea vergeleken met de vorige versie. Alleen echte
inhoudelijke wijzigingen geven een melding, met de toegevoegde en verwijderde alinea's. Een pagina
kan met één tik naar de bibliotheek van de Vraagbaak.

## 5. Informatievergaring buiten het nieuws

### 5.1 Vraagbaak (Werk)

Een vakvraag wordt beantwoord met **bronnen**, in deze volgorde:

1. **PubMed** via de NCBI E-utilities: `esearch` (gesorteerd op relevantie) en daarna `efetch`
   (abstracts). Het model zet de vraag eerst om in een Engelse PubMed-zoekopdracht.
   **Europe PMC** (REST-API, gratis) zoekt daarna met dezelfde termen; publicaties die al via PubMed
   gevonden zijn (zelfde PMID of titel) worden niet dubbel geteld. Open access wordt vermeld.
2. **Vertrouwde sites** via Brave, beperkt met `site:`. Standaard zijn dat: GGZ Standaarden,
   Akwa GGZ, Trimbos, NISPA, Farmacotherapeutisch Kompas, Richtlijnendatabase, NHG-Standaarden en
   Vilans. De gebruiker kan sites toevoegen of uitzetten.
3. **Eigen bibliotheek** (offline). Eigen PDF's, Word-bestanden, ODT, RTF, HTML, tekst en foto's
   worden in stukken (passages) geknipt en geanonimiseerd. Bij een vraag zoekt een TF-IDF-score de
   best passende passages.

Daarna gaat het zo verder:

- De gevonden stukken worden **genummerd** [1], [2], … aan het model gegeven.
- Het model antwoordt alleen op basis daarvan, met bronvermelding. Het noemt nooit doseringen en
  verwijst daarvoor naar het Farmacotherapeutisch Kompas.
- Het antwoord wordt met een lage temperatuur gegenereerd (0,3).
- In **privacymodus** wordt alleen de eigen bibliotheek gebruikt.

### 5.2 Vertalen van webpagina's

Deel je een link met de app, dan haalt Vertalen de pagina op en neemt alleen de hoofdtekst (zonder
menu's of reclame). Die tekst wordt vertaald, en kan worden samengevat of vereenvoudigd.

### 5.3 Webzoeken in de chat

Het zoeken kan uit, automatisch of altijd aan staan. Bij "automatisch" bepaalt een detector of een
vraag actuele informatie nodig heeft.

Er zijn drie zoekdiensten:

- **Gratis**: DuckDuckGo Instant Answer en Wikipedia. Encyclopedisch, niet voor actueel nieuws.
- **Brave Search**: eigen sleutel; telt mee voor de maandlimiet.
- **Serper** (Google-resultaten): eigen sleutel.

De resultaten worden als genummerde context aan het model gegeven, met de opdracht om de bronnen te
noemen. In privacymodus staat webzoeken altijd uit.

### 5.4 Gedeelde kennislaag en AI-zoekdiensten

- De Vraagbaak doorzoekt naast de bibliotheek ook de opgeslagen nieuwsbriefings (offline).
- Antwoorden wegen bewijs (richtlijn/review > studie > nieuws; forum is geen bewijs) en noemen bij
  studies de opzet en het aantal deelnemers als de bron dat vermeldt.
- **Zoekdiensten**: naast Gratis, Brave en Serper ook **Gemini + Google Search** (API-sleutel van
  Google AI Studio; Google Search via de API vraagt een betaalaccount, met een maandelijks gratis
  aantal) en **OpenAI webzoeken** (API-sleutel van platform.openai.com, betalen per gebruik; een
  ChatGPT-abonnement geeft geen API-toegang). De AI zoekt zelf en geeft een samenvatting plus de
  geciteerde pagina's als bronnen. Elke dienst heeft een eigen versleutelde sleutel en instelbaar model.

## 6. Waar een andere zoekmethode kan aansluiten

Voor een ontwikkelaar of een andere AI die betere methodes heeft:

| Haakje | Waar in de code | Wat je kunt leveren |
|---|---|---|
| **Nieuwe nieuwsbron** | `NewsSource(id, name, url)` in `data/briefing/BriefingModels.kt` | Elke RSS/Atom-URL. Met `{q}` erin wordt het een zoekfeed per trefwoord. |
| **Extra artikelen** | `BriefingGenerator.build(extraItems = …)` | Een lijst `NewsItem(title, link, sourceName, publishedMillis, description, imageUrl)` uit elke andere bron (API, scraper, Ground News-export…). De rest van de pijplijn (filter, selectie, vertaling, samenvatting) werkt dan vanzelf. |
| **Zoekdienst** | Interface `SearchProvider` in `data/WebSearch.kt` (bestaande: Free, Brave, Serper) | `search(query, apiKey, maxResults, country, language)` → bronnen met titel, URL en tekstfragment. |
| **Vertrouwde vaksites** | `TrustedSite(domain, name)` in `data/medical/MedicalModels.kt` | Domeinen voor de Vraagbaak. |
| **Betaalmuurlijst** | `Paywall.DOMAINS` in `data/briefing/Paywall.kt` | Domeinen die overgeslagen moeten worden. |

### Wensen en ideeën voor verbetering

- **Volledige artikeltekst ophalen** (bij gratis sites), zodat samenvattingen beter worden.
- **Selectie op betekenis** in plaats van op trefwoorden: lokale embeddings, of het model laten
  scoren op relevantie voor het profiel.
- **Eén verhaal, meerdere bronnen**: berichten over hetzelfde onderwerp groeperen en vergelijken,
  zoals Ground News dat doet.
- **Meer betrouwbare gratis Nederlandse en Bulgaarse vakbronnen** met eigen feed, zoals GGZ Nieuws,
  V&VN, Zorgvisie en BTA.

## 7. Privacy-overzicht

- Het model draait volledig offline. Alleen deze dingen gebruiken internet: nieuws ophalen, PubMed,
  Brave of Serper, model- en taalpakketdownloads, en (alleen als offline niet beschikbaar is) de
  online spraakherkenning van Google.
- **Privacymodus** zet al het internetgebruik voor inhoud uit.
- **App-vergrendeling** met vingerafdruk of pincode. De app vergrendelt opnieuw na meer dan 2
  minuten in de achtergrond.
- Rapportages, scans, gedicteerde tekst en de bibliotheek worden geanonimiseerd. Dat gebeurt eerst
  met vaste regels en daarna met een namenzoeker via het lokale model.
- API-sleutels staan versleuteld op de telefoon.
