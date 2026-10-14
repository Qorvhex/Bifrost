package com.bifrost.twp.ui.components.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Provides smooth spring-based bounce feedback when clicked.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        try {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (_: Exception) {}
                        onClick()
                    }
                )
            } else {
                Modifier
            }
        )
}

/**
 * Creates and remembers a MutableInteractionSource alongside a Modifier that applies
 * an elastic spring scale compression whenever the interaction is pressed.
 */
@Composable
fun rememberBouncePress(
    scaleDown: Float = 0.94f
): Pair<MutableInteractionSource, Modifier> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )

    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    return Pair(interactionSource, modifier)
}

/**
 * Tactile bouncy Button wrapper with spring physics.
 */
@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    scaleDown: Float = 0.95f,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, bounceMod) = rememberBouncePress(scaleDown)
    val haptic = LocalHapticFeedback.current

    Button(
        onClick = {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
            onClick()
        },
        modifier = modifier.then(bounceMod),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content
    )
}

/**
 * Tactile bouncy OutlinedButton wrapper with spring physics.
 */
@Composable
fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    scaleDown: Float = 0.95f,
    content: @Composable RowScope.() -> Unit
) {
    val (interaction, bounceMod) = rememberBouncePress(scaleDown)
    val haptic = LocalHapticFeedback.current

    OutlinedButton(
        onClick = {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
            onClick()
        },
        modifier = modifier.then(bounceMod),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content
    )
}

/**
 * Tactile bouncy IconButton wrapper with spring physics.
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scaleDown: Float = 0.88f,
    content: @Composable () -> Unit
) {
    val (interaction, bounceMod) = rememberBouncePress(scaleDown)
    val haptic = LocalHapticFeedback.current

    IconButton(
        onClick = {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
            onClick()
        },
        modifier = modifier.then(bounceMod),
        enabled = enabled,
        interactionSource = interaction,
        content = content
    )
}

/**
 * Tactile bouncy FloatingActionButton wrapper with spring physics.
 */
@Composable
fun BouncyFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    scaleDown: Float = 0.92f,
    content: @Composable () -> Unit
) {
    val (interaction, bounceMod) = rememberBouncePress(scaleDown)
    val haptic = LocalHapticFeedback.current

    FloatingActionButton(
        onClick = {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
            onClick()
        },
        modifier = modifier.then(bounceMod),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interaction,
        content = content
    )
}

/**
 * Full-featured modal dialog with elastic spring entrance and graceful exit transitions.
 *
 * Provides a `dismissWithAnimation` callback to allow dismiss actions (Back press, outside click,
 * Cancel / Save / Close buttons) to trigger the exit animation before the Composable is removed.
 */
@Composable
fun BifrostModalDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable (dismissWithAnimation: (onComplete: (() -> Unit)?) -> Unit) -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val dismissWithAnimation: (onComplete: (() -> Unit)?) -> Unit = { action ->
        if (isVisible) {
            isVisible = false
            coroutineScope.launch {
                delay(200) // Allow exit animation to complete smoothly
                if (action != null) {
                    action()
                } else {
                    onDismissRequest()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = {
            dismissWithAnimation()
        },
        properties = properties
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(180, easing = FastOutLinearInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (properties.dismissOnClickOutside) {
                            dismissWithAnimation()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .animateEnterExit(
                            enter = scaleIn(
                                initialScale = 0.82f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + slideInVertically(
                                initialOffsetY = { 60 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                            exit = scaleOut(
                                targetScale = 0.85f,
                                animationSpec = tween(180, easing = FastOutLinearInEasing)
                            ) + slideOutVertically(
                                targetOffsetY = { 50 },
                                animationSpec = tween(180, easing = FastOutLinearInEasing)
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Ingest click inside modal container to prevent backdrop dismiss
                        }
                ) {
                    content(dismissWithAnimation)
                }
            }
        }
    }
}
