package com.primalapp.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.android.R

/** Экран «Подготовка к бою»: какую колоду карт реакций подготовить для выбранной сложности (0–3). */
@Composable
fun ReactionDeckHint(difficulty: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Подготовьте колоду карт реакций: ", fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Image(
            painter = painterResource(reactionDeckImage(difficulty)),
            contentDescription = "Колода карт реакций, уровень ${difficulty.coerceIn(0, 3)}",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            // Картинки уровней — руны на белом фоне, 878×361
            modifier = Modifier.fillMaxWidth().height(72.dp)
        )
    }
}

@DrawableRes
private fun reactionDeckImage(difficulty: Int): Int = when (difficulty.coerceIn(0, 3)) {
    0 -> R.drawable.reaction_deck_level_0
    1 -> R.drawable.reaction_deck_level_1
    2 -> R.drawable.reaction_deck_level_2
    else -> R.drawable.reaction_deck_level_3
}
