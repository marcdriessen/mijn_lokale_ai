package com.example.auralocalai

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.screens.BriefingScreen
import com.example.auralocalai.ui.screens.ChatScreen
import com.example.auralocalai.ui.screens.MedicalScreen
import com.example.auralocalai.ui.screens.ModelManagerScreen
import com.example.auralocalai.ui.screens.SettingsScreen

private data class Tab(val key: NavKey, val label: Int, val icon: ImageVector)

/**
 * App shell: the same bottom navigation on every screen (Chat, News, Q&A, Models, Settings).
 * Each tab is a top-level destination; Back returns to the chat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainNavigation(llmViewModel: LlmViewModel, openBriefing: Boolean = false, briefingProfileId: String? = null) {
  val backStack = rememberNavBackStack(Main)
  LaunchedEffect(openBriefing) {
    if (openBriefing && backStack.lastOrNull() != Briefing) backStack.add(Briefing)
  }

  val tabs = listOf(
    Tab(Main, R.string.nav_chat, Icons.AutoMirrored.Outlined.Chat),
    Tab(Briefing, R.string.nav_news, Icons.Outlined.Newspaper),
    Tab(Medical, R.string.nav_qa, Icons.Outlined.MedicalServices),
    Tab(ModelManager, R.string.nav_models, Icons.Outlined.Dns),
    Tab(Settings, R.string.nav_settings, Icons.Outlined.Settings)
  )

  fun open(key: NavKey) {
    if (backStack.lastOrNull() == key) return
    while (backStack.size > 1) backStack.removeLastOrNull()
    if (key != Main) backStack.add(key)
  }

  val current = backStack.lastOrNull() ?: Main
  val keyboardOpen = WindowInsets.isImeVisible

  Scaffold(
    contentWindowInsets = WindowInsets(0),
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      if (!keyboardOpen) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
          tabs.forEach { tab ->
            NavigationBarItem(
              selected = current == tab.key,
              onClick = { open(tab.key) },
              icon = { Icon(tab.icon, contentDescription = null) },
              label = { Text(stringResource(tab.label), maxLines = 1) },
              colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
              )
            )
          }
        }
      }
    }
  ) { innerPadding ->
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      modifier = Modifier
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding),
      entryProvider =
        entryProvider {
          entry<Main> {
            ChatScreen(
                viewModel = llmViewModel,
                onNavigateToModelManager = { open(ModelManager) },
                onNavigateToSettings = { open(Settings) },
                onNavigateToBriefing = { open(Briefing) },
                onNavigateToMedical = { open(Medical) },
                modifier = Modifier.fillMaxSize()
            )
          }
          entry<ModelManager> {
            ModelManagerScreen(
                viewModel = llmViewModel,
                onBack = { open(Main) },
                modifier = Modifier.fillMaxSize()
            )
          }
          entry<Briefing> {
            BriefingScreen(
                llmViewModel = llmViewModel,
                onBack = { open(Main) },
                onOpenChat = { open(Main) },
                onOpenModels = { open(ModelManager) },
                modifier = Modifier.fillMaxSize(),
                initialProfileId = briefingProfileId
            )
          }
          entry<Medical> {
            MedicalScreen(
                llmViewModel = llmViewModel,
                onBack = { open(Main) },
                onOpenChat = { open(Main) },
                onOpenModels = { open(ModelManager) },
                onOpenSettings = { open(Settings) },
                modifier = Modifier.fillMaxSize()
            )
          }
          entry<Settings> {
            SettingsScreen(
                viewModel = llmViewModel,
                onBack = { open(Main) },
                modifier = Modifier.fillMaxSize()
            )
          }
        },
    )
  }
}
