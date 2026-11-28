package id.nexrify.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.nexrify.music.ui.theme.NexrifyAppTheme

data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun NexFloatingNavBar(
    items: List<NavTabItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NexrifyAppTheme.colors
    val isV2 = NexrifyAppTheme.isV2

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.65f),
                    spotColor = Color.Black.copy(alpha = 0.8f)
                )
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (colors.isDark) {
                            listOf(
                                Color(0xFF1A1C23).copy(alpha = 0.94f),
                                Color(0xFF0E1014).copy(alpha = 0.98f)
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.96f),
                                Color(0xFFF1F3F7).copy(alpha = 0.98f)
                            )
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (colors.isDark) {
                            listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        } else {
                            listOf(
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.04f)
                            )
                        }
                    ),
                    shape = CircleShape
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.93f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "navScale"
                )
                val pillWidth by animateDpAsState(
                    targetValue = if (isSelected) 118.dp else 52.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "navPillWidth"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) colors.background else colors.textMuted,
                    label = "navContentColor"
                )

                val activeBrush = if (isV2) {
                    Brush.horizontalGradient(listOf(colors.accent, colors.accentAlt))
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            colors.textPrimary,
                            colors.textPrimary.copy(alpha = 0.88f)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .height(48.dp)
                        .width(pillWidth)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) activeBrush
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onNavigate(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = item.label,
                                color = contentColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
