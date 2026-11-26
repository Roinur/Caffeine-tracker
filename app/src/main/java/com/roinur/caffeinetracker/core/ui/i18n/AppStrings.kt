package com.roinur.caffeinetracker.core.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.roinur.caffeinetracker.core.model.AppLanguage

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.SV }
val LocalStrings = staticCompositionLocalOf<AppStrings> { AppStrings.SV }

interface AppStrings {
    val language: AppLanguage
    // Navigation & top bar
    val appName: String
    val back: String
    val settings: String
    val history: String
    val drinks: String
    val statistics: String
    val newDrink: String
    val editDrink: String
    val editIntake: String
    val close: String
    val cancel: String
    val clear: String
    val delete: String
    val edit: String
    val undo: String
    val saving: String
    val openState: String
    val closedState: String
    val dbLoadError: String
    // Snackbar / ViewModel messages
    val intakeAdded: String
    val intakeUpdated: String
    val intakeDeleted: String
    val changeUndone: String
    val drinkSaved: String
    val drinkDeleted: String
    val settingsSaved: String
    val backupExported: String
    val backupRestored: String
    val externalFolderEnabled: String
    val actionFailed: String
    val intakeMissing: String
    val backupFailedPrefix: String
    fun importedSummary(added: Int, duplicates: Int): String
    val githubModeReadOnly: String
    val fileOpenFailed: String
    val fileTooLarge: String
    val unknownBackupFile: String
    val exportWriteFailed: String
    val exportReadBackFailed: String
    val exportVerifyFailed: String
    // Home screen & chart
    val remainingNow: String
    val atTimePrefix: String
    val intakeToday: String
    val noFavoritesHint: String
    val caffeineCurve: String
    val returnToNow: String
    fun chartSemantic(nowMg: String, bedtimeMg: String): String
    fun chartLegend(thresholdMg: String, bedtime: String, halfLife: String): String
    fun addDrinkAction(name: String): String
    val editFavoriteAction: String
    // Intake composer
    val saveAsFavorite: String
    val drinkField: String
    val drinkNameField: String
    val searchDrinkPlaceholder: String
    val clearDrinkSearch: String
    val showMoreDrinks: String
    fun createDrinkPrompt(name: String): String
    val amountField: String
    val unitField: String
    val portionField: String
    val portionPlaceholder: String
    val estimatedAmount: String
    val savedPortion: String
    fun usePortion(label: String): String
    val calculatedFromRecipe: String
    val noMatchingPortion: String
    val enterCaffeineManually: String
    val totalCaffeineField: String
    val enterAmount: String
    val nowLabel: String
    val moreDetails: String
    val savedTimingPreserved: String
    val useCurrentTime: String
    val showPortionSuggestions: String
    val enterCustomCaffeine: String
    val drinkingDurationField: String
    val durationZeroHint: String
    val noteField: String
    val advancedCalculation: String
    val servingsCountField: String
    val absorptionTimeField: String
    val categoryField: String
    val appliesOnlyToIntake: String
    val aboutPortionSuggestions: String
    val portionSuggestionsHelp: String
    fun standardConcentration(mgPerMl: String): String
    val futureIntakeError: String
    val durationWholeMinutesError: String
    val absorptionMinutesError: String
    val invalidServingsError: String
    val checkAmountError: String
    val addButton: String
    val saveChangesButton: String
    // Intake & Drink editor
    val deleteIntake: String
    val deleteIntakeTitle: String
    val nameField: String
    val volumeInMl: String
    val otherPortion: String
    val volumeMlField: String
    val portionExampleField: String
    val caffeineInPortionField: String
    val showInFavorites: String
    val moreSettings: String
    val categoryLabel: String
    val drinkEditFutureHint: String
    val saveDrinkButton: String
    val deleteDrink: String
    val deleteDrinkTitle: String
    val previousIntakesKept: String
    val defaultPortion: String
    val newSizeSuffix: String
    // Drink image picker
    val imageIconLabel: String
    val imageAutoSuffix: String
    val imageCustomSuffix: String
    val chooseImage: String
    val imageReuseHint: String
    val autoFromName: String
    val imageDescPrefix: String
    // Drinks screen
    val favoritesOnly: String
    val customOnly: String
    val customBadge: String
    val searchDrink: String
    val clearSearch: String
    val noDrinkFoundTitle: String
    val noDrinkFoundDetail: String
    val favoritesHeader: String
    val dragFavoritesHint: String
    val otherDrinksHeader: String
    val moveUp: String
    val moveDown: String
    val selectDrink: String
    val removeFavoritePrefix: String
    val favoritePrefix: String
    val optionsPrefix: String
    val createOtherSize: String
    // History screen
    val yourHistory: String
    val matchingIntakesSuffix: String
    val savedIntakesSuffix: String
    val searchDrinkOrNote: String
    val dateChip: String
    val fromDate: String
    val toDate: String
    val clearDateFilter: String
    val historyStartsHere: String
    val noMatches: String
    val historyEmptyDetail: String
    val historyNoMatchesDetail: String
    val importHistoryButton: String
    val todayLabel: String
    val yesterdayLabel: String
    // Statistics screen
    val statsIntakes: String
    val statsLoggedDays: String
    val statsDrinks: String
    val statsWeek: String
    val statsMonth: String
    val statsYear: String
    val statsAll: String
    val statsCaffeine: String
    val statsIntakesMetric: String
    val statsIntakesUnit: String
    val statsThisWeek: String
    val statsThisMonth: String
    val statsThisYear: String
    val statsAllTime: String
    val previousPeriod: String
    val nextPeriod: String
    val perMonth: String
    val perDay: String
    val showMonthIntakes: String
    val showIntakes: String
    val tapChartMonth: String
    val tapChartDay: String
    val statsTotal: String
    val statsDailyAverage: String
    val statsAllDays: String
    val statsHighestDay: String
    val intakeCalendar: String
    val last13Weeks: String
    val topDrinks: String
    val inSelectedPeriod: String
    val noIntakesInPeriod: String
    val totalSuffix: String
    val countSuffix: String
    fun calendarSemantic(date: String, amount: String): String
    // Import / restore dialog
    val restoreBackupTitle: String
    val importHistoryTitle: String
    val restoreReplacesHint: String
    fun restoreDrinksCount(count: Int): String
    fun importNewAndDuplicate(newCount: Int, dupCount: Int): String
    val importPreservesHint: String
    fun importDrinksCount(count: Int): String
    val restoreButton: String
    val importButton: String
    // Settings
    val searchSettings: String
    val catModelTitle: String
    val catModelSummary: String
    val catAppearanceTitle: String
    val catAppearanceSummary: String
    val catBackupTitle: String
    val catBackupSummary: String
    val catAboutTitle: String
    val catAboutSummary: String
    val sectionCalculation: String
    val calculationSearchTerms: String
    val bedtimeLabel: String
    val halfLifeField: String
    val eveningThresholdField: String
    val calculationDisclaimer: String
    val checkDetailsError: String
    val saveCalculation: String
    val sectionThemeAndColor: String
    val themeSearchTerms: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val accentColorLabel: String
    val accentAuto: String
    val accentBlue: String
    val accentAmber: String
    val accentTeal: String
    val accentGreen: String
    val accentPink: String
    val accentRed: String
    val accentOrange: String
    val accentIndigo: String
    val extraDarkTitle: String
    val extraDarkSummary: String
    val sectionLanguage: String
    val languageSearchTerms: String
    val languageHint: String
    val sectionGoogleDrive: String
    val driveSearchTerms: String
    val sectionImportExport: String
    val importExportSearchTerms: String
    val importHistoryJsonCsv: String
    val exportBackup: String
    val restoreBackup: String
    val sectionAutoBackup: String
    val autoBackupSearchTerms: String
    val noLocalBackupYet: String
    val lastSavedPrefix: String
    val manageBackup: String
    val savedOnPhone: String
    val externalFolderConnected: String
    val previousVersions: String
    val chooseExternalFolder: String
    val changeBackupFolder: String
    val disconnectExternalFolder: String
    val localRetentionHint: String
    val aboutVersionLabel: String
    val aboutBuildLabel: String
    val localStorageAbout: String
    val addHomeWidget: String
    val githubModeBadge: String
    val noMatchingSettings: String
    val localBackupVersionsTitle: String
    val latestVersionLabel: String
    val beforeRestoreLabel: String
    val previousVersionPrefix: String
    // Drive backup settings
    val driveTitle: String
    val driveSubtitle: String
    val driveUnavailable: String
    val driveNotConnected: String
    fun driveLastVerified(value: String): String
    val driveNever: String
    val driveConnect: String
    val driveReconnect: String
    val driveAutoToggle: String
    val driveWifiOnly: String
    val driveBackupNow: String
    val driveRestoreFromDrive: String
    val driveDisconnect: String
    val driveFooter: String
    val driveConfirmTitle: String
    val driveConfirmBody: String
    val driveEnableBackup: String
    val driveVerifiedTitle: String
    val driveNoVerifiedFound: String
    val driveAuthCancelled: String
    val driveNoConsentDialog: String
    val drivePermissionDenied: String
    val driveConnectedEmpty: String
    fun driveConnectedFound(count: Int): String
    val drivePaused: String
    val driveAccountChanged: String
    val driveActionFailed: String
    fun driveAuthFailed(statusCode: Int): String
    val driveQueuedStatus: String
    val drivePreparingStatus: String
    val driveUploadingStatus: String
    val driveUpToDateStatus: String
    val driveUpToDateCleanupPending: String
    val driveBackedUpStatus: String
    val driveBackedUpCleanupPending: String
    val driveRestoredEnabledStatus: String
    val driveRestoredReadyStatus: String
    val driveConflictStatus: String
    val driveCompetingDetectedStatus: String
    val driveExistingBackupBlockedStatus: String
    val driveExistingBannerTitle: String
    fun driveExistingBannerBody(count: Int, latestAt: String): String
    val driveExistingDialogTitle: String
    fun driveExistingEmptyWarning(count: Int, latestAt: String): String
    fun driveExistingNonEmptyWarning(count: Int, latestAt: String, localIntakes: Int): String
    val driveRestoreFirstRecommended: String
    val driveExistingEmptyOverrideAck: String
    val driveExistingOverrideAck: String
    val driveOverrideExistingButton: String
    val driveConflictTitle: String
    val driveConflictBannerBody: String
    fun driveConflictDialogBody(remoteCount: Int, latestAt: String): String
    val driveConflictRemoteMissingBody: String
    val driveReviewAndRestore: String
    val driveResolveConflict: String
    val driveConflictOverrideAck: String
    val driveConflictKeepLocalButton: String
    val driveVersionLatest: String
    val driveVersionPredecessor: String
    val driveVersionCompeting: String
    val driveVersionOlder: String
    // Widget
    val widgetNowLabel: String
    val widgetTodayPrefix: String
    val widgetAddedPrefix: String
    val widgetUndo: String
    val widgetNoFavorites: String
    fun widgetMetricsDesc(nowMg: String, bedtimeMg: String, todayMg: String): String
    fun widgetAddDesc(name: String, mgText: String): String
    val widgetPinUnsupported: String

    companion object {
        val SV: AppStrings = SvStrings
        val EN: AppStrings = EnStrings

        fun forLanguage(language: AppLanguage): AppStrings = when (language) {
            AppLanguage.SV -> SV
            AppLanguage.EN -> EN
        }

        fun forCode(code: String?): AppStrings = forLanguage(AppLanguage.fromCode(code))
    }
}

private object SvStrings : AppStrings {
    override val language = AppLanguage.SV
    override val appName = "Caffeine Tracker"
    override val back = "Tillbaka"
    override val settings = "Inställningar"
    override val history = "Historik"
    override val drinks = "Drycker"
    override val statistics = "Statistik"
    override val newDrink = "Ny dryck"
    override val editDrink = "Redigera dryck"
    override val editIntake = "Redigera intag"
    override val close = "Stäng"
    override val cancel = "Avbryt"
    override val clear = "Rensa"
    override val delete = "Ta bort"
    override val edit = "Redigera"
    override val undo = "Ångra"
    override val saving = "Sparar…"
    override val openState = "Öppet"
    override val closedState = "Stängt"
    override val dbLoadError = "Kunde inte läsa databasen. Starta om appen för att försöka igen."
    override val intakeAdded = "tillagd"
    override val intakeUpdated = "ändrad"
    override val intakeDeleted = "borttagen"
    override val changeUndone = "Ändringen ångrades"
    override val drinkSaved = "Drycken sparades"
    override val drinkDeleted = "Drycken togs bort"
    override val settingsSaved = "Inställningarna sparades"
    override val backupExported = "Backup exporterad och verifierad"
    override val backupRestored = "Backupen återställdes. Tidigare data finns i säkerhetskopian före återställning"
    override val externalFolderEnabled = "Automatisk backup till vald mapp är aktiverad"
    override val actionFailed = "Åtgärden kunde inte slutföras."
    override val intakeMissing = "Intaget finns inte kvar."
    override val backupFailedPrefix = "Ändringen är sparad. Backup misslyckades: "
    override fun importedSummary(added: Int, duplicates: Int) = "$added intag importerades · $duplicates fanns redan"
    override val githubModeReadOnly = "GitHub-läget använder enbart separata exempeldata."
    override val fileOpenFailed = "Filen kunde inte öppnas."
    override val fileTooLarge = "Filen är för stor (max 20 MB)."
    override val unknownBackupFile = "Okänd backupfil."
    override val exportWriteFailed = "Backupfilen kunde inte skrivas."
    override val exportReadBackFailed = "Backupfilen kunde inte kontrolläsas."
    override val exportVerifyFailed = "Backupens kontrolläsning misslyckades."
    override val remainingNow = "Kvar nu"
    override val atTimePrefix = "Vid "
    override val intakeToday = "Intag idag"
    override val noFavoritesHint = "Sök en dryck ovanför eller markera favoriter under Drycker."
    override val caffeineCurve = "Koffeinkurva"
    override val returnToNow = "Återgå till nu"
    override fun chartSemantic(nowMg: String, bedtimeMg: String) = "Koffeinkurva. Nu $nowMg. Vid läggdags $bedtimeMg"
    override fun chartLegend(thresholdMg: String, bedtime: String, halfLife: String) = "Tröskel $thresholdMg · läggdags $bedtime · $halfLife h halveringstid"
    override fun addDrinkAction(name: String) = "Lägg till $name"
    override val editFavoriteAction = "Redigera favorit"
    override val saveAsFavorite = "Spara som favorit"
    override val drinkField = "Dryck"
    override val drinkNameField = "Dryckens namn"
    override val searchDrinkPlaceholder = "Sök dryck"
    override val clearDrinkSearch = "Rensa drycksökning"
    override val showMoreDrinks = "Visa fler drycker"
    override fun createDrinkPrompt(name: String) = "Skapa ”$name”"
    override val amountField = "Mängd"
    override val unitField = "Enhet"
    override val portionField = "Portion"
    override val portionPlaceholder = "Sök t.ex. 200 ml"
    override val estimatedAmount = "Uppskattad mängd"
    override val savedPortion = "Sparad portion"
    override fun usePortion(label: String) = "Använd $label"
    override val calculatedFromRecipe = "Beräknat från dryckens recept"
    override val noMatchingPortion = "Ingen sparad portion matchar."
    override val enterCaffeineManually = "Ange koffein själv"
    override val totalCaffeineField = "Totalt koffein (mg)"
    override val enterAmount = "Ange mängd"
    override val nowLabel = "Nu"
    override val moreDetails = "Fler detaljer"
    override val savedTimingPreserved = "Sparad dricktid och beräkning behålls"
    override val useCurrentTime = "Använd tiden nu"
    override val showPortionSuggestions = "Visa portionsförslag"
    override val enterCustomCaffeine = "Ange egen koffeinmängd"
    override val drinkingDurationField = "Dricktid (minuter)"
    override val durationZeroHint = "0 = ett tillfälle"
    override val noteField = "Anteckning"
    override val advancedCalculation = "Avancerad beräkning"
    override val servingsCountField = "Antal portioner"
    override val absorptionTimeField = "Tid till 90 % upptag (minuter)"
    override val categoryField = "Kategori"
    override val appliesOnlyToIntake = "Gäller bara detta intag."
    override val aboutPortionSuggestions = "Om portionsförslagen"
    override val portionSuggestionsHelp = "Förslagen räknas från dryckens sparade mängd. Kontrollera mot märkningen eller ditt recept."
    override fun standardConcentration(mgPerMl: String) = "Standardvärde: $mgPerMl mg/ml."
    override val futureIntakeError = "Intaget kan inte ligga i framtiden."
    override val durationWholeMinutesError = "Ange dricktid i hela minuter."
    override val absorptionMinutesError = "Ange upptagstid i minuter."
    override val invalidServingsError = "Ange ett giltigt portionsantal."
    override val checkAmountError = "Kontrollera mängden."
    override val addButton = "Lägg till"
    override val saveChangesButton = "Spara ändringar"
    override val deleteIntake = "Ta bort intag"
    override val deleteIntakeTitle = "Ta bort intaget?"
    override val nameField = "Namn"
    override val volumeInMl = "Volym i ml"
    override val otherPortion = "Annan portion"
    override val volumeMlField = "Volym (ml)"
    override val portionExampleField = "Portion, t.ex. 1 kopp"
    override val caffeineInPortionField = "Koffein i hela portionen (mg)"
    override val showInFavorites = "Visa bland favoriter"
    override val moreSettings = "Fler inställningar"
    override val categoryLabel = "Kategori"
    override val drinkEditFutureHint = "Ändringen gäller framtida intag. Historiken behåller sina sparade mängder."
    override val saveDrinkButton = "Spara dryck"
    override val deleteDrink = "Ta bort dryck"
    override val deleteDrinkTitle = "Ta bort drycken?"
    override val previousIntakesKept = "Tidigare intag behålls."
    override val defaultPortion = "1 portion"
    override val newSizeSuffix = " – ny storlek"
    override val imageIconLabel = "Bild / ikon"
    override val imageAutoSuffix = " · automatiskt"
    override val imageCustomSuffix = " · eget val"
    override val chooseImage = "Välj bild"
    override val imageReuseHint = "Samma bild kan användas för flera egna drycker och portionsstorlekar."
    override val autoFromName = "Automatiskt från namnet"
    override val imageDescPrefix = "Bild: "
    override val favoritesOnly = "Bara favoriter"
    override val customOnly = "Egna"
    override val customBadge = "Egen"
    override val searchDrink = "Sök dryck"
    override val clearSearch = "Rensa sökning"
    override val noDrinkFoundTitle = "Ingen dryck hittades"
    override val noDrinkFoundDetail = "Prova ett annat namn eller skapa en ny dryck."
    override val favoritesHeader = "Favoriter"
    override val dragFavoritesHint = "Håll inne och dra en favorit för att ändra ordning"
    override val otherDrinksHeader = "Övriga drycker"
    override val moveUp = "Flytta upp"
    override val moveDown = "Flytta ned"
    override val selectDrink = "Välj dryck"
    override val removeFavoritePrefix = "Ta bort favorit: "
    override val favoritePrefix = "Favorit: "
    override val optionsPrefix = "Alternativ: "
    override val createOtherSize = "Skapa annan storlek"
    override val yourHistory = "Din historik"
    override val matchingIntakesSuffix = " matchande intag"
    override val savedIntakesSuffix = " sparade intag"
    override val searchDrinkOrNote = "Sök dryck eller anteckning"
    override val dateChip = "Datum"
    override val fromDate = "Från datum"
    override val toDate = "Till datum"
    override val clearDateFilter = "Rensa datumfilter"
    override val historyStartsHere = "Historiken börjar här"
    override val noMatches = "Inga träffar"
    override val historyEmptyDetail = "Dina registreringar sparas med ursprunglig tidpunkt. Du kan också importera historik från JSON eller CSV."
    override val historyNoMatchesDetail = "Prova ett annat sökord eller datumintervall."
    override val importHistoryButton = "Importera historik"
    override val todayLabel = "Idag"
    override val yesterdayLabel = "Igår"
    override val statsIntakes = "Intag"
    override val statsLoggedDays = "Loggade dagar"
    override val statsDrinks = "Drycker"
    override val statsWeek = "Vecka"
    override val statsMonth = "Månad"
    override val statsYear = "År"
    override val statsAll = "Allt"
    override val statsCaffeine = "Koffein"
    override val statsIntakesMetric = "Intag"
    override val statsIntakesUnit = "intag"
    override val statsThisWeek = "Denna vecka"
    override val statsThisMonth = "Denna månad"
    override val statsThisYear = "Detta år"
    override val statsAllTime = "Hela historiken"
    override val previousPeriod = "Föregående period"
    override val nextPeriod = "Nästa period"
    override val perMonth = "Per månad"
    override val perDay = "Per dag"
    override val showMonthIntakes = "Visa månadens intag"
    override val showIntakes = "Visa intagen"
    override val tapChartMonth = "Tryck i grafen för att välja en månad"
    override val tapChartDay = "Tryck i grafen för att välja en dag"
    override val statsTotal = "Totalt"
    override val statsDailyAverage = "Snitt per dag"
    override val statsAllDays = "alla dagar"
    override val statsHighestDay = "Högsta dag"
    override val intakeCalendar = "Intagskalender"
    override val last13Weeks = "Senaste 13 veckorna"
    override val topDrinks = "Vanligaste dryckerna"
    override val inSelectedPeriod = "I den valda perioden"
    override val noIntakesInPeriod = "Inga intag i den här perioden."
    override val totalSuffix = " totalt"
    override val countSuffix = " st"
    override fun calendarSemantic(date: String, amount: String) = "Intagskalender. Vald dag $date: $amount"
    override val restoreBackupTitle = "Återställ säkerhetskopia?"
    override val importHistoryTitle = "Importera historik?"
    override val restoreReplacesHint = "Ersätter nuvarande historik, drycker och inställningar. Dina nuvarande data sparas först i en separat säkerhetskopia."
    override fun restoreDrinksCount(count: Int) = "$count drycker återställs också."
    override fun importNewAndDuplicate(newCount: Int, dupCount: Int) = "$newCount nya · $dupCount finns redan"
    override val importPreservesHint = "Intagen läggs till. Ursprungliga datum, portionsmängder, dricktid och upptagstid behålls."
    override fun importDrinksCount(count: Int) = "$count dryckesvarianter följer också med."
    override val restoreButton = "Återställ"
    override val importButton = "Importera"
    override val searchSettings = "Sök inställningar"
    override val catModelTitle = "Sömn och beräkning"
    override val catModelSummary = "Läggdags och avancerad koffeinmodell"
    override val catAppearanceTitle = "Utseende och språk"
    override val catAppearanceSummary = "Språk, tema, accentfärg och extra mörka ytor"
    override val catBackupTitle = "Backup och import"
    override val catBackupSummary = "Import, säkerhetskopior och återställning"
    override val catAboutTitle = "Om appen"
    override val catAboutSummary = "Version och lokal lagring"
    override val sectionCalculation = "Beräkning"
    override val calculationSearchTerms = "halveringstid upptag läggdags mål half life bedtime"
    override val bedtimeLabel = "Läggdags"
    override val halfLifeField = "Halveringstid (timmar)"
    override val eveningThresholdField = "Din kvällströskel (mg)"
    override val calculationDisclaimer = "Nivåerna är uppskattningar. Kvällströskeln är ett personligt riktvärde."
    override val checkDetailsError = "Kontrollera uppgifterna."
    override val saveCalculation = "Spara beräkning"
    override val sectionThemeAndColor = "Tema och färg"
    override val themeSearchTerms = "tema accentfärg mörkt ljust theme accent dark light"
    override val themeSystem = "System"
    override val themeLight = "Ljust"
    override val themeDark = "Mörkt"
    override val accentColorLabel = "Accentfärg"
    override val accentAuto = "Bakgrund"
    override val accentBlue = "Blå"
    override val accentAmber = "Bärnsten"
    override val accentTeal = "Turkos"
    override val accentGreen = "Grön"
    override val accentPink = "Rosa"
    override val accentRed = "Röd"
    override val accentOrange = "Orange"
    override val accentIndigo = "Indigo"
    override val extraDarkTitle = "Extra mörkt"
    override val extraDarkSummary = "Djupare bakgrunder i mörkt läge. Behåller din accentfärg."
    override val sectionLanguage = "Språk / Language"
    override val languageSearchTerms = "språk svenska english language"
    override val languageHint = "Ändrar appens texter och visar standarddrycker på valt språk utan att dela upp din statistik."
    override val sectionGoogleDrive = "Google Drive"
    override val driveSearchTerms = "google drive molnbackup synk konto återställ cloud backup"
    override val sectionImportExport = "Import och export"
    override val importExportSearchTerms = "historik säkerhetskopia json csv import export"
    override val importHistoryJsonCsv = "Importera historik (JSON/CSV)"
    override val exportBackup = "Exportera backup"
    override val restoreBackup = "Återställ backup"
    override val sectionAutoBackup = "Automatisk backup"
    override val autoBackupSearchTerms = "versioner lokal mapp säkerhetskopior automatic backup"
    override val noLocalBackupYet = "Ingen lokal backup sparad ännu."
    override val lastSavedPrefix = "Senast sparad: "
    override val manageBackup = "Hantera backup"
    override val savedOnPhone = "Sparas på mobilen"
    override val externalFolderConnected = "Extern mapp ansluten"
    override val previousVersions = "Tidigare versioner"
    override val chooseExternalFolder = "Välj extern backupmapp"
    override val changeBackupFolder = "Byt backupmapp"
    override val disconnectExternalFolder = "Koppla bort extern mapp"
    override val localRetentionHint = "Fem lokala versioner behålls. En separat kopia sparas före import och återställning."
    override val aboutVersionLabel = "Version"
    override val aboutBuildLabel = "Build"
    override val localStorageAbout = "Dina data sparas på mobilen. Inget konto krävs."
    override val addHomeWidget = "Lägg till hemskärmswidget"
    override val githubModeBadge = "GitHub-läge · separata exempeldata"
    override val noMatchingSettings = "Inga matchande inställningar."
    override val localBackupVersionsTitle = "Lokala backupversioner"
    override val latestVersionLabel = "Senaste versionen"
    override val beforeRestoreLabel = "Före senaste import/återställning"
    override val previousVersionPrefix = "Tidigare version "
    override val driveTitle = "Fullständig molnbackup · Google Drive"
    override val driveSubtitle = "Säkerhetskopierar historik, drycker, favoriter och inställningar till din egen Google Drive."
    override val driveUnavailable = "Molnbackup är inte tillgänglig just nu."
    override val driveNotConnected = "Inte ansluten"
    override fun driveLastVerified(value: String) = "Senast verifierad: $value"
    override val driveNever = "Aldrig"
    override val driveConnect = "Anslut Google Drive"
    override val driveReconnect = "Byt eller återanslut Google-konto"
    override val driveAutoToggle = "Automatisk molnbackup"
    override val driveWifiOnly = "Endast via Wi-Fi / obegränsat nätverk"
    override val driveBackupNow = "Säkerhetskopiera till Drive nu"
    override val driveRestoreFromDrive = "Återställ från Google Drive"
    override val driveDisconnect = "Koppla bort (behåll molnbackuper)"
    override val driveFooter = "Behåller senaste verifierade molnbackup, minst en föregående version samt eventuella konkurrerande versioner i Caffeine Tracker Backups. Äldre versioner rensas först efter kontrolläsning. Uppladdningar är inte totalsträckskrypterade."
    override val driveConfirmTitle = "Använd den här mobilen som backupkälla?"
    override val driveConfirmBody = "Detta laddar upp den här mobilens data till Google Drive. Befintliga verifierade molnbackuper ändras inte förrän den nya har kontrollästs, och minst en föregående version bevaras."
    override val driveEnableBackup = "Aktivera backup"
    override val driveVerifiedTitle = "Verifierade molnbackuper"
    override val driveNoVerifiedFound = "Ingen verifierad backup hittades för appen och kontot."
    override val driveAuthCancelled = "Google-anslutningen avbröts; inget laddades upp."
    override val driveNoConsentDialog = "Google gav ingen behörighetsdialog."
    override val drivePermissionDenied = "Drive-behörighet gavs inte. Inget laddades upp."
    override val driveConnectedEmpty = "Ansluten. Aktivera automatisk backup för att skydda dina data."
    override fun driveConnectedFound(count: Int) = "Ansluten. $count verifierade backuper hittades. Återställ först på en ny mobil innan du aktiverar uppladdning."
    override val drivePaused = "Automatisk molnbackup pausad"
    override val driveAccountChanged = "Det anslutna kontot har ändrats."
    override val driveActionFailed = "Drive-åtgärden misslyckades; befintliga backuper är oförändrade."
    override fun driveAuthFailed(statusCode: Int) = "Google-behörighet misslyckades ($statusCode). Registrera appens paketnamn/SHA-1 i Google Cloud, aktivera Drive API och lägg till ditt testkonto."
    override val driveQueuedStatus = "Ändringar köade; väntar på nätverk/bakgrundsjobb"
    override val drivePreparingStatus = "Förbereder fullständig backup…"
    override val driveUploadingStatus = "Laddar upp och verifierar…"
    override val driveUpToDateStatus = "Uppdaterad; verifierad molnbackup (föregående version bevaras)"
    override val driveUpToDateCleanupPending = "Uppdaterad; äldre Drive-backuper kunde inte rensas än och provas igen senare."
    override val driveBackedUpStatus = "Säkerhetskopierad och verifierad"
    override val driveBackedUpCleanupPending = "Säkerhetskopierad och verifierad; äldre Drive-backuper kunde inte tas bort än."
    override val driveRestoredEnabledStatus = "Återställd från Google Drive och synkad med molnbackupen."
    override val driveRestoredReadyStatus = "Återställd från Google Drive. Du kan nu säkert aktivera automatisk molnbackup."
    override val driveConflictStatus = "Konflikt upptäckt: en annan enhet har uppdaterat molnbackupen. Automatisk uppladdning är pausad och alla verifierade versioner bevaras."
    override val driveCompetingDetectedStatus = "Backup uppladdad, men en konkurrerande verifierad version upptäcktes samtidigt. Båda versionerna bevaras – granska innan fortsatt uppladdning."
    override val driveExistingBackupBlockedStatus = "Befintlig molnbackup hittades på Google Drive. Återställ först eller godkänn uttryckligen att denna mobil ska bli ny backupkälla."
    override val driveExistingBannerTitle = "Befintlig molnbackup hittades"
    override fun driveExistingBannerBody(count: Int, latestAt: String) = "Google Drive har redan $count verifierad(e) backup(er)${if (latestAt.isNotBlank()) " (senaste: $latestAt)" else ""}. Återställ först om detta är en ny eller tom installation så att befintlig historik inte ersätts."
    override val driveExistingDialogTitle = "Befintlig molnbackup på Google Drive"
    override fun driveExistingEmptyWarning(count: Int, latestAt: String) = "Den här mobilen har inga registrerade intag, men Google Drive har redan $count verifierad(e) backup(er)${if (latestAt.isNotBlank()) " (senaste: $latestAt)" else ""}. Återställ från Google Drive först så att du inte laddar upp tom data över din befintliga backup."
    override fun driveExistingNonEmptyWarning(count: Int, latestAt: String, localIntakes: Int) = "Google Drive har redan $count verifierad(e) backup(er)${if (latestAt.isNotBlank()) " (senaste: $latestAt)" else ""}, och den här mobilen har $localIntakes lokala intag. Återställ först om du vill hämta din molnhistorik, eller bekräfta uttryckligen att den här mobilens data ska laddas upp som nyaste version (föregående version bevaras)."
    override val driveRestoreFirstRecommended = "Återställ från Google Drive först (rekommenderas)"
    override val driveExistingEmptyOverrideAck = "Jag förstår att den här mobilen saknar historik och vill ändå ladda upp som nyaste molnbackup (föregående version bevaras)."
    override val driveExistingOverrideAck = "Jag vill behålla den här mobilens data och ladda upp som nyaste molnbackup (föregående version bevaras)."
    override val driveOverrideExistingButton = "Använd den här mobilen ändå"
    override val driveConflictTitle = "Konflikt i molnbackupen"
    override val driveConflictBannerBody = "En annan enhet har laddat upp en ny eller konkurrerande molnbackup. Automatisk uppladdning väntar på ditt val och alla verifierade versioner bevaras."
    override fun driveConflictDialogBody(remoteCount: Int, latestAt: String) = "Google Drive innehåller $remoteCount verifierad(e) backup(er)${if (latestAt.isNotBlank()) " (senaste: $latestAt)" else ""} som inte matchar den här mobilens senaste synk. Granska och återställ från Drive (rekommenderas), eller bekräfta att den här mobilens data ska laddas upp som nyaste version medan befintliga verifierade versioner bevaras."
    override val driveConflictRemoteMissingBody = "Den tidigare kända molnbackupen finns inte längre i Google Drive. Bekräfta att du vill börja om och ladda upp den här mobilens data."
    override val driveReviewAndRestore = "Granska och återställ från Drive"
    override val driveResolveConflict = "Hantera konflikt…"
    override val driveConflictOverrideAck = "Jag har granskat konflikten och vill ladda upp den här mobilens data som nyaste version (befintliga och konkurrerande versioner bevaras)."
    override val driveConflictKeepLocalButton = "Behåll den här mobilens data och ladda upp"
    override val driveVersionLatest = "Senaste versionen"
    override val driveVersionPredecessor = "Föregående version"
    override val driveVersionCompeting = "Konkurrerande version"
    override val driveVersionOlder = "Äldre version"
    override val widgetNowLabel = "Kvar nu · uppskattat"
    override val widgetTodayPrefix = "Idag · "
    override val widgetAddedPrefix = "Tillagt: "
    override val widgetUndo = "Ångra"
    override val widgetNoFavorites = "Spara favoriter i appen för att lägga till här."
    override fun widgetMetricsDesc(nowMg: String, bedtimeMg: String, todayMg: String) = "Kvar nu $nowMg, vid läggdags $bedtimeMg, intag idag $todayMg"
    override fun widgetAddDesc(name: String, mgText: String) = "Lägg till $name, $mgText"
    override val widgetPinUnsupported = "Lägg till Caffeine Tracker via hemskärmens widgetmeny."
}

private object EnStrings : AppStrings {
    override val language = AppLanguage.EN
    override val appName = "Caffeine Tracker"
    override val back = "Back"
    override val settings = "Settings"
    override val history = "History"
    override val drinks = "Drinks"
    override val statistics = "Statistics"
    override val newDrink = "New drink"
    override val editDrink = "Edit drink"
    override val editIntake = "Edit intake"
    override val close = "Close"
    override val cancel = "Cancel"
    override val clear = "Clear"
    override val delete = "Delete"
    override val edit = "Edit"
    override val undo = "Undo"
    override val saving = "Saving…"
    override val openState = "Expanded"
    override val closedState = "Collapsed"
    override val dbLoadError = "Could not read the database. Restart the app to try again."
    override val intakeAdded = "added"
    override val intakeUpdated = "updated"
    override val intakeDeleted = "removed"
    override val changeUndone = "Change undone"
    override val drinkSaved = "Drink saved"
    override val drinkDeleted = "Drink removed"
    override val settingsSaved = "Settings saved"
    override val backupExported = "Backup exported and verified"
    override val backupRestored = "Backup restored. Previous data was saved to the pre-restore backup"
    override val externalFolderEnabled = "Automatic backup to the selected folder is enabled"
    override val actionFailed = "Action could not be completed."
    override val intakeMissing = "Intake no longer exists."
    override val backupFailedPrefix = "Change saved. Backup failed: "
    override fun importedSummary(added: Int, duplicates: Int) = "$added intakes imported · $duplicates already existed"
    override val githubModeReadOnly = "GitHub mode uses separate sample data only."
    override val fileOpenFailed = "Could not open file."
    override val fileTooLarge = "File is too large (max 20 MB)."
    override val unknownBackupFile = "Unknown backup file."
    override val exportWriteFailed = "Could not write backup file."
    override val exportReadBackFailed = "Could not verify written backup file."
    override val exportVerifyFailed = "Backup verification failed."
    override val remainingNow = "Active now"
    override val atTimePrefix = "At "
    override val intakeToday = "Today"
    override val noFavoritesHint = "Search for a drink above or mark favorites under Drinks."
    override val caffeineCurve = "Caffeine curve"
    override val returnToNow = "Return to now"
    override fun chartSemantic(nowMg: String, bedtimeMg: String) = "Caffeine curve. Now $nowMg. At bedtime $bedtimeMg"
    override fun chartLegend(thresholdMg: String, bedtime: String, halfLife: String) = "Threshold $thresholdMg · bedtime $bedtime · $halfLife h half-life"
    override fun addDrinkAction(name: String) = "Add $name"
    override val editFavoriteAction = "Edit favorite"
    override val saveAsFavorite = "Save as favorite"
    override val drinkField = "Drink"
    override val drinkNameField = "Drink name"
    override val searchDrinkPlaceholder = "Search drink"
    override val clearDrinkSearch = "Clear drink search"
    override val showMoreDrinks = "Show more drinks"
    override fun createDrinkPrompt(name: String) = "Create “$name”"
    override val amountField = "Quantity"
    override val unitField = "Unit"
    override val portionField = "Serving"
    override val portionPlaceholder = "Search e.g. 200 ml"
    override val estimatedAmount = "Estimated amount"
    override val savedPortion = "Saved serving"
    override fun usePortion(label: String) = "Use $label"
    override val calculatedFromRecipe = "Calculated from drink recipe"
    override val noMatchingPortion = "No saved serving matches."
    override val enterCaffeineManually = "Enter caffeine manually"
    override val totalCaffeineField = "Total caffeine (mg)"
    override val enterAmount = "Enter amount"
    override val nowLabel = "Now"
    override val moreDetails = "More details"
    override val savedTimingPreserved = "Saved drinking time and absorption kept"
    override val useCurrentTime = "Use current time"
    override val showPortionSuggestions = "Show serving suggestions"
    override val enterCustomCaffeine = "Enter custom caffeine amount"
    override val drinkingDurationField = "Drinking duration (minutes)"
    override val durationZeroHint = "0 = single occasion"
    override val noteField = "Note"
    override val advancedCalculation = "Advanced calculation"
    override val servingsCountField = "Servings"
    override val absorptionTimeField = "Time to 90% absorption (minutes)"
    override val categoryField = "Category"
    override val appliesOnlyToIntake = "Applies to this intake only."
    override val aboutPortionSuggestions = "About serving suggestions"
    override val portionSuggestionsHelp = "Suggestions are calculated from the drink's saved amount. Verify against the label or your recipe."
    override fun standardConcentration(mgPerMl: String) = "Standard concentration: $mgPerMl mg/ml."
    override val futureIntakeError = "Intake cannot be in the future."
    override val durationWholeMinutesError = "Enter drinking duration in whole minutes."
    override val absorptionMinutesError = "Enter absorption time in minutes."
    override val invalidServingsError = "Enter a valid number of servings."
    override val checkAmountError = "Check the amount."
    override val addButton = "Add"
    override val saveChangesButton = "Save changes"
    override val deleteIntake = "Delete intake"
    override val deleteIntakeTitle = "Delete this intake?"
    override val nameField = "Name"
    override val volumeInMl = "Volume in ml"
    override val otherPortion = "Other serving"
    override val volumeMlField = "Volume (ml)"
    override val portionExampleField = "Serving, e.g. 1 cup"
    override val caffeineInPortionField = "Caffeine in whole serving (mg)"
    override val showInFavorites = "Show in favorites"
    override val moreSettings = "More settings"
    override val categoryLabel = "Category"
    override val drinkEditFutureHint = "Changes apply to future intakes. History keeps its saved amounts."
    override val saveDrinkButton = "Save drink"
    override val deleteDrink = "Delete drink"
    override val deleteDrinkTitle = "Delete this drink?"
    override val previousIntakesKept = "Previous intakes are kept."
    override val defaultPortion = "1 serving"
    override val newSizeSuffix = " – new size"
    override val imageIconLabel = "Image / icon"
    override val imageAutoSuffix = " · automatic"
    override val imageCustomSuffix = " · custom"
    override val chooseImage = "Choose image"
    override val imageReuseHint = "The same image can be reused across custom drinks and serving sizes."
    override val autoFromName = "Automatic from name"
    override val imageDescPrefix = "Image: "
    override val favoritesOnly = "Favorites only"
    override val customOnly = "Custom"
    override val customBadge = "Custom"
    override val searchDrink = "Search drink"
    override val clearSearch = "Clear search"
    override val noDrinkFoundTitle = "No drink found"
    override val noDrinkFoundDetail = "Try another name or create a new drink."
    override val favoritesHeader = "Favorites"
    override val dragFavoritesHint = "Hold and drag a favorite to reorder"
    override val otherDrinksHeader = "Other drinks"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val selectDrink = "Select drink"
    override val removeFavoritePrefix = "Remove favorite: "
    override val favoritePrefix = "Favorite: "
    override val optionsPrefix = "Options: "
    override val createOtherSize = "Create another size"
    override val yourHistory = "Your history"
    override val matchingIntakesSuffix = " matching intakes"
    override val savedIntakesSuffix = " saved intakes"
    override val searchDrinkOrNote = "Search drink or note"
    override val dateChip = "Date"
    override val fromDate = "From date"
    override val toDate = "To date"
    override val clearDateFilter = "Clear date filter"
    override val historyStartsHere = "Your history starts here"
    override val noMatches = "No matches"
    override val historyEmptyDetail = "Your entries are saved with their original timestamps. You can also import history from JSON or CSV."
    override val historyNoMatchesDetail = "Try another search term or date range."
    override val importHistoryButton = "Import history"
    override val todayLabel = "Today"
    override val yesterdayLabel = "Yesterday"
    override val statsIntakes = "Intakes"
    override val statsLoggedDays = "Logged days"
    override val statsDrinks = "Drinks"
    override val statsWeek = "Week"
    override val statsMonth = "Month"
    override val statsYear = "Year"
    override val statsAll = "All"
    override val statsCaffeine = "Caffeine"
    override val statsIntakesMetric = "Intakes"
    override val statsIntakesUnit = "intakes"
    override val statsThisWeek = "This week"
    override val statsThisMonth = "This month"
    override val statsThisYear = "This year"
    override val statsAllTime = "All time"
    override val previousPeriod = "Previous period"
    override val nextPeriod = "Next period"
    override val perMonth = "Per month"
    override val perDay = "Per day"
    override val showMonthIntakes = "Show month's intakes"
    override val showIntakes = "Show intakes"
    override val tapChartMonth = "Tap the chart to select a month"
    override val tapChartDay = "Tap the chart to select a day"
    override val statsTotal = "Total"
    override val statsDailyAverage = "Daily average"
    override val statsAllDays = "all days"
    override val statsHighestDay = "Highest day"
    override val intakeCalendar = "Intake calendar"
    override val last13Weeks = "Last 13 weeks"
    override val topDrinks = "Most common drinks"
    override val inSelectedPeriod = "In the selected period"
    override val noIntakesInPeriod = "No intakes in this period."
    override val totalSuffix = " total"
    override val countSuffix = "×"
    override fun calendarSemantic(date: String, amount: String) = "Intake calendar. Selected day $date: $amount"
    override val restoreBackupTitle = "Restore backup?"
    override val importHistoryTitle = "Import history?"
    override val restoreReplacesHint = "Replaces current history, drinks and settings. Your current data is saved first in a separate safety backup."
    override fun restoreDrinksCount(count: Int) = "$count drinks will also be restored."
    override fun importNewAndDuplicate(newCount: Int, dupCount: Int) = "$newCount new · $dupCount already exist"
    override val importPreservesHint = "Intakes will be merged. Original timestamps, serving sizes, drinking duration and absorption are preserved."
    override fun importDrinksCount(count: Int) = "$count drink variants are also included."
    override val restoreButton = "Restore"
    override val importButton = "Import"
    override val searchSettings = "Search settings"
    override val catModelTitle = "Sleep and calculation"
    override val catModelSummary = "Bedtime and advanced caffeine model"
    override val catAppearanceTitle = "Appearance and language"
    override val catAppearanceSummary = "Language, theme, accent color and extra dark surfaces"
    override val catBackupTitle = "Backup and import"
    override val catBackupSummary = "Import, backups and restore"
    override val catAboutTitle = "About"
    override val catAboutSummary = "Version and local storage"
    override val sectionCalculation = "Calculation"
    override val calculationSearchTerms = "half life absorption bedtime threshold goal halveringstid läggdags"
    override val bedtimeLabel = "Bedtime"
    override val halfLifeField = "Half-life (hours)"
    override val eveningThresholdField = "Bedtime threshold (mg)"
    override val calculationDisclaimer = "Levels are estimates. The bedtime threshold is a personal guideline."
    override val checkDetailsError = "Check your inputs."
    override val saveCalculation = "Save calculation"
    override val sectionThemeAndColor = "Theme and color"
    override val themeSearchTerms = "theme accent dark light extra dark tema accentfärg"
    override val themeSystem = "System"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val accentColorLabel = "Accent color"
    override val accentAuto = "Wallpaper"
    override val accentBlue = "Blue"
    override val accentAmber = "Amber"
    override val accentTeal = "Teal"
    override val accentGreen = "Green"
    override val accentPink = "Pink"
    override val accentRed = "Red"
    override val accentOrange = "Orange"
    override val accentIndigo = "Indigo"
    override val extraDarkTitle = "Extra dark"
    override val extraDarkSummary = "Deeper backgrounds in dark mode while keeping your accent color."
    override val sectionLanguage = "Language / Språk"
    override val languageSearchTerms = "language english swedish språk svenska"
    override val languageHint = "Changes app text and matches built-in drink names to your language without splitting statistics."
    override val sectionGoogleDrive = "Google Drive"
    override val driveSearchTerms = "google drive cloud backup sync account restore molnbackup"
    override val sectionImportExport = "Import and export"
    override val importExportSearchTerms = "history backup json csv import export historik"
    override val importHistoryJsonCsv = "Import history (JSON/CSV)"
    override val exportBackup = "Export backup"
    override val restoreBackup = "Restore backup"
    override val sectionAutoBackup = "Automatic backup"
    override val autoBackupSearchTerms = "versions local folder backups automatisk backup"
    override val noLocalBackupYet = "No local backup saved yet."
    override val lastSavedPrefix = "Last saved: "
    override val manageBackup = "Manage backup"
    override val savedOnPhone = "Saved on device"
    override val externalFolderConnected = "External folder connected"
    override val previousVersions = "Previous versions"
    override val chooseExternalFolder = "Choose external backup folder"
    override val changeBackupFolder = "Change backup folder"
    override val disconnectExternalFolder = "Disconnect external folder"
    override val localRetentionHint = "Five local versions are kept. A separate safety copy is saved before import and restore."
    override val aboutVersionLabel = "Version"
    override val aboutBuildLabel = "Build"
    override val localStorageAbout = "Your data stays on your phone. No account required."
    override val addHomeWidget = "Add home screen widget"
    override val githubModeBadge = "GitHub mode · separate sample data"
    override val noMatchingSettings = "No matching settings."
    override val localBackupVersionsTitle = "Local backup versions"
    override val latestVersionLabel = "Latest version"
    override val beforeRestoreLabel = "Before latest import/restore"
    override val previousVersionPrefix = "Previous version "
    override val driveTitle = "Full cloud backup · Google Drive"
    override val driveSubtitle = "Backs up history, drinks, favorites and settings to your own Google Drive."
    override val driveUnavailable = "Cloud backup is unavailable right now."
    override val driveNotConnected = "Not connected"
    override fun driveLastVerified(value: String) = "Last verified: $value"
    override val driveNever = "Never"
    override val driveConnect = "Connect Google Drive"
    override val driveReconnect = "Switch or reconnect Google account"
    override val driveAutoToggle = "Automatic cloud backup"
    override val driveWifiOnly = "Wi-Fi / unmetered network only"
    override val driveBackupNow = "Back up to Drive now"
    override val driveRestoreFromDrive = "Restore from Google Drive"
    override val driveDisconnect = "Disconnect (keep cloud backups)"
    override val driveFooter = "Keeps the latest verified cloud backup, at least one successful predecessor, and any competing verified versions in Caffeine Tracker Backups. Older linear ancestors are pruned only after read-back verification. Uploads are not end-to-end encrypted."
    override val driveConfirmTitle = "Use this phone as backup source?"
    override val driveConfirmBody = "This uploads this phone's data to Google Drive. Existing verified backups are unchanged until read-back succeeds, and at least one previous version is preserved."
    override val driveEnableBackup = "Enable backup"
    override val driveVerifiedTitle = "Verified cloud backups"
    override val driveNoVerifiedFound = "No verified backup found for this app and account."
    override val driveAuthCancelled = "Google connection cancelled; nothing was uploaded."
    override val driveNoConsentDialog = "Google did not return a permission dialog."
    override val drivePermissionDenied = "Drive permission was not granted. Nothing was uploaded."
    override val driveConnectedEmpty = "Connected. Enable automatic backup to protect your data."
    override fun driveConnectedFound(count: Int) = "Connected. $count verified backup(s) found. Restore first on a new phone before enabling uploads."
    override val drivePaused = "Automatic cloud backup paused"
    override val driveAccountChanged = "The connected account has changed."
    override val driveActionFailed = "Drive action failed; existing backups are unchanged."
    override fun driveAuthFailed(statusCode: Int) = "Google authorization failed ($statusCode). Register the app's package name/SHA-1 in Google Cloud, enable Drive API and add your test account."
    override val driveQueuedStatus = "Changes queued; waiting for network/background job"
    override val drivePreparingStatus = "Preparing full backup…"
    override val driveUploadingStatus = "Uploading and verifying…"
    override val driveUpToDateStatus = "Up to date; verified cloud backup (previous version preserved)"
    override val driveUpToDateCleanupPending = "Up to date; older Drive backups could not be cleaned up yet and will be retried later."
    override val driveBackedUpStatus = "Backed up and verified"
    override val driveBackedUpCleanupPending = "Backed up and verified; older Drive backups could not be deleted yet."
    override val driveRestoredEnabledStatus = "Restored from Google Drive and synced with the cloud backup."
    override val driveRestoredReadyStatus = "Restored from Google Drive. You can now safely enable automatic cloud backup."
    override val driveConflictStatus = "Conflict detected: another device updated the cloud backup. Automatic uploads are paused and all verified versions are preserved."
    override val driveCompetingDetectedStatus = "Backup uploaded, but a competing verified version appeared at the same time. Both versions are preserved—review before continuing uploads."
    override val driveExistingBackupBlockedStatus = "An existing cloud backup was found on Google Drive. Restore first or explicitly confirm using this phone as the new backup source."
    override val driveExistingBannerTitle = "Existing cloud backup found"
    override fun driveExistingBannerBody(count: Int, latestAt: String) = "Google Drive already has $count verified backup(s)${if (latestAt.isNotBlank()) " (latest: $latestAt)" else ""}. Restore first on a new or empty installation so existing history is not superseded."
    override val driveExistingDialogTitle = "Existing Google Drive backup found"
    override fun driveExistingEmptyWarning(count: Int, latestAt: String) = "This phone has no logged intakes yet, while Google Drive already has $count verified backup(s)${if (latestAt.isNotBlank()) " (latest: $latestAt)" else ""}. Restore from Google Drive first so you do not supersede your existing cloud backup with empty data."
    override fun driveExistingNonEmptyWarning(count: Int, latestAt: String, localIntakes: Int) = "Google Drive already has $count verified backup(s)${if (latestAt.isNotBlank()) " (latest: $latestAt)" else ""}, and this phone has $localIntakes local intake(s). Restore first to bring your cloud history here, or explicitly confirm using this phone's data as the newest backup (the previous version will be preserved)."
    override val driveRestoreFirstRecommended = "Restore from Google Drive first (recommended)"
    override val driveExistingEmptyOverrideAck = "I understand this phone has no history and still want to upload it as the newest cloud backup (previous version will be preserved)."
    override val driveExistingOverrideAck = "I want to keep this phone's data and upload it as the newest cloud backup (previous version will be preserved)."
    override val driveOverrideExistingButton = "Use this phone anyway"
    override val driveConflictTitle = "Cloud backup conflict"
    override val driveConflictBannerBody = "Another device uploaded a newer or competing cloud backup. Automatic uploads are waiting for your choice and all verified versions are preserved."
    override fun driveConflictDialogBody(remoteCount: Int, latestAt: String) = "Google Drive contains $remoteCount verified backup(s)${if (latestAt.isNotBlank()) " (latest: $latestAt)" else ""} that do not match this phone's last synced version. Review and restore from Drive (recommended), or confirm uploading this phone's data as the newest version while preserving existing verified versions."
    override val driveConflictRemoteMissingBody = "The previously synced cloud backup is no longer present in Google Drive. Confirm that you want to upload this phone's data as a fresh cloud backup."
    override val driveReviewAndRestore = "Review & restore from Drive"
    override val driveResolveConflict = "Resolve conflict…"
    override val driveConflictOverrideAck = "I have reviewed the conflict and want to upload this phone's data as the newest backup (existing and competing versions will be preserved)."
    override val driveConflictKeepLocalButton = "Keep this phone's data & upload"
    override val driveVersionLatest = "Latest version"
    override val driveVersionPredecessor = "Previous version"
    override val driveVersionCompeting = "Competing version"
    override val driveVersionOlder = "Older version"
    override val widgetNowLabel = "Active now · estimated"
    override val widgetTodayPrefix = "Today · "
    override val widgetAddedPrefix = "Added: "
    override val widgetUndo = "Undo"
    override val widgetNoFavorites = "Save favorites in the app to add them here."
    override fun widgetMetricsDesc(nowMg: String, bedtimeMg: String, todayMg: String) = "Active now $nowMg, at bedtime $bedtimeMg, today $todayMg"
    override fun widgetAddDesc(name: String, mgText: String) = "Add $name, $mgText"
    override val widgetPinUnsupported = "Add Caffeine Tracker from your home screen's widget picker."
}
