package com.primalapp.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.ResourceType
import com.primalapp.model.campaign.SkillBranch
import com.primalapp.viewmodel.CampaignUiState
import com.primalapp.viewmodel.CampaignViewModel

@Composable
fun CampaignSheetScreen(state: CampaignUiState, viewModel: CampaignViewModel) {
    val campaign = state.currentCampaign ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(campaign.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Глава ${campaign.currentChapter}", fontSize = 16.sp)
            Text("Кузня ${campaign.forgeLevel}", fontSize = 16.sp)
            Text("Лаб ${campaign.labLevel}", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Глава:")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.onUpdateChapter(campaign.currentChapter - 1) }, modifier = Modifier.padding(end = 4.dp)) {
                    Text("-")
                }
                Text("${campaign.currentChapter}")
                Button(onClick = { viewModel.onUpdateChapter(campaign.currentChapter + 1) }, modifier = Modifier.padding(start = 4.dp)) {
                    Text("+")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (state.hunters.isNotEmpty()) {
            Text("Охотники:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.hunters.forEachIndexed { index, hunter ->
                    val isSelected = index == state.selectedHunterIndex
                    Button(
                        onClick = { viewModel.onHunterSelected(index) },
                        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text("${hunter.playerName}\n(${hunter.className.displayName})", fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SkillTreeSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            ResourcesSection(state, viewModel)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Заметки:", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = state.notes,
            onValueChange = { viewModel.onNotesChanged(it) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            maxLines = 10
        )
        Button(onClick = { viewModel.onSaveNotes() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Сохранить заметки")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onStartCampaignBattle() },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Начать бой", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (state.saveMessage.isNotEmpty()) {
            Text(state.saveMessage, color = MaterialTheme.colorScheme.secondary)
        }

        Button(onClick = { viewModel.onBackToMenu() }, modifier = Modifier.fillMaxWidth()) {
            Text("В главное меню")
        }
    }
}

@Composable
private fun SkillTreeSection(state: CampaignUiState, viewModel: CampaignViewModel) {
    Text("Древо навыков:", fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    SkillBranch.entries.forEach { branch ->
        val tier1 = state.skills.find { it.branch == branch && it.tier == 1 }
        val tier2 = state.skills.find { it.branch == branch && it.tier == 2 }
        if (tier1 != null && tier2 != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ветвь ${branch.letter}", modifier = Modifier.width(60.dp))
                SkillCheckbox(tier1.unlocked, "${branch.letter}1") {
                    viewModel.onUnlockSkill(branch, 1)
                }
                Spacer(modifier = Modifier.width(8.dp))
                SkillCheckbox(tier2.unlocked, "${branch.letter}2") {
                    viewModel.onUnlockSkill(branch, 2)
                }
            }
        }
    }
}

@Composable
private fun SkillCheckbox(unlocked: Boolean, label: String, onClick: () -> Unit) {
    val color = if (unlocked) Color(0xFF4CAF50) else Color(0xFFBDBDBD)
    Button(
        onClick = onClick,
        modifier = Modifier.padding(2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ResourcesSection(state: CampaignUiState, viewModel: CampaignViewModel) {
    Text("Ресурсы:", fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))

    Text("Материи:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Material.entries.forEach { mat ->
        val qty = state.materials[mat] ?: 0
        ResourceRow("${mat.displayName}: $qty") {
            viewModel.onOpenExchange(ResourceType.MATERIAL, mat.name)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Растения:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Plant.entries.forEach { plant ->
        val qty = state.plants[plant] ?: 0
        ResourceRow("${plant.displayName}: $qty") {
            viewModel.onOpenExchange(ResourceType.PLANT, plant.name)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Стихии:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Element.entries.forEach { elem ->
        val qty = state.elements[elem] ?: 0
        ResourceRow("${elem.displayName}: $qty") {
            viewModel.onOpenExchange(ResourceType.ELEMENT, elem.name)
        }
    }
}

@Composable
private fun ResourceRow(label: String, onExchange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Button(onClick = onExchange, modifier = Modifier.height(32.dp)) {
            Text("Обмен", fontSize = 12.sp)
        }
    }
}
