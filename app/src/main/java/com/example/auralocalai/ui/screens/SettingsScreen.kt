package com.example.auralocalai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.TravelExplore
import com.example.auralocalai.data.WebSearchMode
import com.example.auralocalai.data.SearchOutcome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auralocalai.theme.ThemeMode
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.os.Build
import android.content.Context
import java.io.File
import com.example.auralocalai.ui.LlmViewModel
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Memory
import com.example.auralocalai.data.SocDetector
import com.example.auralocalai.data.SocInfo
import com.example.auralocalai.data.HtpVersion
import com.example.auralocalai.data.SocVendor
import androidx.compose.ui.res.stringResource
import com.example.auralocalai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LlmViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val diagnostics = remember { getDiagnosticInfo(context) }

    var tokenInput by remember(uiState.hfToken) { mutableStateOf(uiState.hfToken) }
    var isTokenVisible by remember { mutableStateOf(false) }
    
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var searchKeyInput by remember { mutableStateOf("") }
    var isSearchKeyVisible by remember { mutableStateOf(false) }
    var isTestingSearch by remember { mutableStateOf(false) }
    var testSearchResult by remember { mutableStateOf<SearchOutcome?>(null) }

    Scaffold(
        topBar = {
            com.example.auralocalai.ui.components.AppHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.app_name),
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy & security
            var lockEnabled by remember { mutableStateOf(com.example.auralocalai.AppSecurity.isLockEnabled(context)) }
            var privacyMode by remember { mutableStateOf(com.example.auralocalai.AppSecurity.isPrivacyMode(context)) }
            var anonymize by remember { mutableStateOf(com.example.auralocalai.AppSecurity.isAnonymizeEnabled(context)) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.security_lock), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.security_lock_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = lockEnabled, onCheckedChange = {
                            lockEnabled = it
                            com.example.auralocalai.AppSecurity.setLockEnabled(context, it)
                        })
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.security_privacy), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.security_privacy_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = privacyMode, onCheckedChange = {
                            privacyMode = it
                            com.example.auralocalai.AppSecurity.setPrivacyMode(context, it)
                        })
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.security_anonymize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.security_anonymize_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = anonymize, onCheckedChange = {
                            anonymize = it
                            com.example.auralocalai.AppSecurity.setAnonymizeEnabled(context, it)
                        })
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.ocr_script_settings), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ocr_script_settings_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.example.auralocalai.ui.components.ScriptSelector()
                }
            }

            // Back-up & restore (no models)
            val backupScope = rememberCoroutineScope()
            val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri != null) backupScope.launch {
                    val message = try {
                        val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.auralocalai.data.backup.BackupManager.export(context, uri) }
                        context.getString(R.string.backup_done, n)
                    } catch (e: Exception) {
                        context.getString(R.string.backup_failed, e.message ?: "")
                    }
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) backupScope.launch {
                    val message = try {
                        val n = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.example.auralocalai.data.backup.BackupManager.restore(context, uri) }
                        context.getString(R.string.restore_done, n)
                    } catch (e: Exception) {
                        context.getString(R.string.backup_failed, e.message ?: "")
                    }
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.backup_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("local-llm-ai-backup.zip") }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_export))
                        }
                        OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_restore))
                        }
                    }
                }
            }

            // Title Card with Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = stringResource(R.string.token_settings),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.hf_auth_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.hf_auth_body),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // Main Token Settings Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.hf_token_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { 
                            tokenInput = it.trim()
                            validationResult = null
                        },
                        label = { Text(stringResource(R.string.hf_token_label)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isTokenVisible) stringResource(R.string.hide_token) else stringResource(R.string.show_token)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Token Status Hint Block
                    val isConfigured = uiState.hfToken.isNotBlank()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isConfigured) MaterialTheme.colorScheme.tertiaryContainer 
                                            else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isConfigured) stringResource(R.string.configured) else stringResource(R.string.not_configured),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConfigured) MaterialTheme.colorScheme.onTertiaryContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Validate button
                        Button(
                            onClick = {
                                if (tokenInput.isNotBlank()) {
                                    isValidating = true
                                    validationResult = null
                                    viewModel.validateHfToken(tokenInput) { isSuccess, msg ->
                                        isValidating = false
                                        validationResult = Pair(isSuccess, msg)
                                    }
                                }
                            },
                            enabled = tokenInput.isNotBlank() && !isValidating,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                text = if (isValidating) stringResource(R.string.checking) else stringResource(R.string.validate_access),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Save button
                        Button(
                            onClick = {
                                viewModel.saveHfToken(tokenInput)
                                validationResult = null
                            },
                            enabled = tokenInput != uiState.hfToken && !isValidating,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.save_token),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Clear button
                    if (isConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.clearHfToken()
                                tokenInput = ""
                                validationResult = null
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.remove_token), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Validation status output
                    validationResult?.let { (isSuccess, message) ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSuccess) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSuccess) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = if (isSuccess) stringResource(R.string.success) else stringResource(R.string.error),
                                    tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSuccess) MaterialTheme.colorScheme.onTertiaryContainer
                                            else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Web Search Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.web_search_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Status chip
                        val isKeyedProvider = uiState.webSearchProviderId != "free"
                        val (statusText, statusBg, statusFg) = when {
                            uiState.webSearchMode == WebSearchMode.OFF -> Triple(stringResource(R.string.status_disabled), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                            isKeyedProvider && !uiState.searchApiKeyConfigured -> Triple(stringResource(R.string.status_needs_key), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            else -> Triple(stringResource(R.string.status_active_mode, uiState.webSearchMode.name), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusBg,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.web_search_body),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.web_search_privacy),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Search Mode Section
                    Text(
                        text = stringResource(R.string.trigger_mode),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val searchModes = listOf(
                        WebSearchMode.OFF to (stringResource(R.string.mode_off) to stringResource(R.string.mode_off_desc)),
                        WebSearchMode.AUTO to (stringResource(R.string.mode_auto) to stringResource(R.string.mode_auto_desc)),
                        WebSearchMode.ALWAYS to (stringResource(R.string.mode_always) to stringResource(R.string.mode_always_desc))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        searchModes.forEach { (mode, pair) ->
                            val (title, description) = pair
                            val isSelected = uiState.webSearchMode == mode
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.setWebSearchMode(mode)
                                        if (mode != WebSearchMode.OFF && !uiState.searchDisclosureAcknowledged) {
                                            viewModel.acknowledgeSearchDisclosure()
                                        }
                                    },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setWebSearchMode(mode)
                                            if (mode != WebSearchMode.OFF && !uiState.searchDisclosureAcknowledged) {
                                                viewModel.acknowledgeSearchDisclosure()
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Search Provider Section
                    Text(
                        text = stringResource(R.string.search_provider),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val providers = listOf(
                        Triple("free", stringResource(R.string.provider_free), stringResource(R.string.provider_free_desc)),
                        Triple("brave", stringResource(R.string.provider_brave), stringResource(R.string.provider_brave_desc)),
                        Triple("serper", stringResource(R.string.provider_serper), stringResource(R.string.provider_serper_desc)),
                        Triple("gemini", stringResource(R.string.provider_gemini), stringResource(R.string.provider_gemini_desc)),
                        Triple("openai", stringResource(R.string.provider_openai), stringResource(R.string.provider_openai_desc))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        providers.forEach { (id, name, desc) ->
                            val isSelected = uiState.webSearchProviderId == id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.setWebSearchProvider(id) },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setWebSearchProvider(id) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // AI search: where to get a key, and which model to use.
                    if (uiState.webSearchProviderId == "gemini" || uiState.webSearchProviderId == "openai") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stringResource(if (uiState.webSearchProviderId == "gemini") R.string.provider_gemini_help else R.string.provider_openai_help),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var modelInput by remember(uiState.webSearchProviderId) {
                            mutableStateOf(uiState.aiSearchModels[uiState.webSearchProviderId].orEmpty())
                        }
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = {
                                modelInput = it
                                viewModel.setAiSearchModel(uiState.webSearchProviderId, it)
                            },
                            label = { Text(stringResource(R.string.provider_ai_model)) },
                            placeholder = {
                                Text(
                                    if (uiState.webSearchProviderId == "gemini") com.example.auralocalai.data.providers.GeminiSearchProvider.DEFAULT_MODEL
                                    else com.example.auralocalai.data.providers.OpenAiSearchProvider.DEFAULT_MODEL
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }

                    // 3. API Key Input (if Brave or Serper selected)
                    if (uiState.webSearchProviderId != "free") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(
                                R.string.provider_api_key,
                                when (uiState.webSearchProviderId) {
                                    "brave" -> "Brave Search"
                                    "gemini" -> "Gemini (Google AI Studio)"
                                    "openai" -> "OpenAI"
                                    else -> "Serper"
                                }
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = searchKeyInput,
                            onValueChange = { searchKeyInput = it.trim() },
                            label = { Text(stringResource(R.string.enter_api_key)) },
                            placeholder = { Text(if (uiState.searchApiKeyConfigured) "••••••••••••••••" else stringResource(R.string.paste_api_key)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (isSearchKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isSearchKeyVisible = !isSearchKeyVisible }) {
                                    Icon(
                                        imageVector = if (isSearchKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isSearchKeyVisible) stringResource(R.string.hide_key) else stringResource(R.string.show_key)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (searchKeyInput.isNotBlank()) {
                                        viewModel.saveSearchApiKey(searchKeyInput)
                                        searchKeyInput = ""
                                    }
                                },
                                enabled = searchKeyInput.isNotBlank(),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.save_key), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (uiState.searchApiKeyConfigured) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearSearchApiKey()
                                        searchKeyInput = ""
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.remove_key), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Max Sources Slider
                    Text(
                        text = stringResource(R.string.max_results, uiState.webSearchMaxResults),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = uiState.webSearchMaxResults.toFloat(),
                        onValueChange = { viewModel.setWebSearchMaxResults(it.toInt()) },
                        valueRange = 3f..8f,
                        steps = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.max_results_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Test Search Connection Button
                    Button(
                        onClick = {
                            isTestingSearch = true
                            testSearchResult = null
                            scope.launch {
                                try {
                                    val outcome = viewModel.testSearch("local AI news")
                                    testSearchResult = outcome
                                } catch (e: Exception) {
                                    testSearchResult = SearchOutcome(
                                        query = "local AI news",
                                        sources = emptyList(),
                                        providerId = uiState.webSearchProviderId,
                                        isError = true,
                                        errorMessage = e.message
                                    )
                                } finally {
                                    isTestingSearch = false
                                }
                            }
                        },
                        enabled = !isTestingSearch,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isTestingSearch) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.testing_search), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text(stringResource(R.string.test_search), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Test Result Display
                    testSearchResult?.let { result ->
                        Spacer(modifier = Modifier.height(10.dp))
                        val isSuccess = !result.isError && result.sources.isNotEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSuccess) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSuccess) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isSuccess) stringResource(R.string.search_verified, result.sources.size, result.providerId)
                                               else stringResource(R.string.search_failed, result.errorMessage ?: stringResource(R.string.no_sources)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSuccess) MaterialTheme.colorScheme.onTertiaryContainer
                                                else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (isSuccess && result.sources.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.search_sample, result.sources.first().title, result.sources.first().domain),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Appearance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.appearance),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.select_theme),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val themeOptions = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                        ThemeMode.AMOLED to stringResource(R.string.theme_amoled)
                    )
                    
                    Column {
                        themeOptions.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.select_language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val languageOptions = listOf(
                        com.example.auralocalai.AppLocale.SYSTEM to stringResource(R.string.language_system),
                        "en" to "English",
                        "nl" to "Nederlands",
                        "bg" to "Български"
                    )

                    Column {
                        languageOptions.forEach { (tag, label) ->
                            val selectLanguage = {
                                if (uiState.appLanguage != tag) {
                                    viewModel.setAppLanguage(tag)
                                    com.example.auralocalai.AppLocale.findActivity(context)?.recreate()
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectLanguage() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.appLanguage == tag,
                                    onClick = { selectLanguage() },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Hardware Acceleration Backend Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backend_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.backend_body),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val backendOptions = listOf(
                        "AUTO" to stringResource(R.string.backend_auto),
                        "GPU_ONLY" to stringResource(R.string.backend_gpu),
                        "NPU_ONLY" to stringResource(R.string.backend_npu),
                        "CPU_ONLY" to stringResource(R.string.backend_cpu)
                    )

                    Column {
                        backendOptions.forEach { (backendKey, label) ->
                            val isNpuOption = backendKey == "NPU_ONLY"
                            val isOptionAvailable = !isNpuOption || diagnostics.socInfo.isNpuHardwarePresent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isOptionAvailable) {
                                        viewModel.setPreferredBackend(backendKey)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.preferredBackend == backendKey,
                                    onClick = { viewModel.setPreferredBackend(backendKey) },
                                    enabled = isOptionAvailable,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        color = if (isOptionAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                        fontWeight = if (uiState.preferredBackend == backendKey) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isNpuOption && !diagnostics.socInfo.isNpuHardwarePresent) {
                                        Text(
                                            text = stringResource(R.string.npu_not_detected),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // System Diagnostics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.diag_soc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = diagnostics.socInfo.marketingName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.diag_npu),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (diagnostics.socInfo.isNpuHardwarePresent) {
                                "${diagnostics.socInfo.htpVersion.label}"
                            } else {
                                stringResource(R.string.not_detected, diagnostics.socInfo.vendor.name)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (diagnostics.socInfo.isNpuHardwarePresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.diag_npu_runtime),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = diagnostics.socInfo.qnnDetails,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (diagnostics.socInfo.isQnnRuntimeAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.diag_vulkan),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (diagnostics.vulkanSupported) stringResource(R.string.supported_version, diagnostics.vulkanVersion) else stringResource(R.string.unsupported),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (diagnostics.vulkanSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.diag_ram),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f GB", diagnostics.totalRamGb),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = stringResource(R.string.diag_arch),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = diagnostics.abis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }

            // Chat Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.chat_config),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Context Window turns slider
                    Text(
                        text = stringResource(R.string.context_window, uiState.contextWindowSize),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.context_window_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = uiState.contextWindowSize.toFloat(),
                        onValueChange = { viewModel.saveContextWindowSize(it.toInt()) },
                        valueRange = 4f..12f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-unload after inactivity (frees RAM)
                    Text(
                        text = stringResource(R.string.auto_unload_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.auto_unload_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 15, 30).forEach { minutes ->
                            FilterChip(
                                selected = uiState.autoUnloadMinutes == minutes,
                                onClick = { viewModel.setAutoUnloadMinutes(minutes) },
                                label = {
                                    Text(
                                        text = if (minutes == 0) stringResource(R.string.auto_unload_off)
                                               else stringResource(R.string.minutes_short, minutes),
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Automatic model choice per task
                    ModelRoutingSection(uiState, viewModel)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Custom System Prompt
                    Text(
                        text = stringResource(R.string.system_instructions),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.system_instructions_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var promptInput by remember(uiState.systemPrompt) { mutableStateOf(uiState.systemPrompt) }

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text(stringResource(R.string.enter_instructions)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveSystemPrompt(promptInput) },
                        enabled = promptInput != uiState.systemPrompt,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.align(Alignment.End).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.apply_instructions), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. Model Inference & Sampling Parameters
            OutlinedCard(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = stringResource(R.string.parameters),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.params_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.params_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 15.sp
                    )

                    // Simple choice: most people only need one of these three styles.
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.style_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    val styles = listOf(
                        AnswerStyle(R.string.style_precise, R.string.style_precise_desc, 0.2f, 0.8f, 20, 1.1f),
                        AnswerStyle(R.string.style_balanced, R.string.style_balanced_desc, 0.6f, 0.9f, 40, 1.1f),
                        AnswerStyle(R.string.style_creative, R.string.style_creative_desc, 0.9f, 0.95f, 64, 1.05f)
                    )
                    val currentStyle = styles.firstOrNull {
                        kotlin.math.abs(it.temp - uiState.temperature) < 0.01f && kotlin.math.abs(it.topP - uiState.topP) < 0.01f &&
                            it.topK == uiState.topK && kotlin.math.abs(it.rep - uiState.repetitionPenalty) < 0.01f
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styles.forEach { s ->
                            FilterChip(
                                selected = currentStyle == s,
                                onClick = { viewModel.updateSamplerSettings(temperature = s.temp, topP = s.topP, topK = s.topK, repetitionPenalty = s.rep) },
                                label = { Text(stringResource(s.label), fontSize = 12.sp) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(currentStyle?.desc ?: R.string.style_custom_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.style_advanced), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Temperature Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.temperature),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", uiState.temperature),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.temperature_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Slider(
                        value = uiState.temperature,
                        onValueChange = { viewModel.updateSamplerSettings(temperature = it) },
                        valueRange = 0.0f..1.5f,
                        steps = 29
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Top-P Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.top_p),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", uiState.topP),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.top_p_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                    Slider(
                        value = uiState.topP,
                        onValueChange = { viewModel.updateSamplerSettings(topP = it) },
                        valueRange = 0.1f..1.0f,
                        steps = 17
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Top-K Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Top-K",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.topK}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.top_k_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                    Slider(
                        value = uiState.topK.toFloat(),
                        onValueChange = { viewModel.updateSamplerSettings(topK = it.toInt()) },
                        valueRange = 1f..100f,
                        steps = 98
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Repetition Penalty Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.repetition_penalty),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", uiState.repetitionPenalty),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.repetition_penalty_desc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Slider(
                        value = uiState.repetitionPenalty,
                        onValueChange = { viewModel.updateSamplerSettings(repetitionPenalty = it) },
                        valueRange = 1.0f..1.5f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Thinking / Reasoning Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.thinking_mode),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.thinking_mode_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.enableThinking,
                            onCheckedChange = { viewModel.updateSamplerSettings(enableThinking = it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.updateSamplerSettings(
                                temperature = 0.7f,
                                topP = 0.9f,
                                topK = 40,
                                repetitionPenalty = 1.0f,
                                enableThinking = true,
                                thinkingBudget = -1
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.End).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.reset_defaults), fontSize = 11.sp)
                    }
                }
            }

            // Footer / How to link
            Text(
                text = stringResource(R.string.hf_token_link_intro),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "huggingface.co/settings/tokens",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

data class DiagnosticInfo(
    val vulkanSupported: Boolean,
    val vulkanVersion: String,
    val totalRamGb: Double,
    val socInfo: SocInfo,
    val abis: String
)

fun getDiagnosticInfo(context: Context): DiagnosticInfo {
    val pm = context.packageManager
    var hasVulkan = false
    var vulkanVer = "Unsupported"
    try {
        val features = pm.systemAvailableFeatures
        for (feature in features) {
            if (feature.name == "android.hardware.vulkan.version") {
                hasVulkan = true
                val version = feature.version
                val major = version shr 22 and 0x3FF
                val minor = version shr 12 and 0x3FF
                val patch = version and 0xFFF
                vulkanVer = "$major.$minor.$patch"
                break
            }
        }
    } catch (e: Exception) {
        // Safe catch
    }

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    var totalRam = 0.0
    if (activityManager != null) {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        totalRam = memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
    }

    val abis = Build.SUPPORTED_ABIS.joinToString(", ")
    val socInfo = SocDetector.detectSoc(context)

    return DiagnosticInfo(hasVulkan, vulkanVer, totalRam, socInfo, abis)
}

/** Preset of sampler settings shown as a simple choice in Settings. */
private data class AnswerStyle(val label: Int, val desc: Int, val temp: Float, val topP: Float, val topK: Int, val rep: Float)
