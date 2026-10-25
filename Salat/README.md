# Salat

En «alt mulig»-app: når du åpner den, ser du alle mini-appene inni den og velger hvilken du vil inn på.

Bygget med Kotlin Multiplatform og Compose Multiplatform. Ting som bare kan gjøres i Swift, som varsler og signeringsinfo, ligger i Swift-delen.

## Mini-apper

Forsiden har to deler: **Appene dine** og **Verktøy**.

| Mini-app | Del | Hva den gjør |
| --- | --- | --- |
| Rise | Appene dine | Alarmer og varsler i grupper, med gjentakelser og slumring. Se [planen](docs/rise.md). |
| Kompis | Appene dine | Snakk med Gemini Live med stemmen, også med skjermen av. Samtalene lagres og kan fortsettes. Bruker `gemini-3.8-live`, og husker samtalen som tekst for å holde prisen nede. Har også tester av tale til tekst, språkmodeller og tekst til tale fra OpenRouter, som første steg mot å bytte ut Gemini Live. |
| Ekko | Appene dine | Lyder og sitater fra YouTube Shorts, Snapchat Spotlight og videoer på telefonen. Klipp ut biten du vil ha, gi den navn og lag spillelister. Spiller med skjermen av, og kan styres fra låseskjermen. |
| Brems | Appene dine | Mindre scrolling. En kort pause hver gang du åpner en app du vil bruke mindre, varsel etter 5 minutter, og en pause på 60 sekunder etter 10 minutter. Mål per app, f.eks. under 15 minutter Snapchat om dagen, med rekker, og statistikk for uke og måned. Alt kan stilles inn. Krever to automasjoner i Snarveier, se oppsettet i Brems. |
| Fornyelse | Verktøy | Viser hvor lenge det er til appen må fornyes i SideStore. Salat varsler også dagen før. |
| Varseltest | Verktøy | Sender testvarsler og testalarmer, så du kan sjekke at varsler, alarmer og lyd virker. |

## Widgets

Widgetene ligger i én widget-utvidelse (`iosApp/SalatWidgets`), så de bare bruker én App-ID i SideStore. Hver widget har samme stil som mini-appen den hører til.

| Widget | Hvor | Hva den gjør |
| --- | --- | --- |
| Ekko: Lydbrett | Hjemskjermen | 4, 8 eller 12 lyder. Trykk på en lyd for å spille den uten å åpne appen. Velg lyder i Ekko med … og «Legg på lydbrettet». Er lydbrettet tomt, vises de nyeste lydene. |
| Ekko: Favorittlyd | Låseskjermen | Spiller én lyd. Hold inne widgeten for å velge hvilken. |
| Rise: Neste alarm | Hjemskjermen og låseskjermen | Når neste alarm ringer, og nedtelling til den. |
| Kompis: Snakk nå | Hjemskjermen, låseskjermen og kontrollsenteret | Åpner Kompis og starter en samtale med en gang. |

Appen og widgetene deler data gjennom en app-gruppe. SideStore gir app-gruppen et nytt navn når den signerer appen, så Salat leser det riktige navnet fra signeringsfilen ([SharedFiles.swift](iosApp/Common/SharedFiles.swift)). Velg å beholde utvidelsene hvis SideStore spør når du installerer.

## Installere

Legg til SideStore-kilden og installer Salat derfra, som beskrevet i [README-en i roten](../README.md#installing-an-app-with-sidestore). Da kommer nye versjoner som oppdateringer i SideStore.

Påminnelsen om fornyelse flyttes hver gang du åpner Salat. Har du ikke åpnet Salat siden SideStore fornyet den, kan du derfor få påminnelsen selv om appen allerede er fornyet.

## Oppsett

```
Salat/
├── shared/                  Kotlin: all UI og logikk
│   └── src/
│       ├── commonMain/      Felles kode: forsiden (ui/), mini-appene (apps/) og logoer (composeResources/)
│       ├── iosMain/         Kobler Compose-UI-et til iOS
│       └── desktopMain/     Forhåndsvisning på Linux
└── iosApp/
    ├── Salat/               Swift: oppstart, varsler, alarmer, lyd og signeringsinfo
    ├── SalatWidgets/        Widgetene, i SwiftUI
    └── Common/              Swift-kode som både appen og widgetene bruker
    └── project.yml          XcodeGen-oppsett for Xcode-prosjektet
```

- **Kotlin snakker med Swift** gjennom grensesnittet `NativeBridge` ([NativeBridge.kt](shared/src/commonMain/kotlin/no/jodal/salat/NativeBridge.kt)). Swift implementerer det i [IosNativeBridge.swift](iosApp/Salat/IosNativeBridge.swift).
- **Ekko henter lyd fra YouTube med [YouTubeKit](https://github.com/alexeichhorn/YouTubeKit)**, som legges til som Swift-pakke i [project.yml](iosApp/project.yml). Slutter YouTube-henting å virke, er det ofte nok å bygge på nytt, så den nyeste versjonen av YouTubeKit blir med.
- **Xcode-prosjektet ligger ikke i repoet.** Det genereres fra [project.yml](iosApp/project.yml) med XcodeGen når appen bygges.
- **Testene** kjøres med `./gradlew :shared:desktopTest`.
- **Appen bygges i GitHub Actions** ([salat.yml](../.github/workflows/salat.yml)) ved hver push til `main` som endrer noe i `Salat/`.

## Legge til en mini-app

1. Lag en ny fil i `shared/src/commonMain/kotlin/no/jodal/salat/apps/`. Bruk [Varseltest.kt](shared/src/commonMain/kotlin/no/jodal/salat/apps/Varseltest.kt) som mal.
   Gi den en farge fra `SalatColors` i [Theme.kt](shared/src/commonMain/kotlin/no/jodal/salat/ui/Theme.kt), og velg om den hører til under «Appene dine» eller «Verktøy».
2. Legg den til i listen `miniApps` i [MiniApp.kt](shared/src/commonMain/kotlin/no/jodal/salat/MiniApp.kt).
3. Trenger den noe fra iOS som Kotlin ikke kan gjøre, legg til en funksjon i `NativeBridge` og implementer den i Swift.

## Forhåndsvise på Linux

Du trenger JDK 21. Da kan du åpne UI-et i et vindu:

```sh
./gradlew :shared:run
```

Forhåndsvisningen later som appen går ut om 6 dager, og varsler skrives bare ut i terminalen.
