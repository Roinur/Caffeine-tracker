# Originalets temaväljare, 0.2.2

Användarens uttryckliga krav 2026-10-21: använd den riktiga komponenten, inte en egen replika.

## Källkod

DashboardThemeControl.kt innehåller nu det oförändrade blocket från Sauce Tracker Rewrite / DashboardControls.kt: ThemeToggleWithAccentPicker, themeModeSymbol, AccentPickerOption och ACCENT_PICKER_OPTIONS. Blocket är kopierat direkt från den lokala originalfilen. Den tidigare omskrivningen i Caffeine är borttagen.

Ingen ändring av komponentens funktionskropp, signatur, geometri, färger, gester eller ritning har gjorts. Det innebär originalets 40 dp-kontroll, 8 dp-avstånd till färglisten, 34 dp-färglist, ringar och Canvas-symbol för automatiskt färgval. Originalets beteende har behållits även där den tidigare omskrivningen försökte lägga till eget beteende.

DashboardViewModel.cycleThemeMode i Sauce använder System → Mörkt → Ljust → System. Den tidigare Caffeine-versionen använde enumordningen och fick en annan följd. Originalets when-uttryck är nu kopierat till ThemeModeCycle.kt och används av Caffeines lagringsanrop.

## Endast integration utanför originalblocket

- Paket/importer pekar på Caffeines typer med samma enumvärden.
- SauceThemeText adapterar originalets Text-API till Material3 Text. Sauces två privata lägesflaggor är uttryckligen false i anropet; de motsvarande lägena finns inte i Caffeine. Adaptern ändrar inte textens stil, vikt eller innehåll i det vanliga läget.
- Originalets stödikon ic_incognito_24.xml har kopierats byte för byte så att samma källkod kan kompileras, även om den grenen inte aktiveras i Caffeine.
- Sparning av tema/accent sköts av Caffeines befintliga ViewModel utanför komponenten. Val av redan aktiv accent är en no-op, som i originalets chooseAccentMode.

## Bevis och kontroll

`python tools/verify-theme-source.py <path-to-Sauce-Tracker-Rewrite>` läser den riktiga Sauce-filen och jämför hela komponent-/hjälparblocket utan omformatering eller whitespace-normalisering (förutom plattformens radslut). Den kontrollerar också det ursprungliga temaväxlingsuttrycket och stödikonens bytes. Originalfilen ändras inte.

Absoluta källsökvägar, radnummer och SHA-256 är låsta i theme-source-provenance.json. Blockets SHA-256: dd10c901fa1a15d465a790ad2483b87933f6e15da7e9b3d8b4e619ade0129369.

Tidigare påståenden i UI_POLISH_2026-10-21.md om att 0.2.1 redan använde originalkontrollen var fel: den versionen var en omskrivning. Den verifierade originalkoden införs i 0.2.2.

## Temabyte utan layoutförskjutning

Tema-, accent- och Extra mörkt-val i både toppfältet och Inställningar använder nu ett separat tyst sparanrop i Caffeines ViewModel. Databassparning, backup och spärr mot samtidiga ändringar behålls, men den globala förloppsindikatorn aktiveras inte och ingen kvittens-snackbar visas för dessa val. Därför ändras inte toppfältets höjd när tema eller färg sparas. Övriga operationer behåller sin laddningsindikator. Originalkomponenten är fortsatt oförändrad.

## Slutkontroll på Nothing Phone (3)

0.2.2-debug byggd med build.ps1 -Checks och installerad med adb install -r. 54 JVM-tester passerade; lint har 0 fel och 2 befintliga varningar. Installerad APK har samma SHA-256 som outputs/Caffeine-Tracker-0.2.2-debug.apk.

I separat GitHub-databas verifierades originalets System/Mörkt/Ljust-cykel och långtryck/drag/släpp för Blå, Auto och Bärnsten. Efter den tysta sparintegrationen verifierades alla tre temalägen på hemsidan samt tema, accent och Extra mörkt i Inställningar. UI-trädens innehållspositioner är oförändrade före/efter byten, och ingen spar-snackbar visas. Den första omkörningen av dragtestet misslyckades medan en installation kördes parallellt; den tidigare fullständiga originalgestkontrollen passerade, och originalblocket är oförändrat.

Telefonen är åter i normalt läge. Produktionsdatabasen, den rullande JSON-backupen och backupmappens inställningar är byte-identiska med kontrollkopiorna från före installation. Kontrollrapporter ligger lokalt i qa/device/original-theme-install-check.json och qa/device/theme-quiet-layout-check.json; privat data inkluderas inte i git.
