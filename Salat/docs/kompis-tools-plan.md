# Kompis-verktøy (Rise først) – arbeidsplan

> Midlertidig fil. Slettes når alle oppgavene under er gjort.

## Mål

Kompis skal kunne bruke verktøy, ikke bare svare med tekst. De første verktøyene styrer Rise, slik at man kan si «sett en vekking klokka 06:30 på hverdager», og Kompis oppretter alarmen og bekrefter muntlig.

## Utgangspunkt i koden

Alt ligger i `shared/src/commonMain/kotlin/no/jodal/salat/apps/`.

- `kompis/ChatStream.kt`
  - `LlmRequest.body` sender bare ett verktøy, `say`, og tvinger det med `tool_choice`.
  - `parseChatChunk` slår sammen `arguments` fra alle `tool_calls`. Den skiller ikke mellom verktøy eller indekser.
- `kompis/Say.kt`: `PartialJson` og `SayStream` leser `say`-argumentene mens de strømmer, så teksten kan leses opp underveis.
- `kompis/OpenRouterPipeline.kt`: kjører én forespørsel per tur og mater `SayStream` (se linje ~71, ~259 og ~297).
- `kompis/LlmTest.kt` bruker også `SayStream` og bygger forespørsler.
- `rise/Model.kt`: `RiseAlarm`, `Repeat` (`Once`, `Weekly`, `MonthlyOnDay`, …), `RiseGroup`.
- `rise/RiseStore.kt`: `update { }` lagrer og planlegger på nytt via `NativeBridge`.
- `RiseStore` er et singleton hentet med `RiseStore.get(bridge)`.

## Designvalg

1. Verktøy implementerer et felles grensesnitt og ligger i et register. Nye verktøy (timer, kalender, …) er bare nye implementasjoner.
2. `say` blir et vanlig verktøy blant andre. Modellen må alltid velge et verktøy (`tool_choice: "required"`), og turen er ferdig når `say` kalles.
3. Samtalen er en løkke: forespørsel → verktøykall → utfør → legg resultatet i samtalen → ny forespørsel, til `say` kalles. Maks 4 runder, så en feil ikke kan gå i evig løkke.
4. Verktøyresultatet beskriver alltid hva som faktisk skjedde, slik at modellen ikke kan påstå noe annet i `say`.
5. Opprettelse og endring skjer umiddelbart. Sletting krever muntlig bekreftelse først (ligger i verktøybeskrivelsen og i prompten).

## Oppgaver

### 1. Verktøyregister
- [x] `kompis/tools/KompisTool.kt` med: `name`, `description`, `parameters` (JSON-skjema som `JsonObject`), `suspend fun execute(args: JsonObject): ToolResult` og et kort `promptHint`.
- [x] `ToolResult(content: String, isError: Boolean, card: ToolCard?)`, der `card` er det som vises i UI.
- [x] `KompisTools`-register: `val all: List<KompisTool>` og oppslag på navn.
- [x] `say` flyttes ut av `ChatStream.kt` og inn som et verktøy i registeret (samme skjema som i dag, men uten `execute`; det håndteres av løkken).

### 2. Forespørsel og parser
- [x] `LlmRequest.body` tar en liste verktøy og skriver dem ut. `tool_choice` blir `"required"`.
- [x] `messages` støtter nye meldingstyper: `assistant` med `tool_calls` og `tool` med `tool_call_id` og `content`. Utvid `Role`/meldingsmodellen i `kompis/Model.kt`, uten å ødelegge lagret historikk.
- [x] `ChatChunk` får `toolCalls: List<ToolCallDelta>` (index, id, name, argumentsDelta) i stedet for én sammenslått streng.
- [x] En `ToolCallAssembler` samler deltaene per indeks til ferdige kall (id, navn, hele argumentstrengen).
- [x] `SayStream` mates bare med argumentene til `say`-kallet.
- [x] Oppdater `LlmTest.kt` og andre kallere.

### 3. Verktøyløkke i pipelinen
- [x] `OpenRouterPipeline`: etter at strømmen er ferdig, sjekk kallene som er samlet.
  - `say`: som i dag (tale, historikk).
  - andre verktøy: kjør `execute`, legg til `assistant`-melding med `tool_calls` og én `tool`-melding per kall, og start en ny forespørsel.
- [x] Maks 4 runder. Hvis grensen nås, si en kort feilmelding på tale.
- [x] Ukjent verktøy eller ugyldige argumenter gir et `tool`-svar med feil, slik at modellen kan prøve igjen eller forklare.
- [x] Retry fra siste commit (midlertidige serverfeil før strømmen starter) skal fungere også for oppfølgingsforespørslene.
- [x] Metrikker per melding (tid til første token osv.) må fortsatt gi mening når en tur består av flere forespørsler. Bestem hva som summeres.

### 4. Rise-verktøy (`kompis/tools/RiseTools.kt`)
- [x] `rise_list`: grupper og alarmer med id, tittel, klokkeslett, gjentakelse, type og om den er på.
- [x] `rise_create`: `title`, `time` («HH:mm»), `kind` (`alarm` eller `notification`), `repeat` (`once` med `date`, eller `weekly` med `days`), `group` (valgfri), `snoozeMinutes` (valgfri), `earlyWarningMinutes` (valgfri).
- [x] `rise_update`: endre felt på en eksisterende alarm (på id).
- [x] `rise_set_enabled`: skru en alarm på eller av.
- [x] `rise_delete`: slett (på id). Beskrivelsen sier at brukeren må ha bekreftet først.
- [x] Validering: klokkeslett, ukedager 1–7, dato som ikke er i fortiden. «Én gang» uten dato velger neste forekomst av klokkeslettet.
- [x] Alle endringer går via `RiseStore.update`. Hvis `scheduleError` settes (for eksempel manglende tillatelse til alarmer), skal det komme med i verktøyresultatet.
- [x] Verktøyene får `RiseStore` og `NativeBridge` via konstruktør, slik at de kan testes.

### 5. Prompt
- [x] `VoicePrompt.kt`: legg til dagens dato, ukedag, klokkeslett og tidssone, slik at «i morgen» og «på mandag» kan tolkes.
- [x] Legg til korte regler for verktøybruk: bruk verktøyene i stedet for å love noe, bekreft sletting, avslutt alltid med `say` som oppsummerer hva som ble gjort.
- [x] Legg inn `promptHint` fra aktive verktøy.

### 6. UI
- [x] Vis et lite kort i samtalen for hver handling («⏰ Vekking 06:30 man–fre», «Slettet: Trening»).
- [x] Lagre verktøykort i historikken (`History.kt`), og vis dem i `ConversationScreen.kt`.
- [x] Mens et verktøy kjører, vis en diskret «jobber»-tilstand (mascot-reaksjon om det passer).
- [x] Vis verktøykallene i `MessageInfoSheet` (nyttig for feilsøking).

### 7. Tester
- [x] Parser og `ToolCallAssembler`: flere kall, delte argumenter, indekser som kommer i blandet rekkefølge.
- [x] Løkken: `say` direkte, ett verktøy og så `say`, ugyldig verktøy, maks-grense.
- [x] Rise-verktøyene mot en fake `NativeBridge`: opprett, oppdater, slett, ugyldige argumenter.
- [x] Eksisterende tester for `SayStream`/`PartialJson` skal fortsatt passere.

### 8. Prøving på enhet/simulator
- [ ] Bygg og kjør. Prøv: «sett en vekking klokka halv sju på hverdager», «hva har jeg av alarmer?», «skru av treningsalarmen», «slett den».
- [ ] Sjekk at alarmen vises i Rise og er planlagt i iOS.
- [ ] Sjekk at tale starter uten merkbar ekstra forsinkelse når ingen verktøy trengs.

### 9. Opprydding
- [ ] Oppdater `docs/rise.md` (kort avsnitt om at Kompis kan styre Rise) og eventuelt en kort `docs/kompis-tools.md`.
- [ ] **Slett denne filen** (`docs/kompis-tools-plan.md`).

## Åpne spørsmål

- Hvilke modeller klarer `tool_choice: "required"` og flere runder bra via OpenRouter? Tving ruting til leverandører som støtter verktøy, om nødvendig (`LlmRouting.kt`).
- Hvor mye ekstra forsinkelse gir en verktøyrunde før taleoppleseren starter? Kan vi la modellen si en kort setning først?
