package com.uniplanner.app.study

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Deck(val id: Long, val name: String, val color: Long)

/** A card in a Leitner box: right answers move it to a later box, seen less often. */
data class Flashcard(val id: Long, val deckId: Long, val front: String, val back: String, val box: Int = 1, val dueDay: Long = 0)

data class FlashState(val decks: List<Deck> = emptyList(), val cards: List<Flashcard> = emptyList(), val reviews: Int = 0)

/** Spaced repetition kept on the phone: days until a card comes back, per box. */
object Flashcards {
    private val intervals = listOf(0, 1, 2, 4, 8, 16, 32)
    private const val PREFS = "flashcards"
    private val state = MutableStateFlow<FlashState?>(null)

    val deckColors = listOf(0xFFDCE3FC, 0xFFDDEBD0, 0xFFEADCF5, 0xFFF6E6C3, 0xFFFADBD2, 0xFFD7EEF0)

    fun today(): Long = LocalDate.now().toEpochDay()

    fun flow(ctx: Context): StateFlow<FlashState?> {
        if (state.value == null) state.value = load(ctx)
        @Suppress("UNCHECKED_CAST")
        return state as StateFlow<FlashState?>
    }

    fun reload(ctx: Context) {
        state.value = load(ctx)
    }

    fun due(s: FlashState, deckId: Long): List<Flashcard> =
        s.cards.filter { it.deckId == deckId && it.dueDay <= today() }.sortedBy { it.box }

    fun addDeck(ctx: Context, name: String) = edit(ctx) { s ->
        val id = (s.decks.maxOfOrNull { it.id } ?: 0) + 1
        s.copy(decks = s.decks + Deck(id, name.trim(), deckColors[(id % deckColors.size).toInt()]))
    }

    fun deleteDeck(ctx: Context, deckId: Long) = edit(ctx) { s ->
        s.copy(decks = s.decks.filter { it.id != deckId }, cards = s.cards.filter { it.deckId != deckId })
    }

    fun addCard(ctx: Context, deckId: Long, front: String, back: String) = edit(ctx) { s ->
        val id = (s.cards.maxOfOrNull { it.id } ?: 0) + 1
        s.copy(cards = s.cards + Flashcard(id, deckId, front.trim(), back.trim(), 1, today()))
    }

    fun deleteCard(ctx: Context, cardId: Long) = edit(ctx) { s -> s.copy(cards = s.cards.filter { it.id != cardId }) }

    /** A right answer moves the card one box on; a wrong one sends it back to the first box, for today. */
    fun answer(ctx: Context, card: Flashcard, right: Boolean) = edit(ctx) { s ->
        val box = if (right) (card.box + 1).coerceAtMost(intervals.size - 1) else 1
        val due = today() + if (right) intervals[box] else 0
        s.copy(cards = s.cards.map { if (it.id == card.id) it.copy(box = box, dueDay = due) else it }, reviews = s.reviews + 1)
    }

    private fun edit(ctx: Context, change: (FlashState) -> FlashState) {
        val next = change(state.value ?: load(ctx))
        state.value = next
        save(ctx, next)
    }

    private fun load(ctx: Context): FlashState = runCatching {
        val o = JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("data", null) ?: return FlashState())
        val decks = o.getJSONArray("decks").let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Deck(it.getLong("id"), it.getString("name"), it.getLong("color")) } }
        }
        val cards = o.getJSONArray("cards").let { a ->
            (0 until a.length()).map { i ->
                a.getJSONObject(i).let {
                    Flashcard(it.getLong("id"), it.getLong("deck"), it.getString("f"), it.getString("b"), it.getInt("box"), it.getLong("due"))
                }
            }
        }
        FlashState(decks, cards, o.optInt("reviews"))
    }.getOrDefault(FlashState())

    private fun save(ctx: Context, s: FlashState) {
        val o = JSONObject()
            .put("decks", JSONArray(s.decks.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color) }))
            .put(
                "cards",
                JSONArray(
                    s.cards.map {
                        JSONObject().put("id", it.id).put("deck", it.deckId).put("f", it.front).put("b", it.back).put("box", it.box).put("due", it.dueDay)
                    },
                ),
            )
            .put("reviews", s.reviews)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", o.toString()).apply()
    }
}
