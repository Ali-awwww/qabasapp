package com.qabas.app

/**
 * Prompt guidance reflecting the Islamic production constraints approved so far.
 *
 * Provider prompts are guidance, not a technical safety guarantee. Generated assets
 * must still be reviewed; the policy should later be enforced by validation/review flows.
 */
internal object QabasIslamicContentPolicy {
    private const val VISUAL_RULES = """
        QABAS ISLAMIC CONTENT RULES (apply to the entire generated visual):
        - Depict men only. Do not depict women or female characters, including animation.
        - No romantic or love scenes, including between spouses. Family/social bonds
          such as parenthood, siblings, and friendship may be depicted without romance.
        - Do not visually represent Islamic religious or historical figures: prophets,
          messengers, companions, scholars, or well-known Islamic figures. They may only
          be mentioned in narration/text without visual embodiment.
        - Do not depict magic, sorcery, fortune-telling, occult practices, or magical
          supernatural elements, including in fiction, animation, or historical scenes.
        - Men's clothing may be traditional or contemporary, but must be modest:
          no revealing, transparent, or sexually suggestive clothing.
        - Dramatic/historical fighting is permitted within limits. Avoid graphic cruelty,
          explicit torture, excessive brutality, glorification of oppression, or criminal
          aggression. Respect context and educational/documentary purpose.
        - Never mock or disrespect Islam, sacred symbols, or religious texts.
        - Do not invent, misquote, or alter Quranic verses or hadith. Only include
          religious quotations when exact wording and reliable sourcing are verified.
        - No music or musical instruments. Audio, if applicable, may consist of spoken
          narration, instrument-free nasheed, or permissible natural/environmental effects.
        - Preserve the user's requested purpose and content; do not add creative elements
          that the user did not request.
    """

    fun forVisualGeneration(prompt: String): String =
        prompt.trim() + "\n\n" + VISUAL_RULES.trimIndent()
}
