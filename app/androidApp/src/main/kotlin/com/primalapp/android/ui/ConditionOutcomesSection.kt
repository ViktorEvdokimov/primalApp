package com.primalapp.android.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.domain.ConditionOutcome
import com.primalapp.domain.ConditionOutcomeType

private val SECTION_TITLES = listOf(
    ConditionOutcomeType.QUEST to "Условные задания:",
    ConditionOutcomeType.ACHIEVEMENT to "Условные достижения:",
    ConditionOutcomeType.REWARD to "Условные награды:"
)

/** Условные правила в формулировке правил и под каждым — результат проверки для текущей кампании. */
@Composable
fun ConditionOutcomesSection(outcomes: List<ConditionOutcome>) {
    SECTION_TITLES.forEach { (type, title) ->
        val items = outcomes.filter { it.type == type }
        if (items.isEmpty()) return@forEach
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
        items.forEach { outcome ->
            Text(outcome.description, fontSize = 13.sp)
            Text(
                outcome.result,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
    }
}
