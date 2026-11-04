package com.example.auralocalai

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.screens.BriefingScreen
import com.example.auralocalai.ui.screens.ChatScreen
import com.example.auralocalai.ui.screens.HomeScreen
import com.example.auralocalai.ui.screens.MedicalScreen
import com.example.auralocalai.ui.screens.ModelManagerScreen
import com.example.auralocalai.ui.screens.SettingsScreen
import com.example.auralocalai.ui.screens.TranslateScreen

/**
 * App shell: a dashboard (Home) from which the mini-apps are opened. All mini-apps share the
 * same LlmViewModel and therefore the same loaded model. Back always returns to the dashboard.
 */
@Composable
fun MainNavigation(
  llmViewModel: LlmViewModel,
  openBriefing: Boolean = false,
  briefingProfileId: String? = null,
  sharedText: String? = null,
  sharedImage: android.net.Uri? = null
) {
  val backStack = rememberNavBackStack(Home)
  var pendingText by remember { mutableStateOf(sharedText) }
  var pendingImage by remember { mutableStateOf(sharedImage) }

  fun open(key: NavKey) {
    if (backStack.lastOrNull() == key) return
    while (backStack.size > 1) backStack.removeLastOrNull()
    if (key != Home) backStack.add(key)
  }

  LaunchedEffect(openBriefing) {
    if (openBriefing) open(Briefing)
  }
  LaunchedEffect(sharedText, sharedImage) {
    if (!sharedText.isNullOrBlank() || sharedImage != null) {
      pendingText = sharedText
      pendingImage = sharedImage
      open(Translate)
    }
  }

  val home: () -> Unit = { open(Home) }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(
              llmViewModel = llmViewModel,
              onOpen = { id ->
                when (id) {
                  "chat" -> open(Main)
                  "news" -> open(Briefing)
                  "work" -> open(Medical)
                  "translate" -> open(Translate)
                  "models" -> open(ModelManager)
                  "settings" -> open(Settings)
                }
              },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<Main> {
          ChatScreen(
              viewModel = llmViewModel,
              onNavigateToModelManager = { open(ModelManager) },
              onNavigateToSettings = { open(Settings) },
              onNavigateToBriefing = { open(Briefing) },
              onNavigateToMedical = { open(Medical) },
              onHome = home,
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<ModelManager> {
          ModelManagerScreen(viewModel = llmViewModel, onBack = home, modifier = Modifier.fillMaxSize())
        }
        entry<Briefing> {
          BriefingScreen(
              llmViewModel = llmViewModel,
              onBack = home,
              onOpenChat = { open(Main) },
              onOpenModels = { open(ModelManager) },
              modifier = Modifier.fillMaxSize(),
              initialProfileId = briefingProfileId
          )
        }
        entry<Medical> {
          MedicalScreen(
              llmViewModel = llmViewModel,
              onBack = home,
              onOpenChat = { open(Main) },
              onOpenModels = { open(ModelManager) },
              onOpenSettings = { open(Settings) },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<Translate> {
          TranslateScreen(
              llmViewModel = llmViewModel,
              onBack = home,
              sharedText = pendingText,
              sharedImage = pendingImage,
              onSharedConsumed = {
                pendingText = null
                pendingImage = null
              },
              modifier = Modifier.fillMaxSize()
          )
        }
        entry<Settings> {
          SettingsScreen(viewModel = llmViewModel, onBack = home, modifier = Modifier.fillMaxSize())
        }
      },
  )
}
