# Design och faktisk återanvändning

Caffeine Tracker ska kännas som samma familj som Sauce och Book, med egen koffeinidentitet: bärnstensaccent, kaffeglyf, beräknad nivå och koffeinkurva.

Den första iterationen återanvände byggmiljö, tema och backuphjälpare men ritade nya skärmlayouter. Det var för lite synlig återanvändning för användarens instruktion. Den korrigerade versionen nedan återanvänder även riktiga produktkomponenter och sidstruktur.

| Del | Ursprung och ändring |
| --- | --- |
| Startsideskort | Sauce ModernMetricTile: oförändrad funktionskropp, samma pressdjup, färganimering, rundning, kantlinje, typografi och marginaler. |
| Ikoner i korten | Sauce DashboardMetricGlyphIcon: originalets Canvas-stil; två tillagda domänglyfer för kaffe och koffein. |
| Sidhuvud | Sauce DashboardHost: CenterAlignedTopAppBar-layout anpassad till Caffeine-rutter och inställningsknapp. |
| Temakontroll | Sauce ThemeToggleWithAccentPicker och accentval. Privata innehållslägen från Sauce följer inte med. |
| Startsideslayout | Sauce HomeDashboardScreen: tre metrikkort och 2:1-panelrad, 184 dp. Sökrutan ersatt med dryck/portion, innehållspanelerna med koffeinkurva och nivå. |
| Inställningar | Sauce SettingsSearchBar, SettingsCategoryRow, SettingsOptionSection och SettingsActionButton. Ändrade kategorier och svenska texter. |
| Statistikceller | Book BookStatCell och BookVerticalDivider: oförändrade funktionskroppar under Caffeine-namn. |
| Statistikdiagram | Book BookLineChart: kopierad linje/yta, rutnät, pekval och markering. Anpassad till koffein/antal, svenska datum och förberedda dag-/månadspunkter. |
| Statistiknavigering | Book BookStatsScreen: föregående/nästa period, fyra periodknappar, total/snitt/högsta dag med avdelare. Kalenderperioder följer lokal tid; dagens period slutar idag. |
| Tema/formulär | Sauce-paletter, accent/extra mörkt, palettanimering, PaletteOutlinedTextField och valkomponenter. |
| Backup | Sauce DocumentTreeStorage, BackupJsonWriter, RollingBackupStore, LocalBackupRetention och rotationstester. Koffeinformat och kontrolläsning är anpassade. |
| GitHub-läge | Sauce-start/capture-skript och explicit sessionskonfiguration anpassade. Caffeine använder bara syntetiska fixturedata i separat DB. |
| Ny domänkod | Koffeinmodell, intag/drycker, databasens koffeinschema, importerare, editorer, historikfiltrering och koffeinberäkning. |

BASE_PROVENANCE.txt innehåller originalfiler och originalhashar. component-reuse-check.txt visar fyra jämförelser av normaliserade funktionskroppar. Hela Sauce- eller Book-backenden följer inte med: de domänoberoende delarna återanvänds och koffeinets lagring och validering är nya.

## UX-förenkling 0.2.0 (2026-10-21)

Användarens godkända förenkling ersätter de stora antalspanelerna och 2:1-panelraden med en kompakt lägesrad, favoriter och ett gemensamt intagsformulär. De historiska kopieringsuppgifterna ovan beskriver basen; ModernMetricTile och den gamla temagestkontrollen visas inte längre på startsidan. Familjens centrerade sidhuvud, paletter, kort och palettfält består. Sidhuvudet har nu en synlig tillbaka-pil på undersidor. Books statistikceller, periodnavigation och diagram består, med detaljavsnitt och pekhantering som låter vertikal scroll vinna.

Rättelse 0.2.2: temaväljaren i 0.2.1 var omskriven. Den har nu ersatts av oförändrad originalkod med verifierad källkodslikhet. Se ORIGINAL_THEME_SELECTOR.md och theme-source-provenance.json.
