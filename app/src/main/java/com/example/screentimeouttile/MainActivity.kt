package com.example.screentimeouttile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.screentimeouttile.ui.theme.ScreenTimeoutTileTheme

class MainActivity : ComponentActivity() {

    private lateinit var manager: ScreenTimeoutManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Will refresh state on resume
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Will refresh state on resume
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ScreenTimeoutManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ScreenTimeoutTileTheme {
                var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
                
                LaunchedEffect(Unit) {
                    updateInfo = AppUpdater.checkForUpdate()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScreenTimeoutScreen(manager, ::requestWriteSettings)

                    updateInfo?.let { info ->
                        AlertDialog(
                            onDismissRequest = { updateInfo = null },
                            title = { Text("Update Available") },
                            text = { 
                                Column {
                                    Text("Version ${info.version} is available!")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Release Notes:", fontWeight = FontWeight.Bold)
                                    Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    AppUpdater.downloadAndInstallUpdate(this@MainActivity, info.downloadUrl)
                                    updateInfo = null
                                }) {
                                    Text("Update Now")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("Later")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        permissionLauncher.launch(intent)
    }
}

@Composable
fun ScreenTimeoutScreen(
    manager: ScreenTimeoutManager,
    onRequestPermission: () -> Unit
) {
    var hasPermission by remember { mutableStateOf(manager.hasWriteSettingsPermission()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoringBattery by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = manager.hasWriteSettingsPermission()
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // App title
        Text(
            text = "⏱",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Screen Timeout Tile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Quickly change your screen timeout\nfrom the Quick Settings panel.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Battery Optimization Card (Crucial for preventing the "stops working next day" bug)
        val batteryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) {
            isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        if (!isIgnoringBattery) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Battery Optimization Detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Android puts this app to sleep overnight because you only use it from the notification panel. This causes the tile to stop working the next day.\n\nPlease allow the app to run in the background to fix this permanently.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            batteryLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disable Optimization")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Permission card
        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app needs permission to modify system settings to change your screen timeout.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Allow Permission")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Permission granted. You're all set!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // How to use section
        Text(
            text = "How to Use",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1
        StepCard(
            number = "1",
            title = "Add the Tile",
            description = "Swipe down from the top of your screen to open Quick Settings. Tap the edit (pencil) icon, find \"Screen Timeout\" and drag it into your active tiles."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 2
        StepCard(
            number = "2",
            title = "Tap to Cycle",
            description = "Each tap on the tile cycles through temporary timeouts:\n\nYour timeout → 5 min → 10 min → 30 min → Your timeout\n\nYour original timeout is saved automatically and restored at the end."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 3
        StepCard(
            number = "3",
            title = "Notification",
            description = "A notification appears while a temporary timeout is active, showing the current timeout. Swipe it away to instantly restore your original timeout."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 4
        StepCard(
            number = "4",
            title = "That's it!",
            description = "No need to open this app again. Everything works from the Quick Settings tile and the notification. This app uses zero battery in the background."
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Debug Logs Section
        val logs by DebugLogger.logs.collectAsState()
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Debug Logs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black,
                    contentColor = androidx.compose.ui.graphics.Color.Green,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) {
                    val logText = if (logs.isEmpty()) "No logs yet." else logs.joinToString("\n")
                    Text(
                        text = logText,
                        modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logs.joinToString("\n")))
                        android.widget.Toast.makeText(context, "Logs copied!", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Copy Logs") }

                    OutlinedButton(onClick = { DebugLogger.clearLogs() }) { Text("Clear") }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun StepCard(number: String, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = number,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}