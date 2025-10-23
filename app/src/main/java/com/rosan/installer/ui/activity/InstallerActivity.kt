package com.rosan.installer.ui.activity

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rosan.installer.R
import com.rosan.installer.build.Level
import com.rosan.installer.build.RsConfig
import com.rosan.installer.data.installer.model.entity.ProgressEntity
import com.rosan.installer.data.installer.repo.InstallerRepo
import com.rosan.installer.data.reflect.repo.ReflectRepo
import com.rosan.installer.data.settings.model.datastore.AppDataStore
import com.rosan.installer.ui.page.main.installer.InstallerPage
import com.rosan.installer.ui.theme.InstallerMaterialExpressiveTheme
import com.rosan.installer.ui.util.PermissionDenialReason
import com.rosan.installer.ui.util.PermissionManager
import com.rosan.installer.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class InstallerActivity : ComponentActivity(), KoinComponent {
    companion object {
        const val KEY_ID = "installer_id"
        private const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
    }

    private val appDataStore: AppDataStore by inject()
    private val reflect: ReflectRepo by inject()

    private var installer by mutableStateOf<InstallerRepo?>(null)
    private var job: Job? = null

    private data class ConfirmationState(val sessionId: Int, val sessionInfo: PackageInstaller.SessionInfo)

    private var confirmationState by mutableStateOf<ConfirmationState?>(null)

    private lateinit var permissionManager: PermissionManager

    private enum class InstallerTheme {
        MATERIAL,
        MIUIX
    }

    // + Define a data class for the UI state
    private data class InstallerUiState(
        val theme: InstallerTheme = InstallerTheme.MATERIAL, // Default theme
        val isThemeLoaded: Boolean = false // Flag to check if loading is complete
    )

    private var uiState by mutableStateOf(InstallerUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        if (RsConfig.isDebug && RsConfig.LEVEL == Level.UNSTABLE)
            logIntentDetails("onNewIntent", intent)
        enableEdgeToEdge()
        // Compat Navigation Bar color for Xiaomi Devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        Timber.d("onCreate. SavedInstanceState is ${if (savedInstanceState == null) "null" else "not null"}")

        lifecycleScope.launch {
            val useMiuix = appDataStore.getBoolean(AppDataStore.UI_USE_MIUIX, false).first()
            uiState = uiState.copy(
                theme = if (useMiuix) InstallerTheme.MIUIX else InstallerTheme.MATERIAL,
                isThemeLoaded = true
            )
        }

        permissionManager = PermissionManager(this)
        if (intent.action == ACTION_CONFIRM_INSTALL) {
            // Flow B: 我们被系统调用以确认一个已有的会话
            Timber.d("onCreate: Handling CONFIRM_INSTALL flow.")
            handleConfirmInstall(intent)
        } else {
            Timber.d("onCreate: Handling CLIENT (ACTION_VIEW/SEND) flow.")
            restoreInstaller(savedInstanceState)
            val installerId =
                if (savedInstanceState == null) intent?.getStringExtra(KEY_ID) else savedInstanceState.getString(KEY_ID)

            if (installerId == null) {
                Timber.d("onCreate: This is a fresh launch for a new task. Starting permission and resolve process.")
                // Only start the process for a completely new task.
                checkPermissionsAndStartProcess()
            } else {
                Timber.d("onCreate: Re-attaching to existing installer ($installerId). Skipping resolve process.")
            }
        }
        showContent()
    }

    private fun checkPermissionsAndStartProcess() {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            Timber.d("checkPermissionsAndStartProcess: Launched from history, skipping permission checks.")
            return
        }
        Timber.d("checkPermissionsAndStartProcess: Starting permission check flow.")

        // Call the manager to request permissions and handle the results in the callbacks.
        permissionManager.requestEssentialPermissions(
            onGranted = {
                // This is called when all permissions are successfully granted.
                Timber.d("All essential permissions are granted.")
                installer?.resolveInstall(this)
            },
            onDenied = { reason ->
                // This is called if any permission is denied.
                // The 'reason' enum tells you which one failed.
                when (reason) {
                    PermissionDenialReason.NOTIFICATION -> {
                        Timber.w("Notification permission was denied.")
                        this.toast(R.string.enable_notification_hint)
                    }

                    PermissionDenialReason.STORAGE -> {
                        Timber.w("Storage permission was denied.")
                        this.toast(R.string.enable_storage_permission_hint)
                    }
                }
                // Finish the activity if permissions are not granted.
                finish()
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val currentId = installer?.id
        outState.putString(KEY_ID, currentId)
        Timber.d("onSaveInstanceState: Saving id: $currentId")
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        Timber.d("onNewIntent: Received new intent.")
        if (RsConfig.isDebug && RsConfig.LEVEL == Level.UNSTABLE)
            logIntentDetails("onNewIntent", intent)
        if (intent.action == ACTION_CONFIRM_INSTALL) {
            // Flow B: 确认流程
            Timber.d("onNewIntent: Handling CONFIRM_INSTALL flow.")
            handleConfirmInstall(intent)
        } else {// Fix for Microsoft Edge
            if (this.installer != null) {
                Timber.w("onNewIntent was called, but an installer instance already exists. Ignoring re-initialization.")
                super.onNewIntent(intent) // Call super, but do not proceed further.
                return
            }
            if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this.intent = intent
            super.onNewIntent(intent)
            restoreInstaller()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        Timber.d("onDestroy: Activity is being destroyed. Job cancelled.")
        super.onDestroy()
    }

    private fun handleConfirmInstall(intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId == -1) {
            Timber.e("CONFIRM_INSTALL intent missing EXTRA_SESSION_ID")
            finish()
            return
        }
        Timber.d("handleConfirmInstall: sessionId=$sessionId")

        job?.cancel()
        job = null
        installer = null

        try {
            val packageInstaller = packageManager.packageInstaller
            val sessionInfo = packageInstaller.getSessionInfo(sessionId)
            if (sessionInfo == null) {
                Timber.e("Could not get SessionInfo for id $sessionId")
                finish()
                return
            }
            confirmationState = ConfirmationState(sessionId, sessionInfo)
            Timber.d("handleConfirmInstall: State set for ConfirmationFlow")

        } catch (e: Exception) {
            Timber.e(e, "Failed to handle CONFIRM_INSTALL")
            finish()
        }
    }

    private fun restoreInstaller(savedInstanceState: Bundle? = null) {
        confirmationState = null

        val installerId =
            if (savedInstanceState == null) intent?.getStringExtra(KEY_ID) else savedInstanceState.getString(KEY_ID)
        Timber.d("restoreInstaller: Attempting to restore with id: $installerId")

        if (this.installer != null && this.installer?.id == installerId) {
            Timber.d("restoreInstaller: Current installer already matches id $installerId. Skipping.")
            return
        }

        job?.cancel()
        Timber.d("restoreInstaller: Old job cancelled. Getting new installer instance.")

        val installer: InstallerRepo = get { parametersOf(installerId) }
        installer.background(false)
        this.installer = installer
        Timber.d("restoreInstaller: New installer instance [id=${installer.id}] set. Starting collectors.")

        val scope = CoroutineScope(Dispatchers.Main.immediate)
        job = scope.launch {
            launch {
                installer.progress.collect { progress ->
                    Timber.d("[id=${installer.id}] Activity collected progress: ${progress::class.simpleName}")
                    if (progress is ProgressEntity.Finish) {
                        Timber.d("[id=${installer.id}] Finish progress detected, finishing activity.")
                        if (!this@InstallerActivity.isFinishing) this@InstallerActivity.finish()
                    }
                }
            }
            launch {
                installer.background.collect { isBackground ->
                    Timber.d("[id=${installer.id}] Activity collected background: $isBackground")
                    if (isBackground) {
                        Timber.d("[id=${installer.id}] Background mode detected, finishing activity.")
                        this@InstallerActivity.finish()
                    }
                }
            }
        }
    }

    private fun showContent() {
        setContent {
            if (!uiState.isThemeLoaded) return@setContent

            val installer = installer


            /*if (background || progress is ProgressEntity.Ready || progress is ProgressEntity.InstallResolving || progress is ProgressEntity.Finish)
            // Return@setContent to show nothing, logs will explain why.
                return@setContent*/

            val confirmationState = confirmationState

            if (installer == null && confirmationState == null)
                return@setContent

            InstallerMaterialExpressiveTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (confirmationState != null)
                        ShowConfirmationDialog(
                            sessionInfo = confirmationState.sessionInfo,
                            onInstall = {
                                Timber.d("CONFIRM: Install clicked for session ${confirmationState.sessionId}")
                                approveSession(confirmationState.sessionId, true)
                            },
                            onCancel = {
                                Timber.d("CONFIRM: Cancel clicked for session ${confirmationState.sessionId}")
                                approveSession(confirmationState.sessionId, false)
                            }
                        )
                    else if (installer != null) {
                        val background by installer.background.collectAsState(false)
                        val progress by installer.progress.collectAsState(ProgressEntity.Ready)
                        InstallerPage(installer)
                    }
                }
            }
        }
        /*when (uiState.theme) {
            InstallerTheme.MATERIAL -> {
                InstallerMaterialExpressiveTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        InstallerPage(installer)
                    }
                }
            }

            InstallerTheme.MIUIX -> {
                InstallerMiuixTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MiuixInstallerPage(installer)
                    }
                }
            }
        }*/
    }

    private fun approveSession(sessionId: Int, granted: Boolean) {
        try {
            val packageInstaller = packageManager.packageInstaller

            val method = reflect.getMethod(
                packageInstaller::class.java,
                "setPermissionsResult",
                Int::class.java,
                Boolean::class.java
            ) //

            if (method != null) {
                method.invoke(packageInstaller, sessionId, granted)
                Timber.d("approveSession: Invoked hidden setPermissionsResult($sessionId, $granted) successfully.")
            } else {
                throw NoSuchMethodException("setPermissionsResult not found via ReflectRepo")
            }

        } catch (e: Exception) {
            Timber.e(e, "approveSession: Failed to invoke hidden setPermissionsResult via reflection.")
            if (!granted) {
                try {
                    packageManager.packageInstaller.abandonSession(sessionId)
                } catch (e2: Exception) {
                    Timber.e(e2, "approveSession: Fallback abandonSession also failed.")
                }
            }
        } finally {
            finish()
        }
    }

    private fun logIntentDetails(tag: String, intent: Intent?) {
        if (intent == null) {
            Timber.tag(tag).d("Intent is null")
            return
        }
        val flags = intent.flags
        val hexFlags = String.format("0x%08X", flags)

        Timber.tag(tag).d("---------- Intent Details Start ----------")
        Timber.tag(tag).d("Full Intent: $intent")
        Timber.tag(tag).d("Action: ${intent.action}")
        Timber.tag(tag).d("Data: ${intent.dataString}")
        Timber.tag(tag).d("Type: ${intent.type}")
        Timber.tag(tag).d("Categories: ${intent.categories?.joinToString(", ")}")
        Timber.tag(tag).d("Flags (Decimal): $flags")
        Timber.tag(tag).d("Flags (Hex): $hexFlags") // Flags 是关键！
        Timber.tag(tag).d("Component: ${intent.component}")
        Timber.tag(tag).d("Extras: ${intent.extras?.keySet()?.joinToString(", ")}")
        Timber.tag(tag).d("---------- Intent Details End ----------")
    }
}

@Composable
private fun ShowConfirmationDialog(
    sessionInfo: PackageInstaller.SessionInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val tag = "ShowConfirmDump"
    Timber.tag(tag).d("--- Dumping SessionInfo (ID: ${sessionInfo.sessionId}) ---")
    Timber.tag(tag).d("AppLabel: ${sessionInfo.appLabel}")
    Timber.tag(tag).d("AppPackageName: ${sessionInfo.appPackageName}")
    Timber.tag(tag).d("AppIcon available: ${sessionInfo.appIcon != null}")
    Timber.tag(tag).d("CreatedMillis: ${sessionInfo.createdMillis}")
    Timber.tag(tag).d("InstallerPackageName: ${sessionInfo.installerPackageName}")
    Timber.tag(tag).d("InstallerAttributionTag: ${sessionInfo.installerAttributionTag}")
    Timber.tag(tag).d("OriginatingUid: ${sessionInfo.originatingUid}") //
    Timber.tag(tag).d("IsActive: ${sessionInfo.isActive}")
    Timber.tag(tag).d("IsSealed: ${sessionInfo.isSealed}") //

    // 记录 Android S (API 31)及更高版本中可用的字段
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Timber.tag(tag).d("InstallReason: ${sessionInfo.installReason}")
    }
    Timber.tag(tag).d("--- End of SessionInfo Dump ---")
    val appLabel = sessionInfo.appLabel ?: "N/A"

    val appIconBitmap = sessionInfo.appIcon?.asImageBitmap()

    AlertDialog(
        onDismissRequest = onCancel, // 用户点击对话框外部或按返回键
        confirmButton = {
            Button(onClick = onInstall) {
                Text(stringResource(R.string.install))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        },
        icon = {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(40.dp) // Material Dialog 的标准图标大小
                )
            }
        },
        title = {
            Text(text = appLabel.toString())
        },
        text = {
            Text(text = stringResource(R.string.confirm))
        }
    )
}