package com.roinur.caffeinetracker.core.model

/** A receipt holds exact values so undo also restores imported metadata. */
data class IntakeChange(val before: Intake?, val after: Intake?, val token: String = java.util.UUID.randomUUID().toString()) {
    val row: Intake get() = after ?: requireNotNull(before)
}

fun reviseIntake(original: Intake, name: String, dose: Double, timestamp: Long,
    duration: Int, absorption: Double, note: String, category: String, portion: String,
    imageKey: String?, servings: Double = original.servings): Intake = original.copy(
    name = name, caffeineMg = dose, timestamp = timestamp, durationMinutes = duration,
    absorptionMinutes90 = absorption, note = note, category = category, servingUnit = portion,
    imageKey = imageKey, servings = servings,
    caffeinePerServingMg = if(dose == original.caffeineMg && servings == original.servings)
        original.caffeinePerServingMg else dose / servings
)

fun undoTarget(change: IntakeChange, current: Intake?): Intake? {
    require(current == change.after) { "Intaget har ändrats igen. Öppna det i historiken." }
    return change.before
}
