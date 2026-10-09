package com.qabas.app

/**
 * Compatibility shim: callers use RealGeminiService.chatWithAssistant,
 * but the implementation lives on RealMediaLibraryService.
 * This extension resolves the Unresolved reference compile error.
 */
suspend fun RealGeminiService.chatWithAssistant(
    messages: List<Pair<Boolean, String>>,
    customSystemInstruction: String? = null
): String = RealMediaLibraryService.chatWithAssistant(messages, customSystemInstruction)
