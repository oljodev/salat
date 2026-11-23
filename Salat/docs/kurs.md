# 🧭 Kurs – plan

Kurs er mini-appen for daglig logging av vaner: søvn, skjermstopp, analog kveld, kvalitetsskjermtid,
trening/pauser og «Ship & Earn». Bygget etter spesifikasjonen «Personlig Telemetri- & Habit-modul v1.0».

## Valg som spesifikasjonen ikke avgjorde

- **Garmin Sleep Score i loggen for dag D er natten etter D** (den man våkner fra morgenen D+1). Det passer med
  morgenloggingen for gårsdagen, og gjør at «skjerm av før 22:00» kan sammenlignes med søvnen samme natt.
  Grafen viser derfor søvnen etter dagen, ikke søvnen før.
- **Poeng, godkjente dager, rekker, freezes og achievements regnes ut fra loggene hver gang.** De lagres ikke, så
  alt blir riktig når en logg redigeres eller Sleep Score fylles inn morgenen etter.
- **Freezes brukes automatisk** på en uke som endte på 4/7, så også de kan regnes ut.
- Maks er 220 poeng om dagen. Godkjent dag: logget og minst 110 poeng.
- Perfect Week-bonusen (+150) teller i totalpoengene, og dermed også mot nye freezes.

## Fase 1: Grunnmur og check-in

- `KursLog`, `KursData` og `KursStore` med lagring gjennom `NativeBridge.saveText`.
- Smart dato: før 14:00 og gårsdagen ikke logget → i går, ellers i dag. Bytte mellom i går og i dag.
- Check-in på én skjerm som kan blas: søvn og kveld, skjermtid, trening/pauser, Ship & Earn, følelse og dagens seier.
- Poengberegning med tester.
- JSON-eksport og -import gjennom utklippstavlen.
- Mini-appen på forsiden.

## Fase 2: Ukesystem og dashboard

- Godkjente dager, ukesmål 5/7, ukesrekke, kjernerekke for 22:00, freezes (1 per 1 000 poeng, maks 2 på lager,
  brukes automatisk på en uke med 4/7), Perfect Week.
- Dashboard: toppkort med ukesrekke, fremdrift for uka, freezes og totalpoeng, og knappen «Logg dag».
- Graf med poeng, 7-dagers snitt og Sleep Score, 7D/30D/90D, trykk på et punkt for detaljer.
- Heatmap for de siste 12 ukene og 4 KPI-kort.

## Fase 3: Innsikt, ukesrapport og achievements

- Regelbasert innsiktsmotor som gir 2–4 setninger etter hver check-in, med minimum antall dager før korrelasjoner
  brukes.
- Ukentlig «Forskningsrapport»: poeng og endring, snitt søvn og følelse, skjermstopp X/7, ukas seire.
- Achievements: Analog Arkitekt, 22:00-Disiplin, Søvnforskeren, Shipper, Jernuke. Egen visning.

## Fase 4: Varsler og Brems

- Lokale varsler: nedtelling 21:45, morgenpåminnelse (08:15 hverdager, 09:30 helg) bare hvis gårsdagen ikke er
  logget, valgfri ettermiddagspause 16:30 på treningsfrie dager. Kan skrus av og på.
- «Holdt app-sperrer» fylles ut fra Brems: `Nei` hvis et Brems-mål ble brutt den dagen. Kan overstyres.
