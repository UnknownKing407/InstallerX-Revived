package com.rosan.installer.ui.page.miuix.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Switch

@Composable
fun MiuixSwitchWidget(
    icon: ImageVector? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
    // Note: The 'isError' parameter is removed as it's not supported by the standard BasicComponent.
) {
    // This action makes the entire row clickable to toggle the switch.
    val toggleAction = {
        if (enabled) {
            onCheckedChange(!checked)
        }
    }

    BasicComponent(
        title = title,
        summary = description,
        enabled = enabled,
        onClick = toggleAction,
        rightActions = {
            // Place the Switch component at the end of the row.
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}

/**
 * A BasicComponent variant that displays a Checkbox as its right action.
 * The entire row is clickable to toggle the checked state.
 *
 * @param icon Optional icon for the component (Note: not passed to BasicComponent,
 * matching MiuixSwitchWidget's provided structure).
 * @param title The main text title.
 * @param description The supporting text (summary).
 * @param enabled Controls the enabled state of the component and the Checkbox.
 * @param checked The current checked state of the Checkbox.
 * @param onCheckedChange A lambda called when the checked state changes.
 */
@Composable
fun MiuixCheckboxWidget(
    icon: ImageVector? = null, // Following your MiuixSwitchWidget signature
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // This action makes the entire row clickable to toggle the checkbox.
    val toggleAction = {
        if (enabled) {
            onCheckedChange(!checked)
        }
    }

    BasicComponent(
        // Note: 'icon' parameter is not used here, to match the
        // structure of the MiuixSwitchWidget you provided.
        // If your BasicComponent accepts an icon, you should pass it here.
        title = title,
        summary = description,
        enabled = enabled,
        onClick = toggleAction,
        rightActions = {
            // Use a Checkbox instead of a Switch
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}