# Portioner och Caffeine Clock-standard

Användaren bad om samma standard som Caffeine Clock. Kontrollerat 2026-10-20 mot den installerade appen com.AWSoft.CaffeineClock, version 2.9.3, assets/consumable_items.json. Endast nedanstående numeriska enhetsfakta används; appens databas, beskrivningar och bilder har inte kopierats till projektets tillgångar.

| Dryck | Sparad enhet | Separat ml-standard |
| --- | --- | --- |
| Drip coffee | 1 cup: 240 ml, 95 mg | 0,4 mg/ml |
| Instant Coffee | 1 teaspoon: 57 mg | 0,28 mg/ml |

Caffeine Clocks avrundade enhetsvärden är separata. Vi ersätter inte 95 mg/cup med 96 mg bara för att 240 × 0,4 blir 96. Val av cup sparar 95 mg; volymförslagen 200/250/300 ml ger 80/100/120 mg. För snabbkaffe ger 200/250 ml 56/70 mg, medan en tesked fortfarande ger 57 mg.

Utvecklaren bekräftar också cirka 240 ml/cup här: https://www.reddit.com/r/Android/comments/1nyzl3a/i_built_caffeine_clock_the_caffeine_tracker/ . Den installerade appens enhetsdata är huvudunderlaget.

Samma standard används för de oförändrade startrecepten Filterkaffe/95 mg/1 kopp och Snabbkaffe/57 mg/1 tesked. När en dryck uttryckligen har en volym i sitt sparade recept, exempelvis 200 ml/100 mg, har den volymen och koffeinmängden företräde. Ändrade cup-/teaspoon-doser får inte standardomvandlingen automatiskt. Okända cup/can-recept behåller sin enhet och kan sökas som multiplar eller anges manuellt; vi hittar inte på en volym för en okänd burk.

Historiska intag, mängder, portionsnamn, tider och importerade ID:n ändras inte av sökning eller portionsförslag. Nya intag sparar det valda receptet/den beräknade dosen, och egen koffeinmängd är ett uttryckligt användarval.
