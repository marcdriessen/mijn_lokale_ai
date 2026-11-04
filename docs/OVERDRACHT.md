# Overdracht voor een nieuwe sessie

**App:** "Local LLM/AI" (Android, Kotlin/Compose, LiteRT-LM, offline).
**Gebruiker:** ggz-verpleegkundige (verslavingszorg); spreekt Nederlands en Bulgaars; Pixel 8 Pro met 12 GB RAM.
**Werkwijze:**
- Antwoord in eenvoudig Nederlands.
- Werk op branch `claude/optimistic-ramanujan-11epu5`.
- De pull request naar main is marcdriessen/mijn_lokale_ai#5. De gebruiker merget zelf.
- CI publiceert `LokaleAI.apk` in de release `laatste-versie`.
- Uitleg over hoe de app werkt: `docs/HOE-DE-APP-WERKT.md`.

## Afspraken
- API-keys en tokens nooit in de chat; ze worden in de app ingevoerd en versleuteld bewaard.
- Geen APK's committen; applicationId niet wijzigen.
- Privacy:
  - cliëntgegevens worden geanonimiseerd vóór iets de telefoon verlaat;
  - de privacymodus blokkeert externe verzoeken;
  - nooit inloggen automatiseren (alleen de gebruiker logt zelf in, in de WebView).
- Geen betaalde API's. De abonnementen van de gebruiker (ChatGPT, Gemini, Claude) zijn niet bruikbaar als API.
- Geen GGUF/llama.cpp-motor.
- Plan: eerst deze app werkbaar afmaken. Daarna opsplitsen in losse apps (Nieuws, Werk, Talen), elk als nieuw project.
- Een VPS-brug is uitgesteld.

## Laatste stand (4 nov 2026)
- 17 modellen. Automatisch model per taak via `data/ModelRouting.kt` (Instellingen → Model).
- Alle nieuwsbronnen gecontroleerd op betaalmuren (`tools/bronnencheck.py`).
  - Dnevnik, Zorgvisie, ZorgWelzijn en Reuters staan uit (bronversie 7).
  - Gewone cookies tellen niet meer als login.

- Kennisbank GGZ & verslaving (offline, ±286.000 vraag/antwoord-paren uit UMCU-datasets) voor de Vraagbaak:
  `tools/maak_kennisbank.py`, workflow `kennisbank.yml` → release `kennisbank`, app-code `data/medical/KnowledgeBase.kt`.
  Zie `docs/DATASETS.md`.

## Open punten
- Testronde door de gebruiker. Checklist:
  - ontgrendelen;
  - rapportage (SOEP);
  - scannen (ook Cyrillisch);
  - vertalen en voorlezen;
  - taalcoach;
  - nieuws om 08:00;
  - vraagbaak.
- Kennisbank testen op de telefoon (downloaden, vragen stellen). Later eventueel: leerkaarten/quiz uit de kennisbank.
- Eigen scorelijsten (naast CIWA, COWS en AUDIT).
- Eventueel: "Dataset importeren" met kolomkeuze, voor de Vraagbaak (vraag/antwoord), de Taalcoach (NL/BG-zinsparen) en leerkaarten. Geschikte Hugging Face-sets staan in `docs/DATASETS.md`.
