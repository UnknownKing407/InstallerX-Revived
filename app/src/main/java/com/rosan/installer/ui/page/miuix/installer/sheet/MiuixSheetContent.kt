package com.rosan.installer.ui.page.miuix.installer.sheet

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.rosan.installer.R
import com.rosan.installer.data.app.model.entity.AppEntity
import com.rosan.installer.data.app.model.entity.DataType
import com.rosan.installer.data.app.model.entity.InstalledAppInfo
import com.rosan.installer.data.app.model.entity.PackageAnalysisResult
import com.rosan.installer.data.app.model.entity.SignatureMatchStatus
import com.rosan.installer.data.app.util.sortedBest
import com.rosan.installer.data.installer.model.entity.SelectInstallEntity
import com.rosan.installer.data.installer.repo.InstallerRepo
import com.rosan.installer.data.recycle.util.openAppPrivileged
import com.rosan.installer.ui.icons.AppIcons
import com.rosan.installer.ui.page.main.installer.dialog.InstallerViewAction
import com.rosan.installer.ui.page.main.installer.dialog.InstallerViewModel
import com.rosan.installer.ui.page.main.installer.dialog.InstallerViewState
import com.rosan.installer.ui.page.miuix.widgets.MiuixErrorTextBlock
import com.rosan.installer.ui.page.miuix.widgets.MiuixSwitchWidget
import com.rosan.installer.util.asUserReadableSplitName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.BackHandler
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun MiuixSheetContent(
    installer: InstallerRepo,
    viewModel: InstallerViewModel = koinViewModel {
        parametersOf(installer)
    }
) {
    LaunchedEffect(installer.id) {
        viewModel.dispatch(InstallerViewAction.CollectRepo(installer))
    }

    val showSettings = viewModel.showMiuixSheetRightActionSettings
    val currentPackageName by viewModel.currentPackageName.collectAsState()
    val packageName = currentPackageName ?: installer.analysisResults.firstOrNull()?.packageName ?: ""
    val displayIcons by viewModel.displayIcons.collectAsState()
    val installProgress by viewModel.installProgress.collectAsState()
    val installProgressText by viewModel.installProgressText.collectAsState()

    val analysisResult =
        if (currentPackageName != null) installer.analysisResults.find { it.packageName == currentPackageName } else null
    val baseEntity = analysisResult?.appEntities?.map { it.app }?.filterIsInstance<AppEntity.BaseEntity>()?.firstOrNull()
    val appIcon = if (currentPackageName != null) displayIcons[currentPackageName] else null

    BackHandler(
        enabled = showSettings,
        onBack = {
            viewModel.dispatch(InstallerViewAction.HideMiuixSheetRightActionSettings)
        }
    )

    @SuppressLint("UnusedContentLambdaTargetStateParameter")
    AnimatedContent(
        targetState = viewModel.state::class,
        label = "MiuixSheetContentAnimation",
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 150))
        }
    ) { _ ->
        when (viewModel.state) {
            is InstallerViewState.Preparing -> {
                LoadingContent(statusText = stringResource(R.string.installer_preparing))
            }

            is InstallerViewState.InstallChoice -> {
                InstallChoiceContent(installer, viewModel)
            }

            is InstallerViewState.InstallPrepare -> {
                AnimatedContent(
                    targetState = showSettings,
                    label = "PrepareContentVsSettings",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 150))
                    }
                ) { isShowingSettings ->
                    if (isShowingSettings) {
                        PrepareSettingsContent(installer, viewModel)
                    } else {
                        InstallPrepareContent(
                            installer = installer,
                            viewModel = viewModel,
                            onCancel = { viewModel.dispatch(InstallerViewAction.Close) },
                            onInstall = { viewModel.dispatch(InstallerViewAction.Install) }
                        )
                    }
                }
            }

            is InstallerViewState.Installing -> {
                // Show a progress indicator during installation.
                InstallingContent(
                    baseEntity = baseEntity,
                    appIcon = appIcon,
                    // progress = installProgress,
                    progressText = installProgressText?.toString()
                )
            }

            is InstallerViewState.InstallSuccess -> {
                InstallSuccessContent(
                    baseEntity = baseEntity,
                    installer = installer,
                    packageName = packageName,
                    appIcon = appIcon,
                    dhizukuAutoClose = viewModel.autoCloseCountDown,
                    onClose = { viewModel.dispatch(InstallerViewAction.Close) }
                )
            }

            is InstallerViewState.InstallFailed -> {
                InstallFailureContent(
                    baseEntity = baseEntity,
                    appIcon = appIcon,
                    error = installer.error,
                    onClose = { viewModel.dispatch(InstallerViewAction.Close) }
                )
            }

            is InstallerViewState.AnalyseFailed, is InstallerViewState.ResolveFailed -> {
                NonInstallFailureContent(
                    error = installer.error,
                    onClose = { viewModel.dispatch(InstallerViewAction.Close) }
                )
            }

            is InstallerViewState.Resolving, is InstallerViewState.Analysing -> {
                LoadingContent(
                    statusText = if (viewModel.state is InstallerViewState.Resolving) stringResource(R.string.installer_resolving)
                    else stringResource(R.string.installer_analysing)
                )
            }

            else -> {
                LoadingContent(statusText = stringResource(R.string.loading))
            }
        }
    }
}

@Composable
private fun InstallChoiceContent(installer: InstallerRepo, viewModel: InstallerViewModel) {
    val analysisResults = installer.analysisResults
    val containerType = analysisResults.firstOrNull()?.appEntities?.firstOrNull()?.app?.containerType ?: DataType.NONE
    val isMultiApk = containerType == DataType.MULTI_APK || containerType == DataType.MULTI_APK_ZIP
    val isModuleApk = containerType == DataType.MIXED_MODULE_APK

    val titleRes = if (isMultiApk) R.string.installer_select_from_zip else R.string.installer_select_install
    val primaryButtonTextRes = if (isMultiApk) R.string.install else R.string.next
    val primaryButtonAction = if (isMultiApk) {
        { viewModel.dispatch(InstallerViewAction.InstallMultiple) }
    } else {
        { viewModel.dispatch(InstallerViewAction.InstallPrepare) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title and Subtitle
        Text(stringResource(titleRes), style = MiuixTheme.textStyles.title1)
        when (containerType) {
            DataType.MIXED_MODULE_APK -> Text(
                "请选择安装类型",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )

            DataType.MULTI_APK_ZIP -> Text(
                stringResource(R.string.installer_multi_apk_zip_description),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )

            DataType.MULTI_APK -> Text(
                stringResource(R.string.installer_multi_apk_description),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )

            else -> Unit
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Content
        Box(modifier = Modifier.weight(1f, fill = false)) {
            ChoiceLazyList(
                analysisResults = analysisResults,
                viewModel = viewModel,
                isModuleApk = isModuleApk,
                isMultiApk = isMultiApk
            )
        }


        // Buttons
        if (!isModuleApk) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { viewModel.dispatch(InstallerViewAction.Close) },
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = primaryButtonAction,
                    text = stringResource(primaryButtonTextRes),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChoiceLazyList(
    analysisResults: List<PackageAnalysisResult>,
    viewModel: InstallerViewModel,
    isModuleApk: Boolean,
    isMultiApk: Boolean
) {
    val cardColor = if (isSystemInDarkTheme()) Color(0xFF434343) else Color.White

    if (isModuleApk) {
        val allSelectableEntities = analysisResults.flatMap { it.appEntities }
        val baseSelectableEntity = allSelectableEntities.firstOrNull { it.app is AppEntity.BaseEntity }
        val moduleSelectableEntity = allSelectableEntities.firstOrNull { it.app is AppEntity.ModuleEntity }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (baseSelectableEntity != null) {
                val baseEntityInfo = baseSelectableEntity.app as AppEntity.BaseEntity
                item {
                    Card(
                        onClick = {
                            viewModel.dispatch(
                                InstallerViewAction.ToggleSelection(
                                    packageName = baseSelectableEntity.app.packageName,
                                    entity = baseSelectableEntity,
                                    isMultiSelect = false
                                )
                            )
                            viewModel.dispatch(InstallerViewAction.InstallPrepare)
                        },
                        colors = CardColors(
                            color = cardColor,
                            contentColor = MiuixTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(baseEntityInfo.label ?: "N/A", style = MiuixTheme.textStyles.title2)
                            Text(
                                "Package: ${baseEntityInfo.packageName}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            if (moduleSelectableEntity != null) {
                val moduleEntityInfo = moduleSelectableEntity.app as AppEntity.ModuleEntity
                item {
                    Card(
                        onClick = {
                            viewModel.dispatch(
                                InstallerViewAction.ToggleSelection(
                                    packageName = moduleSelectableEntity.app.packageName,
                                    entity = moduleSelectableEntity,
                                    isMultiSelect = false
                                )
                            )
                            viewModel.dispatch(InstallerViewAction.InstallPrepare)
                        },
                        colors = CardColors(
                            color = cardColor,
                            contentColor = MiuixTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(moduleEntityInfo.name, style = MiuixTheme.textStyles.title2)
                            Text(
                                "Module ID: ${moduleEntityInfo.id}",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    } else if (isMultiApk) {
        LazyColumn(
            modifier = Modifier.wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(analysisResults, key = { _, it -> it.packageName }) { _, packageResult ->
                MiuixMultiApkGroupCard(
                    packageResult = packageResult,
                    viewModel = viewModel,
                    cardColor = cardColor
                )
            }
        }
    } else { // Single-Package Split Mode
        val entities = analysisResults.firstOrNull()?.appEntities ?: emptyList()
        LazyColumn(
            modifier = Modifier.wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(entities, key = { _, it -> it.app.name + it.app.packageName }) { _, item ->
                MiuixSingleItemCard(
                    item = item,
                    cardColor = cardColor,
                    onClick = {
                        viewModel.dispatch(
                            InstallerViewAction.ToggleSelection(
                                packageName = item.app.packageName,
                                entity = item,
                                isMultiSelect = true
                            )
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiuixMultiApkGroupCard(
    packageResult: PackageAnalysisResult,
    viewModel: InstallerViewModel,
    cardColor: Color
) {
    val itemsInGroup = packageResult.appEntities
    val isSingleItemInGroup = itemsInGroup.size == 1
    var isExpanded by remember { mutableStateOf(itemsInGroup.any { it.selected }) }
    val baseInfo = remember(itemsInGroup) { itemsInGroup.firstNotNullOfOrNull { it.app as? AppEntity.BaseEntity } }
    val appLabel = baseInfo?.label ?: packageResult.packageName

    if (isSingleItemInGroup) {
        val item = itemsInGroup.first()
        MiuixSingleItemCard(
            item = item,
            cardColor = cardColor,
            onClick = {
                viewModel.dispatch(
                    InstallerViewAction.ToggleSelection(
                        packageName = packageResult.packageName,
                        entity = item,
                        isMultiSelect = true
                    )
                )
            }
        )
    } else {
        val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "arrowRotation")
        Card(
            colors = CardColors(
                color = cardColor,
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appLabel, style = MiuixTheme.textStyles.title2)
                        Text(
                            packageResult.packageName,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    Icon(
                        imageVector = AppIcons.ArrowDropDown,
                        contentDescription = "Expand",
                        modifier = Modifier.rotate(rotation)
                    )
                }
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsInGroup
                            .sortedByDescending { (it.app as? AppEntity.BaseEntity)?.versionCode ?: 0 }
                            .forEach { item ->
                                MiuixSelectableSubCard(
                                    item = item,
                                    cardColor = cardColor,
                                    isRadio = true,
                                    onClick = {
                                        viewModel.dispatch(
                                            InstallerViewAction.ToggleSelection(
                                                packageName = packageResult.packageName,
                                                entity = item,
                                                isMultiSelect = false
                                            )
                                        )
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixSingleItemCard(
    item: SelectInstallEntity,
    cardColor: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            onClick()
        },
        colors = CardColors(
            color = cardColor,
            contentColor = MiuixTheme.colorScheme.onSurface
        ),
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.selected,
                onCheckedChange = null,
                /*colors = CheckboxDefaults.colors(
                    checkedColor = MiuixTheme.colorScheme.primary,
                    uncheckedColor = MiuixTheme.colorScheme.onSurface
                )*/
            )
            Spacer(modifier = Modifier.width(16.dp))
            ChoiceItemContent(app = item.app)
        }
    }
}

@Composable
private fun MiuixSelectableSubCard(item: SelectInstallEntity, cardColor: Color, isRadio: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val selectedColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        colors = CardColors(
            color = if (item.selected) selectedColor else cardColor,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRadio) {
                RadioButton(
                    selected = item.selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MiuixTheme.colorScheme.primary,
                        unselectedColor = MiuixTheme.colorScheme.onSurface
                    )
                )
            } else {
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onClick() }
                )
            }
            if (isRadio)
                (item.app as? AppEntity.BaseEntity)?.let { baseEntity ->
                    MultiApkItemContent(app = baseEntity)
                }
            else
                ChoiceItemContent(app = item.app)
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoiceItemContent(app: AppEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp)
    ) {
        when (app) {
            is AppEntity.BaseEntity -> {
                Text(
                    app.label ?: app.packageName,
                    style = MiuixTheme.textStyles.title2,
                )
                Text(
                    app.packageName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = stringResource(R.string.installer_version, app.versionName, app.versionCode),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }

            is AppEntity.SplitEntity -> {
                Text(
                    app.splitName.asUserReadableSplitName(),
                    style = MiuixTheme.textStyles.title2,
                )
                Text(
                    text = stringResource(R.string.installer_file_name, app.name),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            is AppEntity.DexMetadataEntity -> {
                Text(app.dmName, style = MiuixTheme.textStyles.title2)
                Text(
                    app.packageName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            // Should never happen!
            else -> Unit
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiApkItemContent(app: AppEntity.BaseEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.installer_version, app.versionName, app.versionCode),
            style = MiuixTheme.textStyles.title2
        )
        Text(
            text = app.data.getSourceTop().toString().removeSuffix("/").substringAfterLast('/'), // The original filename
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InstallPrepareContent(
    installer: InstallerRepo,
    viewModel: InstallerViewModel,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    val context = LocalContext.current
    val currentPackageName by viewModel.currentPackageName.collectAsState()
    val currentPackage = installer.analysisResults.find { it.packageName == currentPackageName }
    val displayIcons by viewModel.displayIcons.collectAsState()

    if (currentPackage == null) {
        LoadingContent(statusText = stringResource(id = R.string.loading))
        return
    }

    val selectedEntities = currentPackage.appEntities.filter { it.selected }.map { it.app }.sortedBest()
    if (selectedEntities.isEmpty()) {
        LoadingContent(statusText = "No apps selected")
        return
    }

    val primaryEntity = selectedEntities.first()
    val entityToInstall = selectedEntities.filterIsInstance<AppEntity.BaseEntity>().firstOrNull()
    val displayIcon = if (currentPackageName != null) displayIcons[currentPackageName] else null

    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MiuixTheme.colorScheme.primary

    val (warningMessages, buttonTextId) = remember(currentPackage, entityToInstall) {
        val newEntity = entityToInstall
        val oldInfo = currentPackage.installedAppInfo
        val signatureStatus = currentPackage.signatureMatchStatus
        val warnings = mutableListOf<Pair<String, Color>>()
        var finalButtonTextId = R.string.install
        if (newEntity != null) {
            if (oldInfo == null) {
                finalButtonTextId = R.string.install
            } else {
                when {
                    newEntity.versionCode > oldInfo.versionCode -> finalButtonTextId = R.string.upgrade
                    newEntity.versionCode < oldInfo.versionCode -> {
                        warnings.add(context.getString(R.string.installer_prepare_type_downgrade) to errorColor)
                        finalButtonTextId = R.string.install_anyway
                    }

                    oldInfo.isArchived -> finalButtonTextId = R.string.unarchive
                    else -> finalButtonTextId = R.string.reinstall
                }
            }
        }
        if (primaryEntity.containerType == DataType.APK || primaryEntity.containerType == DataType.APKS)
            when (signatureStatus) {
                SignatureMatchStatus.MISMATCH -> {
                    warnings.add(0, context.getString(R.string.installer_prepare_signature_mismatch) to errorColor)
                    finalButtonTextId = R.string.install_anyway
                }

                SignatureMatchStatus.UNKNOWN_ERROR -> {
                    warnings.add(0, context.getString(R.string.installer_prepare_signature_unknown) to tertiaryColor)
                }

                else -> {}
            }
        val newMinSdk = newEntity?.minSdk?.toIntOrNull()
        if (newMinSdk != null && newMinSdk > Build.VERSION.SDK_INT) {
            warnings.add(0, context.getString(R.string.installer_prepare_sdk_incompatible) to errorColor)
        }
        Pair(warnings, finalButtonTextId)
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            AppInfoSlot(
                icon = displayIcon,
                label = (primaryEntity as? AppEntity.BaseEntity)?.label ?: primaryEntity.packageName,
                packageName = primaryEntity.packageName
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            WarningTextBlock(warnings = warningMessages)
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardColors(
                    color = if (isSystemInDarkTheme()) Color(0xFF434343) else Color.White,
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (primaryEntity is AppEntity.BaseEntity) {
                        AdaptiveInfoRow(
                            labelResId = R.string.installer_version_name_label,
                            newValue = primaryEntity.versionName,
                            oldValue = currentPackage.installedAppInfo?.versionName,
                            isArchived = currentPackage.installedAppInfo?.isArchived ?: false
                        )
                        AdaptiveInfoRow(
                            labelResId = R.string.installer_version_code_label,
                            newValue = primaryEntity.versionCode.toString(),
                            oldValue = currentPackage.installedAppInfo?.versionCode?.toString(),
                            isDowngrade = if (currentPackage.installedAppInfo != null) primaryEntity.versionCode < currentPackage.installedAppInfo.versionCode else false,
                            isArchived = currentPackage.installedAppInfo?.isArchived ?: false
                        )
                        SDKComparison(
                            entityToInstall = primaryEntity,
                            preInstallAppInfo = currentPackage.installedAppInfo,
                            installer = installer,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        item {
            val canInstall = entityToInstall?.minSdk?.toIntOrNull()?.let { it <= Build.VERSION.SDK_INT } ?: true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancel,
                    text = stringResource(R.string.cancel),
                    modifier = Modifier.weight(1f),
                )
                if (canInstall) {
                    TextButton(
                        onClick = onInstall,
                        text = stringResource(buttonTextId),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private val InfoRowSpacing = 8.dp

@Composable
private fun AdaptiveInfoRow(
    @StringRes labelResId: Int,
    newValue: String,
    oldValue: String?,
    isDowngrade: Boolean = false,
    isArchived: Boolean
) {
    val showComparison = oldValue != null && newValue != oldValue
    val oldTextContent = if (isArchived) stringResource(R.string.old_version_archived) else oldValue.orEmpty()

    SubcomposeLayout { constraints ->
        val label = @Composable {
            Text(
                text = stringResource(labelResId),
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold
            )
        }

        val valueContentSingleLine = @Composable {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showComparison) {
                    Text(oldTextContent, style = MiuixTheme.textStyles.body2)
                    Icon(
                        imageVector = AppIcons.ArrowIndicator,
                        contentDescription = "to",
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(16.dp)
                    )
                    Text(newValue, style = MiuixTheme.textStyles.body2)
                } else {
                    Text(newValue, style = MiuixTheme.textStyles.body2)
                }
            }
        }

        val labelPlaceable = subcompose("label", label).first().measure(constraints)
        val valuePlaceable = subcompose("valueContent", valueContentSingleLine).first().measure(constraints)

        val totalWidth = labelPlaceable.width + InfoRowSpacing.roundToPx() + valuePlaceable.width
        val shouldWrap = totalWidth > constraints.maxWidth

        if (shouldWrap && showComparison) {
            val oldValueText = @Composable { Text(oldTextContent, style = MiuixTheme.textStyles.body2) }
            val newValueTextWithArrow = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.ArrowIndicator,
                        contentDescription = "to",
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(16.dp)
                    )
                    Text(newValue, style = MiuixTheme.textStyles.body2)
                }
            }

            val oldTextPlaceable = subcompose("oldTextWrap", oldValueText).first().measure(constraints)
            val newTextWithArrowPlaceable = subcompose("newTextWrap", newValueTextWithArrow).first().measure(constraints)

            val firstRowHeight = maxOf(labelPlaceable.height, oldTextPlaceable.height)
            val secondRowHeight = newTextWithArrowPlaceable.height
            val totalHeight = firstRowHeight + InfoRowSpacing.roundToPx() + secondRowHeight

            layout(constraints.maxWidth, totalHeight) {
                labelPlaceable.placeRelative(0, Alignment.CenterVertically.align(labelPlaceable.height, firstRowHeight))
                oldTextPlaceable.placeRelative(
                    constraints.maxWidth - oldTextPlaceable.width,
                    Alignment.CenterVertically.align(oldTextPlaceable.height, firstRowHeight)
                )

                val secondRowYOffset = firstRowHeight + InfoRowSpacing.roundToPx()
                newTextWithArrowPlaceable.placeRelative(
                    constraints.maxWidth - newTextWithArrowPlaceable.width,
                    secondRowYOffset + Alignment.CenterVertically.align(newTextWithArrowPlaceable.height, secondRowHeight)
                )
            }
        } else {
            val height = maxOf(labelPlaceable.height, valuePlaceable.height)
            layout(constraints.maxWidth, height) {
                labelPlaceable.placeRelative(0, Alignment.CenterVertically.align(labelPlaceable.height, height))
                valuePlaceable.placeRelative(
                    constraints.maxWidth - valuePlaceable.width,
                    Alignment.CenterVertically.align(valuePlaceable.height, height)
                )
            }
        }
    }
}

@Composable
private fun WarningTextBlock(warnings: List<Pair<String, Color>>) {
    AnimatedVisibility(visible = warnings.isNotEmpty()) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            warnings.forEach { (text, color) ->
                Text(
                    text = text,
                    color = color,
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SDKComparison(
    entityToInstall: AppEntity,
    preInstallAppInfo: InstalledAppInfo?,
    viewModel: InstallerViewModel,
    installer: InstallerRepo
) {
    AnimatedVisibility(visible = installer.config.displaySdk) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Target SDK
            entityToInstall.targetSdk?.let { newTargetSdk ->
                SdkInfoRow(
                    labelResId = R.string.installer_package_target_sdk_label,
                    newSdk = newTargetSdk,
                    oldSdk = preInstallAppInfo?.targetSdk?.toString(),
                    isArchived = preInstallAppInfo?.isArchived ?: false,
                    type = "target"
                )
            }
            // Min SDK
            entityToInstall.minSdk?.let { newMinSdk ->
                SdkInfoRow(
                    labelResId = R.string.installer_package_min_sdk_label,
                    newSdk = newMinSdk,
                    oldSdk = preInstallAppInfo?.minSdk?.toString(),
                    isArchived = preInstallAppInfo?.isArchived ?: false,
                    type = "min"
                )
            }
        }
    }
}

@Composable
private fun SdkInfoRow(
    @StringRes labelResId: Int,
    newSdk: String,
    oldSdk: String?,
    isArchived: Boolean,
    type: String // "min" or "target"
) {
    val newSdkInt = newSdk.toIntOrNull()
    val oldSdkInt = oldSdk?.toIntOrNull()
    val showComparison = oldSdkInt != null && newSdkInt != null && newSdkInt != oldSdkInt

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label to the left.
        Text(
            text = stringResource(labelResId),
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold
        )

        // Label to the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showComparison) {
                val isDowngrade = newSdkInt < oldSdkInt
                val isIncompatible = type == "min" && newSdkInt > Build.VERSION.SDK_INT
                // val color = if (isDowngrade || isIncompatible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                val oldText = if (isArchived) stringResource(R.string.old_version_archived) else oldSdk.toString()

                Text(text = oldText, style = MiuixTheme.textStyles.body2)

                Icon(
                    imageVector = AppIcons.ArrowIndicator,
                    contentDescription = "to",
                    // tint = color,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(16.dp)
                )

                Text(text = newSdk/*, color = color*/, style = MiuixTheme.textStyles.body2)
            } else {
                val isIncompatible = type == "min" && newSdkInt != null && newSdkInt > Build.VERSION.SDK_INT
                val color = if (isIncompatible) MaterialTheme.colorScheme.error else Color.Unspecified

                Text(text = newSdk, color = color, style = MiuixTheme.textStyles.body2)
            }
        }
    }
}

@Composable
private fun PrepareSettingsContent(installer: InstallerRepo, viewModel: InstallerViewModel) {
    var autoDelete by remember { mutableStateOf(installer.config.autoDelete) }
    var displaySdk by remember { mutableStateOf(installer.config.displaySdk) }

    LaunchedEffect(autoDelete, displaySdk) {
        val currentConfig = installer.config
        if (currentConfig.autoDelete != autoDelete) installer.config.autoDelete = autoDelete
        if (currentConfig.displaySdk != displaySdk) installer.config.displaySdk = displaySdk
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.padding(bottom = 6.dp),
            colors = CardColors(
                color = if (isSystemInDarkTheme()) Color(0xFF434343) else Color.White,
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            MiuixSwitchWidget(
                title = stringResource(R.string.config_display_sdk_version),
                description = stringResource(R.string.config_display_sdk_version_desc),
                checked = displaySdk,
                onCheckedChange = {
                    val newValue = !displaySdk
                    displaySdk = newValue
                    installer.config.displaySdk = newValue
                }
            )
            MiuixSwitchWidget(
                title = stringResource(R.string.config_auto_delete),
                description = stringResource(R.string.config_auto_delete_desc),
                checked = installer.config.autoDelete,
                onCheckedChange = {
                    val newValue = !autoDelete
                    autoDelete = newValue
                    installer.config.autoDelete = newValue
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InstallingContent(
    baseEntity: AppEntity.BaseEntity?,
    appIcon: Drawable?,
    progressText: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppInfoSlot(
            icon = appIcon,
            label = baseEntity?.label ?: "Unknown App",
            packageName = baseEntity?.packageName ?: "unknown.package"
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            enabled = false,
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Row {
                InfiniteProgressIndicator()
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = progressText ?: stringResource(R.string.installer_installing)
                )
            }
        }
    }
}

@Composable
private fun NonInstallFailureContent(
    error: Throwable,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MiuixErrorTextBlock(
            error = error,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun InstallSuccessContent(
    baseEntity: AppEntity.BaseEntity?,
    installer: InstallerRepo,
    appIcon: Drawable?,
    packageName: String,
    dhizukuAutoClose: Int,
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppInfoSlot(
            icon = appIcon,
            label = baseEntity?.label ?: "Unknown App",
            packageName = baseEntity?.packageName ?: "unknown.package"
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.installer_install_success),
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val intent =
                if (packageName.isNotEmpty()) context.packageManager.getLaunchIntentForPackage(
                    packageName
                ) else null
            TextButton(
                text = stringResource(R.string.finish),
                modifier = Modifier.weight(1f),
                onClick = onClose,
            )
            if (intent != null)
                TextButton(
                    text = stringResource(R.string.open),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            openAppPrivileged(
                                context = context,
                                config = installer.config,
                                packageName = packageName,
                                dhizukuAutoCloseSeconds = dhizukuAutoClose,
                                onSuccess = onClose
                            )
                        }
                    }
                )
        }
    }
}

@Composable
private fun InstallFailureContent(
    baseEntity: AppEntity.BaseEntity?,
    appIcon: Drawable?,
    error: Throwable,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppInfoSlot(
            icon = appIcon,
            label = baseEntity?.label ?: "Unknown App",
            packageName = baseEntity?.packageName ?: "unknown.package"
        )
        Spacer(modifier = Modifier.height(32.dp))
        MiuixErrorTextBlock(
            error = error,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(/*horizontal = 16.dp, */vertical = 24.dp),
        ) {
            TextButton(
                onClick = onClose,
                text = stringResource(R.string.close),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoadingContent(statusText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.5f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            InfiniteProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(statusText, style = MiuixTheme.textStyles.body1)
        }
    }
}


@Composable
private fun ActionButtons(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onCancel,
            text = cancelText,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onConfirm,
            text = confirmText,
            colors = ButtonDefaults.textButtonColorsPrimary(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppInfoSlot(
    icon: Drawable?,
    label: String,
    packageName: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = icon),
            contentDescription = "App Icon",
            modifier = Modifier.size(72.dp)
        )
        Text(label, style = MiuixTheme.textStyles.title2)
        Text(packageName, style = MiuixTheme.textStyles.subtitle)
    }
}