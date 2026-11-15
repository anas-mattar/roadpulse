package com.roadpulse.app.i18n

/** Screen language of the current composition (provided at the root, see MainActivity). */
val LocalStrings = androidx.compose.runtime.staticCompositionLocalOf<AppStrings> { EnglishStrings }

/** Shortcut for composables: `strings.cancel`. */
val strings: AppStrings
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalStrings.current

/** Screen language outside composition (view model messages, notification). */
fun currentStrings(): AppStrings = stringsFor(com.roadpulse.app.nav.VoiceSettings.language.value)
