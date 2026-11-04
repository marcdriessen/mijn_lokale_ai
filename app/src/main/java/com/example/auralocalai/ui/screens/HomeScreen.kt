package com.example.auralocalai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auralocalai.R
import com.example.auralocalai.ui.LlmViewModel
import com.example.auralocalai.ui.ModelState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** One mini-app on the dashboard. */
data class MiniApp(
    val id: String,
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
    val tint: Color
)

/**
 * Dashboard: greeting, the shared model (all mini-apps use the same loaded model) and the
 * mini-apps as tiles.
 */
@Composable
fun HomeScreen(
    llmViewModel: LlmViewModel,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by llmViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val apps = remember {
        listOf(
            MiniApp("chat", R.string.nav_chat, R.string.home_chat_desc, Icons.AutoMirrored.Outlined.Chat, Color(0xFF2E5A44)),
            MiniApp("news", R.string.nav_news, R.string.home_news_desc, Icons.Outlined.Newspaper, Color(0xFF3B6EB5)),
            MiniApp("work", R.string.home_work, R.string.home_work_desc, Icons.Outlined.MedicalServices, Color(0xFFB5523B)),
            MiniApp("translate", R.string.home_translate, R.string.home_translate_desc, Icons.Outlined.Translate, Color(0xFF7A5BB5)),
            MiniApp("coach", R.string.home_coach, R.string.home_coach_desc, Icons.Outlined.RecordVoiceOver, Color(0xFFC08A1E)),
            MiniApp("models", R.string.nav_models, R.string.home_models_desc, Icons.Outlined.Dns, Color(0xFF5D6A62)),
            MiniApp("settings", R.string.nav_settings, R.string.home_settings_desc, Icons.Outlined.Settings, Color(0xFF5D6A62))
        )
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 6 -> stringResource(R.string.home_greeting_night)
        hour < 12 -> stringResource(R.string.home_greeting_morning)
        hour < 18 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_evening)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    SimpleDateFormat("EEEE d MMMM", locale).format(Date()).replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(greeting, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // Shared model card
        item(span = { GridItemSpan(2) }) {
            val loaded = state.modelState as? ModelState.Loaded
            val loading = state.modelState is ModelState.Loading
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onOpen("models") }
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    loaded != null -> Color(0xFF3DDC97)
                                    loading -> Color(0xFFEAB308)
                                    else -> Color(0xFF9AA0A6)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_model_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                        )
                        Text(
                            text = when {
                                loaded != null -> loaded.modelName
                                loading -> state.loadingStage ?: stringResource(R.string.loading_model)
                                else -> stringResource(R.string.home_no_model)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.background,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.home_model_shared),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                        )
                    }
                    if (loaded != null) {
                        TextButton(onClick = { llmViewModel.unloadModel() }) {
                            Text(stringResource(R.string.unload_model), color = MaterialTheme.colorScheme.background)
                        }
                    }
                }
            }
        }

        items(apps, key = { it.id }) { app ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onOpen(app.id) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(app.tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(app.icon, contentDescription = null, tint = app.tint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(stringResource(app.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(app.subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
