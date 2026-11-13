package com.pawquest.presentation.components

import com.pawquest.localization.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawquest.presentation.theme.PawQuestTheme

enum class ButtonVariant {
    Primary,
    Secondary,
    Accent,
    Outlined,
    Ghost,
}

enum class ButtonSize {
    Small,
    Medium,
    Large,
}

/**
 * Friendly, tactile 3D-feel button for PawQuest.
 * Features a cute press animation, responsive padding, and compliant 48dp minimum touch target.
 */
@Composable
fun PawQuestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = PawQuestTheme.shapes.button,
    labelColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !loading) 0.98f else 1.0f,
        label = "button_scale",
    )

    val colors = PawQuestTheme.colors
    val (containerColor, contentColor, borderColor, shadowElevation) = when (variant) {
        ButtonVariant.Primary -> {
            if (enabled) {
                Quadruple(colors.primary, colors.onPrimary, null, if (isPressed) 1.dp else 4.dp)
            } else {
                Quadruple(colors.surfaceVariant, colors.textMuted, colors.textMuted, 0.dp)
            }
        }
        ButtonVariant.Secondary -> {
            if (enabled) {
                Quadruple(colors.secondary, colors.textPrimary, null, if (isPressed) 0.dp else 2.dp)
            } else {
                Quadruple(colors.backgroundAlt, colors.textMuted, colors.borderLight, 0.dp)
            }
        }
        ButtonVariant.Accent -> {
            if (enabled) {
                Quadruple(colors.accentGold, colors.textPrimary, null, if (isPressed) 1.dp else 4.dp)
            } else {
                Quadruple(colors.surfaceVariant, colors.textMuted, colors.textMuted, 0.dp)
            }
        }
        ButtonVariant.Outlined -> {
            if (enabled) {
                Quadruple(Color.Transparent, colors.textPrimary, colors.outlineStrong, 0.dp)
            } else {
                Quadruple(Color.Transparent, colors.textMuted, colors.borderLight, 0.dp)
            }
        }
        ButtonVariant.Ghost -> {
            if (enabled) {
                Quadruple(Color.Transparent, colors.primaryDark, null, 0.dp)
            } else {
                Quadruple(Color.Transparent, colors.textMuted, null, 0.dp)
            }
        }
    }

    val (minHeight, horizontalPadding, iconSize, textStyle) = when (size) {
        ButtonSize.Small -> Quadruple(
            36.dp,
            12.dp,
            16.dp,
            PawQuestTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
        ButtonSize.Medium -> Quadruple(
            48.dp,
            20.dp,
            20.dp,
            PawQuestTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        ButtonSize.Large -> Quadruple(
            56.dp,
            24.dp,
            24.dp,
            PawQuestTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = 48.dp) // Accessibility minimum
            .drawBehind {
                // A solid shadow behind transparent outlined buttons hides their label.
                if (enabled && containerColor.alpha>0f && variant != ButtonVariant.Ghost && !isPressed) {
                    val outline=shape.createOutline(this.size,layoutDirection,this)
                    translate(top=3.dp.toPx()) {drawOutline(outline,colors.shadowColor)}
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button },
        shape = shape,
        color = containerColor,
        contentColor = labelColor ?: contentColor,
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = minHeight)
                .padding(PaddingValues(horizontal = horizontalPadding, vertical = 8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    modifier=if(trailingContent!=null) Modifier.fillMaxWidth() else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = contentColor,
                        )
                    }
                    ProvideTextStyle(value = textStyle) {
                        Text(text = tr(text))
                    }
                    trailingIcon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = contentColor,
                        )
                    }
                    if(trailingContent!=null) {Spacer(Modifier.weight(1f));trailingContent()}
                }
            }
        }
    }
}

/**
 * Convenient circular/rounded tactile icon button.
 */
@Composable
fun PawQuestIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1.0f,
        label = "icon_button_scale",
    )

    val colors = PawQuestTheme.colors
    val (containerColor, contentColor, borderColor) = when (variant) {
        ButtonVariant.Primary -> Triple(colors.primary, colors.onPrimary, null)
        ButtonVariant.Secondary -> Triple(colors.secondary, colors.primaryDark, colors.secondaryDark)
        ButtonVariant.Accent -> Triple(colors.accentGold, colors.textPrimary, colors.accentGoldDark)
        ButtonVariant.Outlined -> Triple(Color.Transparent, colors.textPrimary, colors.borderLight)
        ButtonVariant.Ghost -> Triple(Color.Transparent, colors.primaryDark, null)
    }

    Surface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button },
        shape = PawQuestTheme.shapes.pill,
        color = containerColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        shadowElevation = if (isPressed) 0.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tr(contentDescription),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
    }
}

/**
 * Large tactile action button designed for virtual pet needs (Feed, Play, Sleep).
 * Features active highlight border, smooth press scaling, and minimum 48dp touch target.
 */
@Composable
fun PawQuestActionButton(
    text: String,
    icon: ImageVector,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PawQuestTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1.0f,
        label = "action_button_scale",
    )

    val effectiveContainer = when {
        !enabled -> colors.surfaceVariant
        active -> colors.primaryContainer
        else -> colors.surface
    }
    val effectiveBorder = when {
        !enabled -> colors.borderLight
        active -> colors.primaryDark
        else -> colors.borderLight
    }
    val effectiveText = when {
        !enabled -> colors.textMuted
        active -> colors.primaryDark
        else -> colors.textPrimary
    }
    val effectiveIcon = when {
        !enabled -> colors.textMuted
        active -> colors.primaryDark
        else -> color
    }

    Surface(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { role = Role.Button },
        shape = PawQuestTheme.shapes.button,
        color = effectiveContainer,
        border = BorderStroke(
            width = if (active) 2.dp else 1.dp,
            color = effectiveBorder,
        ),
        shadowElevation = if (!enabled || active || isPressed) 1.dp else 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = effectiveIcon,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = tr(text),
                style = PawQuestTheme.typography.titleMedium.copy(
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                ),
                color = effectiveText,
                maxLines = 1,
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
