package com.roadpulse.app.nav

/** Small clean-ups of routing-server prompt text before it is shown or spoken. */
object PromptText {
    // Words the routing server sometimes capitalises after "Then" ("Then Your destination…").
    private val lowercaseAfterThen = setOf(
        "Your", "The", "Turn", "Keep", "Bear", "Take", "Continue", "Make", "Enter", "Exit",
        "Stay", "Drive", "Go", "Head", "Merge", "You",
    )
    private val thenWord = Regex("""(\bThen )([A-Z][a-z]+)""")

    fun tidy(text: String): String = thenWord.replace(text.trim()) { m ->
        val word = m.groupValues[2]
        if (word in lowercaseAfterThen) m.groupValues[1] + word.lowercase() else m.value
    }
}
