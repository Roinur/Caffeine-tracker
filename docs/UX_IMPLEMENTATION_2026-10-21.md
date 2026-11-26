# UX-förenkling 0.2.0

Genomförd 2026-10-21 efter användarens godkännande av UX_REVIEW_2026-10-21.md. Paketet är fortfarande com.roinur.caffeinetracker, versionCode 2, utvecklingsversion 0.2.0-debug. Ingen ny appidentitet eller databas skapas för riktiga användardata.

## Genomfört

- Startsida med kompakt nivå nu/vid läggdags/dagens registrerade koffein, favoriter och sökning. Förvald portionsruta och sparknapp visas först efter ett val. Kurvan öppnas vid behov. Senaste intag visar bara dagens poster.
- Ett gemensamt IntakeComposer-formulär för nya och befintliga intag. Val i biblioteket tar med exakt recept och bild till hemmet. Den stora registreringsdialogen är borttagen; redigering är en vanlig sida.
- Tid Nu kan ändras direkt. Datum, dricktid och anteckning finns under Fler detaljer. Upptagstid, kategori och mindre vanliga portionsantal finns under Avancerad beräkning. Befintliga intag med flera portioner visar faktisk mängd/enhet i grundvyn.
- Sparning avslutas efter databasbekräftelse. Tydliga kvitton med Ångra för tillägg, ändring och borttagning. Ångra kontrollerar att posten inte hunnit ändras igen och återställer exakt tidigare värde. Misslyckad sparning behåller formuläret och släpper spärren mot dubbeltryck.
- Frivilligt Spara som favorit efter egen registrering. Kompakt bibliotek med hjärta, alternativmeny, Skapa annan storlek och sparbar favoritordning. Befintliga favoriter ändras inte vid uppdatering. Helt nya installationer börjar med två favoriter.
- Dryckesredigering prioriterar namn, portion och total koffeinmängd. Volym kan lagras separat i ml. Bildvalet ligger i en separat väljare; samma bild återanvänds mellan storlekar. Kategori är infälld.
- Synlig tillbaka-pil på undersidor; tema och färg i Inställningar. Stora antalspaneler har ersatts av kompakta ingångar till Historik, Drycker och Statistik.
- Statistik börjar med vald period. Livstidsräknare visas under Allt; kalender och vanligaste drycker öppnas vid behov. Vertikal scroll kan börja på diagrammet; tryck/horizontaldrag väljer diagramvärde.
- Läggdags har tidsväljare. Modellparametrar ligger under Avancerad beräkning. Den verkningslösa dagsmål-kontrollen är dold och dess gamla värde bevaras. Backupytan visar status och tydliga huvudåtgärder; versioner/mapphantering ligger under Hantera backup.
- Kort visar kort portionsetikett, exempelvis 250 ml, medan originalbeskrivningen behålls i data. Favoritkortens bredd anpassas till textstorleken.
- GitHub-lägets databas raderas inte vid konfigurationsändring när samma ViewModel lever vidare.

## Datakompatibilitet

Databasschemat är fortsatt version 1: tabellernas JSON-payload tillåter valfria nya dryckesfält favoriteOrder och volumeMl. Äldre säkerhetskopior får standardvärden och går att läsa. Ordning och volym följer med nya säkerhetskopior. En opåverkad gammal portionsbeskrivning skrivs inte om bara för att namn eller bild ändras.

Intagsredigering kopierar den gamla posten och bevarar ID, importidentitet, ursprunglig tid, portionsantal, koffein per portion, upptagstid och andra dolda värden när de inte ändras. Ändrad totaldos och portionsantal stäms av med koffein per portion. Historiska intag skrivs inte om när ett recept ändras eller tas bort.

## Verifiering

- AssembleDebug, 54 JVM-tester och lint har gått igenom. Lint har två tidigare varningar i den gamla metrikkortskomponenten och den applikationsbaserade ContextWrappern; inga fel.
- Sex isolerade lagringsprov har gått igenom på Nothing Phone (3), inklusive återöppning, oförändrade historiska doser, backuprotation, återställning, bildnycklar, exakt ångring samt volym/favoritordning. Rapport: qa/device/ux-storage-tests.txt.
- På telefonen i separat GitHub-läge: favorit + Lägg till, dubbelt snabbt tryck, Ångra tillägg, egen dryck och frivillig favorit, redigering av anteckning utan ändring av övriga värden, Ångra ändring, borttagning och Ångra borttagning. Repeterbart huvudflöde: tools/ux-qa.py; tools/composer-qa.py pekar på detta flöde.
- Bibliotekets val använder inlineformuläret; portionssökning hittar 200/250 ml. En 600 ml/192 mg-variant skapades med Red Bull-bild, dess favoritordning flyttades och dess nya intag behöll samma volym/dos/bild. Intaget ångrades.
- Verifierat att modellparametrar är infällda, att inställningarnas tillbaka-pil går via kategorilistan och att vertikal scroll från diagrammet fungerar när kalendern är utfälld. En tidigare kontroll med helt infälld sida kunde inte bedöma scroll eftersom innehållet redan fick plats på skärmen; kontrollen gjordes om med utfällt innehåll.
- 130 procent systemtext provades på hemmet och registreringen; Lägg till förblev åtkomlig. En syntetisk extra registrering fanns kvar efter Activity-återskapande. Ursprunglig font_scale återställdes till 1.0.
- Skärmbilder tas enbart via capture-github-media.ps1 i GitHub-läge. Originaldatabasen används inte av UI-proven. Privata granskningsbilder finns i build/github-media-review/ux-*.png.

Ingen ny mätning av bildrutetider har gjorts. Detta är funktions-/layoutkontroll, inte en användarstudie eller ett fullständigt TalkBack-test. Faktisk import från filväljare och användarens externa backupmapp kördes inte på nytt under UI-granskningen; kompatibilitet och återställning omfattas av kod-/lagringsproven.

Ytterligare slutkontroller: ett syntetiskt intag med två portioner visades som mängd/enhet direkt i historikeditorn. Ändring 2 → 3 gav 190 → 285 mg med 95 mg per portion; Ångra återställde hela originalposten. Kortbredden provades på nytt vid 130 procent efter anpassningen, så att Energidryck visas sammanhängande. Den egna portionsbeskrivningens tangentbord använder Nästa för att komma till total koffeinmängd.

Slutleverans: installerad APK matchar outputs/Caffeine-Tracker-0.2.0-debug.apk byte för byte (SHA-256). VersionCode 2/0.2.0-debug bekräftades via pakethanteraren. Vanliga MainActivity öppnades och Favoriter verifierades utan skärmbild av användardata. Produktionsdatabasen, backup-JSON och backupmappens preferens var byte-identiska med kopiorna före installationen. Systemets font_scale är återställd till 1.0. Den sista APK:ns Nästa-tangent provades: fokus gick till koffeinmängden; registrering och Ångra fungerade. Rapport: qa/device/ux-final-install-check.json.
