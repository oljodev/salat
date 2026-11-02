package no.jodal.salat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.jodal.salat.MiniApp
import no.jodal.salat.MiniAppIcon
import no.jodal.salat.miniApps
import no.jodal.salat.resources.Res
import no.jodal.salat.resources.salat_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(darkTheme: Boolean, onOpen: (MiniApp) -> Unit) {
    val apps = miniApps.filter { it.section == MiniApp.Section.Apps }
    val tools = miniApps.filter { it.section == MiniApp.Section.Tools }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
        ),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        fullWidth { Header(darkTheme) }

        fullWidth { SectionTitle("Appene dine") }
        items(apps) { app -> AppTile(app, darkTheme, onClick = { onOpen(app) }) }

        fullWidth { SectionTitle("Verktøy") }
        items(tools, span = { GridItemSpan(maxLineSpan) }) { app ->
            ToolRow(app, darkTheme, onClick = { onOpen(app) })
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Header(darkTheme: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .bleedHorizontally(20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(
                Brush.verticalGradient(
                    listOf(colors.tint(SalatColors.Leaf, darkTheme), colors.tint(SalatColors.Bowl, darkTheme)),
                ),
            )
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(84.dp).clip(CircleShape).background(SalatColors.Bowl),
            ) {
                Image(painterResource(Res.drawable.salat_logo), contentDescription = null, modifier = Modifier.size(66.dp))
            }
            Column {
                Text(greeting(), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                Text("Salat", style = MaterialTheme.typography.displaySmall, color = colors.onBackground)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun AppTile(app: MiniApp, darkTheme: Boolean, onClick: () -> Unit) {
    val available = app.content != null
    val colors = MaterialTheme.colorScheme.let {
        if (app.solidTile) it.copy(onSurface = Color.White, onSurfaceVariant = Color(0xFF8E8E93)) else it
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .pressable(enabled = available, onClick = onClick)
            .fillMaxWidth()
            .heightIn(min = 176.dp)
            .alpha(if (available) 1f else 0.75f)
            .clip(RoundedCornerShape(28.dp))
            .background(if (app.solidTile) app.color else colors.tint(app.color, darkTheme))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            AppIcon(app, size = 60)
            Spacer(Modifier.weight(1f))
            if (!available) {
                Text(
                    "Snart",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f, fill = false).heightIn(min = 6.dp))
        Text(app.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text(
            app.description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ToolRow(app: MiniApp, darkTheme: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .pressable(onClick = onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer)
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(app.color),
        ) {
            MiniAppIconImage(app.icon, size = if (app.icon is MiniAppIcon.Logo) 36.dp else 22.dp, tint = IconOnPastel)
        }
        Column(Modifier.weight(1f)) {
            Text(app.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(app.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
fun AppIcon(app: MiniApp, size: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.3f).dp))
            .background(app.color),
    ) {
        val icon = app.icon
        val iconSize = when {
            icon is MiniAppIcon.Logo && icon.fullBleed -> size.toFloat()
            icon is MiniAppIcon.Logo -> size * 0.8f
            else -> size * 0.5f
        }
        MiniAppIconImage(icon, size = iconSize.dp, tint = IconOnPastel)
    }
}

/** Ikonfarge som er lett å se på de lyse app-fargene, i både lys og mørk modus. */
private val IconOnPastel = Color(0xFF2A2318)

/** Krymper litt når man trykker, så det føles som en knapp. */
@Composable
private fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f)
    return scale(scale).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** Lar elementet gå [amount] ut over kantene på begge sider, forbi padding i listen. */
private fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

private fun greeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..9 -> "God morgen"
        in 10..11 -> "God formiddag"
        in 12..17 -> "God ettermiddag"
        in 18..22 -> "God kveld"
        else -> "God natt"
    }
}
