package com.example.auralocalai

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.screens.ChatScreen
import com.example.auralocalai.ui.screens.ModelManagerScreen
import com.example.auralocalai.ui.screens.SettingsScreen

@Composable
fun MainNavigation(llmViewModel: LlmViewModel, openBriefing: Boolean = false, briefingProfileId: String? = null) {
  val backStack = rememberNavBackStack(Main)
  androidx.compose.runtime.LaunchedEffect(openBriefing) {
    if (openBriefing && backStack.lastOrNull() != Briefing) backStack.add(Briefing)
  }
  // Shared LlmViewModel to persist state across screens
  

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          ChatScreen(
              viewModel = llmViewModel,
              onNavigateToModelManager = { backStack.add(ModelManager) },
              onNavigateToSettings = { backStack.add(Settings) },
              onNavigateToBriefing = { backStack.add(Briefing) },
              onNavigateToMedical = { backStack.add(Medical) },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<ModelManager> {
          ModelManagerScreen(
              viewModel = llmViewModel,
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<Briefing> {
          com.example.auralocalai.ui.screens.BriefingScreen(
              llmViewModel = llmViewModel,
              onBack = { backStack.removeLastOrNull() },
              onOpenChat = { while (backStack.size > 1) backStack.removeLastOrNull() },
              onOpenModels = { backStack.add(ModelManager) },
              modifier = Modifier.fillMaxSize(),
              initialProfileId = briefingProfileId
          )
        }
        entry<Medical> {
          com.example.auralocalai.ui.screens.MedicalScreen(
              llmViewModel = llmViewModel,
              onBack = { backStack.removeLastOrNull() },
              onOpenChat = { while (backStack.size > 1) backStack.removeLastOrNull() },
              onOpenModels = { backStack.add(ModelManager) },
              onOpenSettings = { backStack.add(Settings) },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<Settings> {
          SettingsScreen(
              viewModel = llmViewModel,
              onBack = { backStack.removeLastOrNull() },
              modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
