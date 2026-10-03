package com.uniplanner.app.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniplanner.app.ui.screens.ScreenHeader
import com.uniplanner.app.ui.theme.Bricolage
import com.uniplanner.app.ui.theme.Coral
import com.uniplanner.app.ui.theme.Ink
import com.uniplanner.app.ui.theme.Mono

@Composable
fun FlashcardsScreen() {
    val ctx = LocalContext.current
    val s = Flashcards.flow(ctx).collectAsState().value ?: FlashState()
    var openDeck by rememberSaveable { mutableStateOf<Long?>(null) }
    var studying by rememberSaveable { mutableStateOf(false) }
    var addingDeck by remember { mutableStateOf(false) }
    var addingCard by remember { mutableStateOf(false) }
    BackHandler(enabled = openDeck != null) { if (studying) studying = false else openDeck = null }

    val deck = s.decks.firstOrNull { it.id == openDeck }
    when {
        deck != null && studying -> StudyCards(deck, Flashcards.due(s, deck.id), onBack = { studying = false })
        deck != null -> {
            val cards = s.cards.filter { it.deckId == deck.id }
            val due = Flashcards.due(s, deck.id).size
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { openDeck = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { Flashcards.deleteDeck(ctx, deck.id); openDeck = null }) {
                            Icon(Icons.Filled.DeleteOutline, "Apagar baralho")
                        }
                    }
                }
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(deck.color)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(deck.name, fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Ink)
                        Text("${cards.size} cartões · $due para rever hoje", fontFamily = Mono, fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { studying = true },
                            enabled = due > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                        ) { Text(if (due > 0) "Rever $due cartões" else "Tudo revisto por hoje ✅") }
                    }
                }
                item {
                    OutlinedButton(onClick = { addingCard = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null)
                        Text("Novo cartão")
                    }
                }
                items(cards, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.front, style = MaterialTheme.typography.titleSmall)
                            Text(c.back, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Text("📦${c.box}", fontSize = 12.sp)
                        IconButton(onClick = { Flashcards.deleteCard(ctx, c.id) }) { Icon(Icons.Filled.DeleteOutline, "Apagar") }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ScreenHeader("Cartões de estudo", "Repetição espaçada") }
            item {
                Text(
                    "Escreve uma pergunta de um lado e a resposta do outro. Os cartões que sabes aparecem cada vez menos; os que falhas voltam no mesmo dia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(s.decks, key = { it.id }) { d ->
                val due = Flashcards.due(s, d.id).size
                val total = s.cards.count { it.deckId == d.id }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(d.color)).clickable { openDeck = d.id }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                        Text("$total cartões", fontFamily = Mono, fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
                    }
                    if (due > 0) {
                        Text(
                            "$due hoje",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Coral).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            item {
                Button(onClick = { addingDeck = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Text("Novo baralho")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (addingDeck) {
        TextDialog("Novo baralho", listOf("Nome (ex.: Anatomia, Cálculo)"), onDismiss = { addingDeck = false }) { v ->
            Flashcards.addDeck(ctx, v[0]); addingDeck = false
        }
    }
    if (addingCard && deck != null) {
        TextDialog("Novo cartão", listOf("Pergunta", "Resposta"), onDismiss = { addingCard = false }) { v ->
            Flashcards.addCard(ctx, deck.id, v[0], v[1]); addingCard = false
        }
    }
}

@Composable
private fun TextDialog(title: String, labels: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var values by remember { mutableStateOf(labels.map { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, l ->
                    OutlinedTextField(values[i], { v -> values = values.toMutableList().also { it[i] = v } }, label = { Text(l) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values) }, enabled = values.all { it.isNotBlank() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** One card at a time: tap to turn it, then say whether you knew it. */
@Composable
private fun StudyCards(deck: Deck, due: List<Flashcard>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var flipped by remember { mutableStateOf(false) }
    val card = due.firstOrNull()
    val turn by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = 0.6f), label = "flip")
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(deck.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${due.size} por rever", fontFamily = Mono, fontSize = 12.sp)
        }
        if (card == null) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 64.sp)
                Text("Revisão feita!", fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Voltar") }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .graphicsLayer { rotationY = turn; cameraDistance = 14f * density }
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (turn <= 90f) Color(deck.color) else Ink)
                    .clickable { flipped = !flipped }
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (turn <= 90f) card.front else card.back,
                    fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                    color = if (turn <= 90f) Ink else Color.White,
                    modifier = Modifier.graphicsLayer { rotationY = if (turn > 90f) 180f else 0f },
                )
            }
            if (!flipped) {
                Text("Toca no cartão para ver a resposta", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { Flashcards.answer(ctx, card, right = false); flipped = false },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("😬 Errei") }
                    Button(
                        onClick = { Flashcards.answer(ctx, card, right = true); flipped = false },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    ) { Text("😎 Acertei") }
                }
            }
        }
    }
}
