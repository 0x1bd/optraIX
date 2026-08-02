package org.kvxd.optraix.command

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.Locale
import java.util.concurrent.CompletableFuture

fun SuggestionsBuilder.suggestMatching(
    candidates: Iterable<String>,
): CompletableFuture<Suggestions> {
    val remaining = remaining.lowercase(Locale.ROOT)

    candidates
        .asSequence()
        .filter { it.lowercase(Locale.ROOT).startsWith(remaining) }
        .distinct()
        .forEach(::suggest)

    return buildFuture()
}