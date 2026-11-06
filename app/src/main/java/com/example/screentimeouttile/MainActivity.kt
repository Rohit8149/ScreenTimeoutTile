package com.example.screentimeouttile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.init(this)
        
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
                    ScreenTimeoutScreen()

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
}

private fun isTouchResetEnabled(context: Context): Boolean {
    if (ScreenInteractionAccessibilityService.isServiceRunning) return true
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName)
}

@Composable
fun ScreenTimeoutScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBattery by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) }
    var isTouchResetActive by remember { mutableStateOf(isTouchResetEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                isTouchResetActive = isTouchResetEnabled(context)
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                ScreenTimeoutService.notifyUserInteraction(isScroll = false)
            }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // App title
        Text(
            text = "☕",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Screen Keeper",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep your screen awake securely\nfrom the Quick Settings panel.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Touch Reset (Accessibility) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isTouchResetActive) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isTouchResetActive) "👆 Touch & Scroll Reset: Active" else "👆 Touch & Scroll Reset: Disabled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isTouchResetActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isTouchResetActive) {
                        "Touching or scrolling the screen (such as reading a PDF) will automatically reset the timeout countdown, just like standard Android display timeout."
                    } else {
                        "Enable this service so that touching or scrolling the screen (e.g. while reading a PDF) automatically resets the countdown timer back to full."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (isTouchResetActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                )
                if (!isTouchResetActive) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Enable Touch Reset")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Battery Optimization Card
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
                        text = "To ensure the Screen Keeper timer doesn't get randomly killed by the OS when you are reading something long, please allow background activity.",
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

        // How to use section
        Text(
            text = "How It Works",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1
        StepCard(
            number = "1",
            title = "Add the Tile",
            description = "Swipe down from the top of your screen to open Quick Settings. Tap the edit (pencil) icon, find \"Screen Timeout\" / \"Screen Keeper\" and drag it into your active tiles."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 2
        StepCard(
            number = "2",
            title = "Tap to Cycle (Exact Real-Time Math)",
            description = "Each tap cycles: Off → 5 min → 10 min → 30 min → Off.\n\nThe app calculates Target minus Native timeout so the total duration is mathematically exact, and your phone's native dimming reminder triggers before the screen turns off."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 3
        StepCard(
            number = "3",
            title = "Touch & Scroll Reset",
            description = "Whenever you scroll a PDF or touch the screen, the timer resets back to full. If the screen has begun dimming, touching it instantly un-dims the screen back to full brightness."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 4
        StepCard(
            number = "4",
            title = "Zero Battery Drain",
            description = "Pressing your phone's physical Power Button instantly destroys the Screen Keeper service to guarantee 0% background battery drain."
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
                        ScreenTimeoutService.notifyUserInteraction(isScroll = false)
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logs.joinToString("\n")))
                        android.widget.Toast.makeText(context, "Logs copied!", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Copy Logs") }

                    OutlinedButton(onClick = {
                        ScreenTimeoutService.notifyUserInteraction(isScroll = false)
                        DebugLogger.clearLogs()
                    }) { Text("Clear") }
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
