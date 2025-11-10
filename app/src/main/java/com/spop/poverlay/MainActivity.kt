package com.spop.poverlay

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.releases.ReleaseChecker
import com.spop.poverlay.ui.theme.PTONOverlayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ConfigurationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel =
            ConfigurationViewModel(
                application,
                ConfigurationRepository(applicationContext, this),
                ReleaseChecker(),
            )
        viewModel.finishActivity.observe(this) {
            finish()
        }
        viewModel.requestOverlayPermission.observe(this) {
            requestScreenPermission()
        }
        viewModel.requestBluetoothPermissions.observe(this) { permissions ->
            requestBluetoothPermissions(permissions)
        }
        viewModel.requestRestart.observe(this) {
            restartGrupetto()
        }
        viewModel.infoPopup.observe(this) {
            Toast
                .makeText(
                    this,
                    it,
                    Toast.LENGTH_LONG,
                ).show()
        }
        setContent {
            MainComponent(viewModel)
        }
        lifecycleScope.launchWhenResumed {
            viewModel.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    private fun restartGrupetto() {
        Toast
            .makeText(
                this@MainActivity,
                HtmlCompat.fromHtml("<big>Restarting Grupetto</big>", HtmlCompat.FROM_HTML_MODE_LEGACY),
                Toast.LENGTH_LONG,
            ).apply { setGravity(Gravity.CENTER, 0, 0) }
            .show()

        CoroutineScope(Dispatchers.IO).launch {
            delay(1500L)
            val pm: PackageManager = applicationContext.packageManager
            val intent = pm.getLaunchIntentForPackage(applicationContext.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
            applicationContext.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    private val overlayPermissionRequest =
        registerForActivityResult(StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= 23) {
                viewModel.onOverlayPermissionRequestCompleted(
                    Settings.canDrawOverlays(this),
                )
            }
        }

    private val bluetoothPermissionRequest =
        registerForActivityResult(RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            viewModel.onBluetoothPermissionsResult(allGranted)
        }

    private fun requestScreenPermission() =
        Intent(
            "android.settings.action.MANAGE_OVERLAY_PERMISSION",
            Uri.parse("package:$packageName"),
        ).apply {
            overlayPermissionRequest.launch(this)
        }

    private fun requestBluetoothPermissions(permissions: Array<String>) {
        bluetoothPermissionRequest.launch(permissions)
    }
}

enum class Destination(
    val icon: ImageVector,
    val iconDescription: String,
    val renderPage: @Composable (ConfigurationViewModel) -> Unit,
) {
    CONFIGURATION(
        icon = Icons.Filled.Home,
        iconDescription = "Configuration Page",
        renderPage = { viewModel ->
            ConfigurationPage(viewModel)
        },
    ),
    WORKOUT_PLANS(
        icon = Icons.Filled.List,
        iconDescription = "Workout Plans Page",
        renderPage = {},
    ),
}

@Composable
fun MainComponent(viewModel: ConfigurationViewModel) {
    val startDestination = Destination.CONFIGURATION
    var selectedDestination by rememberSaveable { mutableStateOf(startDestination.ordinal) }

    PTONOverlayTheme {
        Row {
            NavigationRail {
                Destination.entries.forEachIndexed { index, destination ->
                    NavigationRailItem(
                        selected = selectedDestination == index,
                        onClick = { selectedDestination = index },
                        icon = {
                            Icon(destination.icon, destination.iconDescription)
                        },
                    )
                }
            }
            Destination.entries[selectedDestination].renderPage(viewModel)
        }
    }
}