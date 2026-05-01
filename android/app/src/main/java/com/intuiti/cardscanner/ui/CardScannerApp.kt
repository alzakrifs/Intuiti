package com.intuiti.cardscanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.intuiti.cardscanner.R
import com.intuiti.cardscanner.data.ContactFields
import com.intuiti.cardscanner.data.ExtractionSource
import com.intuiti.cardscanner.util.ContactIntent
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CardScannerApp(viewModel: ScannerViewModel = viewModel(factory = ScannerViewModel.Factory)) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    ScannerScaffold(
        aiEnabled = apiKey.isNotBlank(),
        onOpenSettings = {
            viewModel.loadSettings()
            showSettings = true
        },
    ) { snackbarHost ->
        when (val current = phase) {
            ScanPhase.Idle -> CaptureScreen(
                aiEnabled = apiKey.isNotBlank(),
                onImageSelected = viewModel::onImageCaptured,
                snackbar = snackbarHost,
            )
            is ScanPhase.Working -> WorkingScreen(message = current.message)
            is ScanPhase.Review -> ReviewScreen(
                state = current,
                onUpdate = viewModel::updateField,
                onScanAnother = viewModel::reset,
            )
            is ScanPhase.Error -> ErrorScreen(message = current.message, onRetry = viewModel::reset)
        }
    }

    if (showSettings) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerScaffold(
    aiEnabled: Boolean,
    onOpenSettings: () -> Unit,
    content: @Composable (SnackbarHostState) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringRes(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (aiEnabled) stringRes(R.string.mode_ai)
                            else stringRes(R.string.mode_ocr),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (aiEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringRes(R.string.cd_more_options),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            content(snackbar)
        }
    }
}

// ----- Capture --------------------------------------------------------------

@Composable
private fun CaptureScreen(
    aiEnabled: Boolean,
    onImageSelected: (Uri) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) onImageSelected(uri)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onImageSelected(uri) }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = newCaptureUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.error_no_camera_permission)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringRes(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringRes(R.string.capture_take_photo),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringRes(R.string.capture_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val uri = newCaptureUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = "  " + stringRes(R.string.capture_take_photo),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(text = "  " + stringRes(R.string.capture_pick_from_gallery))
                }
            }
        }

        Text(
            text = if (aiEnabled) {
                "AI mode is on. Images are sent to the Anthropic API for extraction."
            } else {
                "Using on-device ML Kit OCR. No images leave your phone."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { if (!exists()) mkdirs() }
    val file = File(dir, "card-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

// ----- Working --------------------------------------------------------------

@Composable
private fun WorkingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f))
        }
    }
}

// ----- Review ---------------------------------------------------------------

@Composable
private fun ReviewScreen(
    state: ScanPhase.Review,
    onUpdate: ((ContactFields) -> ContactFields) -> Unit,
    onScanAnother: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = state.imageUri,
                    contentDescription = stringRes(R.string.cd_preview),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Text(
                text = stringRes(R.string.review_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = sourceLabel(state.source),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(R.string.review_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.source == ExtractionSource.MlKitFallback && !state.errorMessage.isNullOrBlank()) {
                FallbackErrorBanner(message = state.errorMessage)
            }

            ContactFieldsForm(state.fields, onUpdate)

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    runCatching {
                        context.startActivity(ContactIntent.build(state.fields))
                    }.onFailure {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.error_save_failed)) }
                    }
                },
                enabled = !state.fields.isEmpty,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringRes(R.string.action_save)) }

            OutlinedButton(
                onClick = onScanAnother,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringRes(R.string.action_scan_another)) }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingDp()))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ContactFieldsForm(
    fields: ContactFields,
    onUpdate: ((ContactFields) -> ContactFields) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fields.firstName,
                onValueChange = { v -> onUpdate { it.copy(firstName = v) } },
                label = { Text(stringRes(R.string.field_first_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = fields.lastName,
                onValueChange = { v -> onUpdate { it.copy(lastName = v) } },
                label = { Text(stringRes(R.string.field_last_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = fields.title,
            onValueChange = { v -> onUpdate { it.copy(title = v) } },
            label = { Text(stringRes(R.string.field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.org,
            onValueChange = { v -> onUpdate { it.copy(org = v) } },
            label = { Text(stringRes(R.string.field_org)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.phone,
            onValueChange = { v -> onUpdate { it.copy(phone = v) } },
            label = { Text(stringRes(R.string.field_phone)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.mobile,
            onValueChange = { v -> onUpdate { it.copy(mobile = v) } },
            label = { Text(stringRes(R.string.field_mobile)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.email,
            onValueChange = { v -> onUpdate { it.copy(email = v) } },
            label = { Text(stringRes(R.string.field_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.website,
            onValueChange = { v -> onUpdate { it.copy(website = v) } },
            label = { Text(stringRes(R.string.field_website)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.address,
            onValueChange = { v -> onUpdate { it.copy(address = v) } },
            label = { Text(stringRes(R.string.field_address)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }
}

@Composable
private fun sourceLabel(source: ExtractionSource): String = when (source) {
    ExtractionSource.Claude -> stringRes(R.string.status_done_ai)
    ExtractionSource.MlKit -> stringRes(R.string.status_done_ocr)
    ExtractionSource.MlKitFallback -> stringRes(R.string.status_done_fallback)
}

@Composable
private fun FallbackErrorBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "AI extraction failed",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ----- Error ----------------------------------------------------------------

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringRes(R.string.action_retake)) }
        }
    }
}

// ----- Settings sheet -------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(viewModel: ScannerViewModel, onDismiss: () -> Unit) {
    val state by viewModel.settingsState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.loadSettings() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.cd_close))
                }
            }

            Text(
                text = stringRes(R.string.settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onSettingsKeyChanged,
                label = { Text(stringRes(R.string.settings_key_label)) },
                placeholder = { Text(stringRes(R.string.settings_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringRes(R.string.settings_storage_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.saveApiKey(state.apiKey) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringRes(R.string.settings_save)) }
                OutlinedButton(
                    onClick = viewModel::clearApiKey,
                    modifier = Modifier.weight(1f),
                ) { Text(stringRes(R.string.settings_clear)) }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingDp()))
        }
    }
}

// ----- Helpers --------------------------------------------------------------

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun WindowInsets.asPaddingDp(): androidx.compose.ui.unit.Dp =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        getBottom(this).toDp()
    }
