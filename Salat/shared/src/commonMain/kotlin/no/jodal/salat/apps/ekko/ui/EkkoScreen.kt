package no.jodal.salat.apps.ekko.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.jodal.salat.Haptics
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.ekko.Clip
import no.jodal.salat.apps.ekko.ClipFilter
import no.jodal.salat.apps.ekko.EkkoImporter
import no.jodal.salat.apps.ekko.EkkoPlayer
import no.jodal.salat.apps.ekko.EkkoStore
import no.jodal.salat.apps.ekko.Playlist
import no.jodal.salat.apps.ekko.formatCount
import no.jodal.salat.apps.ekko.people
import no.jodal.salat.resources.Res
import no.jodal.salat.resources.ekko_logo
import no.jodal.salat.ui.BackButton
import no.jodal.salat.ui.pressable
import org.jetbrains.compose.resources.painterResource

private sealed interface EkkoDestination {
    data object Home : EkkoDestination
    data class PlaylistPage(val id: String) : EkkoDestination
    data object Add : EkkoDestination
    data class Edit(val clipId: String, val back: EkkoDestination) : EkkoDestination
}

enum class EkkoTab { Sounds, Playlists }

/** Det som er åpent i et ark nederst. */
private sealed interface EkkoSheet {
    data class ClipActions(val clip: Clip, val playlist: Playlist?) : EkkoSheet
    data class AddToPlaylist(val clip: Clip) : EkkoSheet
    data object NewPlaylist : EkkoSheet
    data class PlaylistActions(val playlist: Playlist) : EkkoSheet
    data class RenamePlaylist(val playlist: Playlist) : EkkoSheet
    data class PickClips(val playlist: Playlist) : EkkoSheet
}

@Composable
fun EkkoScreen(bridge: NativeBridge, onBack: () -> Unit) {
    EkkoTheme {
        val store = remember { EkkoStore.get(bridge) }
        val player = remember { EkkoPlayer.get(store, bridge) }
        val importer = remember { EkkoImporter.get(store, bridge) }
        var destination by remember {
            mutableStateOf<EkkoDestination>(if (importer.state is EkkoImporter.State.Idle) EkkoDestination.Home else EkkoDestination.Add)
        }
        var tab by remember { mutableStateOf(EkkoTab.Sounds) }
        var filter by remember { mutableStateOf(ClipFilter()) }
        var nowPlayingOpen by remember { mutableStateOf(false) }
        var sheet by remember { mutableStateOf<EkkoSheet?>(null) }
        var confirmDelete by remember { mutableStateOf<Clip?>(null) }
        var confirmDeletePlaylist by remember { mutableStateOf<Playlist?>(null) }

        LaunchedEffect(player.queue == null) {
            if (player.queue == null) nowPlayingOpen = false
        }

        Box(Modifier.fillMaxSize().background(EkkoPalette.Ink)) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val duration = 280
                    val forward = targetState !is EkkoDestination.Home && initialState !is EkkoDestination.Edit
                    if (forward) {
                        slideInHorizontally(tween(duration)) { it } togetherWith fadeOut(tween(duration))
                    } else {
                        fadeIn(tween(duration)) togetherWith slideOutHorizontally(tween(duration)) { it }
                    }
                },
            ) { current ->
                when (current) {
                    EkkoDestination.Home -> EkkoHome(
                        store = store,
                        player = player,
                        tab = tab,
                        onTab = { tab = it },
                        filter = filter,
                        onFilter = { filter = it },
                        onBack = onBack,
                        onAdd = { destination = EkkoDestination.Add },
                        onMore = { sheet = EkkoSheet.ClipActions(it, null) },
                        onOpenPlaylist = { destination = EkkoDestination.PlaylistPage(it.id) },
                        onNewPlaylist = { sheet = EkkoSheet.NewPlaylist },
                    )
                    is EkkoDestination.PlaylistPage -> {
                        val playlist = store.playlist(current.id)
                        if (playlist == null) {
                            LaunchedEffect(Unit) { destination = EkkoDestination.Home }
                        } else {
                            PlaylistScreen(
                                playlist = playlist,
                                store = store,
                                player = player,
                                onBack = { destination = EkkoDestination.Home },
                                onMore = { sheet = EkkoSheet.ClipActions(it, playlist) },
                                onAddClips = { sheet = EkkoSheet.PickClips(playlist) },
                                onPlaylistActions = { sheet = EkkoSheet.PlaylistActions(playlist) },
                            )
                        }
                    }
                    EkkoDestination.Add -> AddClipScreen(
                        importer = importer,
                        player = player,
                        bridge = bridge,
                        onClose = { destination = EkkoDestination.Home },
                        onSaved = {
                            tab = EkkoTab.Sounds
                            destination = EkkoDestination.Home
                        },
                    )
                    is EkkoDestination.Edit -> {
                        val clip = store.clip(current.clipId)
                        if (clip == null) {
                            LaunchedEffect(Unit) { destination = current.back }
                        } else {
                            ClipEditor(
                                heading = "Rediger lyd",
                                clipId = clip.id,
                                initialTitle = clip.title,
                                initialArtist = clip.artist,
                                sourceSeconds = clip.sourceSeconds,
                                waveform = clip.waveform,
                                initialStart = clip.startSeconds,
                                initialEnd = clip.endSeconds,
                                player = player,
                                onCancel = { destination = current.back },
                                onSave = { title, artist, start, end ->
                                    store.saveClip(
                                        clip.copy(
                                            title = title.trim().ifEmpty { clip.title },
                                            artist = artist.trim(),
                                            startSeconds = start,
                                            endSeconds = end,
                                        ),
                                    )
                                    destination = current.back
                                },
                            )
                        }
                    }
                }
            }

            // Minispilleren nederst, på forsiden og i spillelistene.
            val showMini = destination is EkkoDestination.Home || destination is EkkoDestination.PlaylistPage
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                player.error?.let { ErrorBanner(it, onDismiss = player::dismissError) }
                AnimatedVisibility(
                    visible = showMini && player.current != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    player.current?.let { MiniPlayer(it, player, store, onOpen = { nowPlayingOpen = true }) }
                }
            }

            AnimatedVisibility(
                visible = nowPlayingOpen,
                enter = slideInVertically(tween(320)) { it },
                exit = slideOutVertically(tween(280)) { it },
            ) {
                NowPlayingScreen(
                    store = store,
                    player = player,
                    onClose = { nowPlayingOpen = false },
                    onEdit = { clip ->
                        nowPlayingOpen = false
                        destination = EkkoDestination.Edit(clip.id, destination)
                    },
                )
            }
        }

        when (val open = sheet) {
            null -> Unit
            is EkkoSheet.ClipActions -> ClipActionsSheet(
                clip = open.clip,
                playlist = open.playlist,
                onSoundboard = store.isOnSoundboard(open.clip.id),
                onDismiss = { sheet = null },
                onToggleSoundboard = {
                    store.toggleSoundboard(open.clip.id)
                    sheet = null
                },
                onEdit = {
                    sheet = null
                    destination = EkkoDestination.Edit(open.clip.id, destination)
                },
                onAddToPlaylist = { sheet = EkkoSheet.AddToPlaylist(open.clip) },
                onRemoveFromPlaylist = {
                    open.playlist?.let { store.removeFromPlaylist(it.id, open.clip.id) }
                    sheet = null
                },
                onDelete = {
                    sheet = null
                    confirmDelete = open.clip
                },
            )
            is EkkoSheet.AddToPlaylist -> AddToPlaylistSheet(
                clip = open.clip,
                store = store,
                onDismiss = { sheet = null },
            )
            EkkoSheet.NewPlaylist -> NameSheet(
                title = "Ny spilleliste",
                initial = "",
                confirm = "Lag",
                onDismiss = { sheet = null },
                onConfirm = { name ->
                    val playlist = store.createPlaylist(name)
                    sheet = null
                    destination = EkkoDestination.PlaylistPage(playlist.id)
                },
            )
            is EkkoSheet.PlaylistActions -> PlaylistActionsSheet(
                playlist = open.playlist,
                onDismiss = { sheet = null },
                onRename = { sheet = EkkoSheet.RenamePlaylist(open.playlist) },
                onDelete = {
                    sheet = null
                    confirmDeletePlaylist = open.playlist
                },
            )
            is EkkoSheet.RenamePlaylist -> NameSheet(
                title = "Endre navn",
                initial = open.playlist.name,
                confirm = "Lagre",
                onDismiss = { sheet = null },
                onConfirm = { name ->
                    store.renamePlaylist(open.playlist.id, name)
                    sheet = null
                },
            )
            is EkkoSheet.PickClips -> PickClipsSheet(
                library = store.library,
                alreadyIn = open.playlist.clipIds.toSet(),
                onDismiss = { sheet = null },
                onAdd = { ids ->
                    store.addToPlaylist(open.playlist.id, ids)
                    sheet = null
                },
            )
        }

        confirmDelete?.let { clip ->
            ConfirmDialog(
                title = "Slette «${clip.title}»?",
                text = "Lyden blir borte fra Ekko og fra alle spillelistene.",
                confirm = "Slett",
                onDismiss = { confirmDelete = null },
                onConfirm = {
                    Haptics.warning()
                    player.forget(clip.id)
                    store.deleteClip(clip.id)
                    confirmDelete = null
                },
            )
        }
        confirmDeletePlaylist?.let { playlist ->
            ConfirmDialog(
                title = "Slette «${playlist.name}»?",
                text = "Lydene blir liggende i Ekko.",
                confirm = "Slett",
                onDismiss = { confirmDeletePlaylist = null },
                onConfirm = {
                    Haptics.warning()
                    store.deletePlaylist(playlist.id)
                    confirmDeletePlaylist = null
                    destination = EkkoDestination.Home
                },
            )
        }
    }
}

/** Navnet på det som spilles: spillelisten slik den heter nå, eller «Alle lyder». */
internal fun sourceName(player: EkkoPlayer, store: EkkoStore): String {
    val source = player.source ?: return ""
    return store.playlist(source.id)?.name ?: source.name
}

/** Plassen nederst i listene, så minispilleren ikke dekker den siste raden. */
@Composable
internal fun bottomSpace() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 112.dp

@Composable
fun EkkoHome(
    store: EkkoStore,
    player: EkkoPlayer,
    tab: EkkoTab,
    onTab: (EkkoTab) -> Unit,
    filter: ClipFilter,
    onFilter: (ClipFilter) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onMore: (Clip) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    val library = store.library
    val shown = library.filter(filter::matches)
    val source = if (filter.isActive) EkkoPlayer.QueueSource("filter", filter.label) else EkkoPlayer.QueueSource.All
    val people = people(library)
    val listState = rememberLazyListState()
    // Når den store tittelen er scrollet bort, vises en liten tittel i toppen i stedet.
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90 } }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, tween(180))

    Box(Modifier.fillMaxSize().background(EkkoPalette.Ink)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 52.dp,
                bottom = bottomSpace(),
            ),
        ) {
            item(key = "title") {
                Text(
                    "Ekko",
                    style = MaterialTheme.typography.displaySmall,
                    color = EkkoPalette.Text,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 16.dp).alpha(1f - barAlpha),
                )
            }
            item(key = "search") {
                SearchField(
                    query = filter.query,
                    onQuery = { onFilter(filter.copy(query = it)) },
                    placeholder = if (tab == EkkoTab.Sounds) "Søk etter lyder og personer" else "Søk i spillelister",
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item(key = "tabs") {
                SegmentedTabs(
                    options = listOf("Lyder", "Spillelister"),
                    selected = tab.ordinal,
                    onSelect = { onTab(EkkoTab.entries[it]) },
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 14.dp),
                )
            }
            when (tab) {
                EkkoTab.Sounds -> {
                    if (people.isNotEmpty()) {
                        item(key = "people") {
                            PersonChips(
                                people = people,
                                selected = filter.person,
                                onSelect = { onFilter(filter.copy(person = it)) },
                                modifier = Modifier.padding(bottom = 14.dp),
                            )
                        }
                    }
                    when {
                        library.isEmpty() -> item(key = "empty") { EmptyLibrary(onAdd) }
                        shown.isEmpty() -> item(key = "none") { NoMatches(onClear = { onFilter(ClipFilter()) }) }
                        else -> {
                            item(key = "header") {
                                PlayHeader(
                                    summary = formatCount(shown.size, shown.sumOf { it.lengthSeconds }),
                                    shuffle = store.data.shuffle,
                                    isPlayingThis = player.isPlaying && player.isFrom(source),
                                    onShuffle = player::toggleShuffle,
                                    onPlay = { player.playOrPause(shown, source) },
                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, bottom = 6.dp).animateItem(),
                                )
                            }
                            items(shown, key = { it.id }) { clip ->
                                Box(Modifier.animateItem()) {
                                    ClipRow(
                                        clip = clip,
                                        isCurrent = player.current?.id == clip.id,
                                        isPlaying = player.isPlaying,
                                        onClick = { player.playClip(shown, clip, source) },
                                        onMore = { onMore(clip) },
                                    )
                                }
                            }
                        }
                    }
                }
                EkkoTab.Playlists -> {
                    val playlists = store.data.playlists.sortedByDescending { it.createdAtMillis }.filter(filter::matches)
                    if (filter.query.isBlank()) item(key = "new") { NewPlaylistRow(onNewPlaylist) }
                    if (playlists.isEmpty() && filter.query.isNotBlank()) {
                        item(key = "none") { NoMatches(onClear = { onFilter(filter.copy(query = "")) }) }
                    }
                    items(playlists, key = { it.id }) { playlist ->
                        Box(Modifier.animateItem()) {
                            PlaylistRow(playlist, store.clipsIn(playlist), onClick = { onOpenPlaylist(playlist) })
                        }
                    }
                }
            }
        }

        // Toppen ligger over listen, og blir mørk med en liten tittel når man har scrollet.
        Box(
            Modifier
                .fillMaxWidth()
                .background(EkkoPalette.Ink.copy(alpha = 0.94f * barAlpha))
                .statusBarsPadding()
                .height(52.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 16.dp),
            ) {
                BackButton(onBack, color = EkkoPalette.Lime)
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.Add, "Legg til lyd", onAdd, size = 36.dp, background = EkkoPalette.Lime, tint = EkkoPalette.OnLime, iconSize = 22.dp)
            }
            Text(
                "Ekko",
                style = MaterialTheme.typography.titleMedium,
                color = EkkoPalette.Text,
                modifier = Modifier.align(Alignment.Center).alpha(barAlpha),
            )
            Hairline(Modifier.align(Alignment.BottomCenter).alpha(barAlpha))
        }
    }
}

/** Velg mellom lyder og spillelister. Markøren glir over til det som velges. */
@Composable
private fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(CircleShape)
            .background(EkkoPalette.Surface)
            .padding(3.dp),
    ) {
        val width = maxWidth / options.size
        val offset by animateDpAsState(width * selected, spring(dampingRatio = 0.78f, stiffness = 520f))
        Box(Modifier.offset(x = offset).width(width).fillMaxHeight().clip(CircleShape).background(EkkoPalette.Text))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val color by animateColorAsState(if (index == selected) EkkoPalette.Ink else EkkoPalette.TextDim)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable({ if (index != selected) onSelect(index) }, pressedScale = 0.97f, haptic = Haptics::select),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}

/** Lar innholdet gå [amount] ut over kantene på hver side, forbi luften i listen. */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** «Alle», og én knapp per person som sier noe i lydene. */
@Composable
private fun PersonChips(people: List<String>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Går helt ut til kanten av skjermen, så knappene ikke kuttes før kanten når man blar.
        modifier = modifier.bleed(12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Chip("Alle", selected == null) { onSelect(null) }
        people.forEach { person ->
            Chip(person, person.equals(selected, ignoreCase = true)) {
                onSelect(if (person.equals(selected, ignoreCase = true)) null else person)
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) EkkoPalette.Lime else EkkoPalette.Surface)
    val color by animateColorAsState(if (selected) EkkoPalette.OnLime else EkkoPalette.Text)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .pressable(onClick, pressedScale = 0.94f, haptic = Haptics::select)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun NoMatches(onClear: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = EkkoPalette.TextDim, modifier = Modifier.size(36.dp))
        Text("Ingen treff", style = MaterialTheme.typography.titleLarge, color = EkkoPalette.Text)
        Text("Prøv et annet ord, eller en annen person.", style = MaterialTheme.typography.bodyMedium, color = EkkoPalette.TextDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        SecondaryButton("Vis alle", onClear, modifier = Modifier.height(44.dp))
    }
}

/** «12 lyder · 3 min», med knapper for tilfeldig rekkefølge og avspilling. */
@Composable
fun PlayHeader(
    summary: String,
    shuffle: Boolean,
    isPlayingThis: Boolean,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = EkkoPalette.TextDim, modifier = Modifier.weight(1f))
        ShuffleButton(shuffle, onShuffle)
        RoundIconButton(
            if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (isPlayingThis) "Pause" else "Spill av",
            onPlay,
            size = 52.dp,
            background = EkkoPalette.Lime,
            tint = EkkoPalette.OnLime,
            iconSize = 30.dp,
        )
    }
}

/** Tilfeldig rekkefølge av og på. Lime når den er på. */
@Composable
fun ShuffleButton(on: Boolean, onClick: () -> Unit, size: Dp = 44.dp) {
    RoundIconButton(
        Icons.Rounded.Shuffle,
        if (on) "Slå av tilfeldig rekkefølge" else "Spill i tilfeldig rekkefølge",
        onClick,
        size = size,
        background = if (on) EkkoPalette.Lime.copy(alpha = 0.14f) else EkkoPalette.Surface,
        tint = if (on) EkkoPalette.Lime else EkkoPalette.TextDim,
        iconSize = size * 0.5f,
        haptic = Haptics::select,
    )
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
    ) {
        Image(painterResource(Res.drawable.ekko_logo), contentDescription = null, modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(8.dp))
        Text("Ingen lyder ennå", style = MaterialTheme.typography.titleLarge, color = EkkoPalette.Text)
        Text(
            "Lim inn en lenke til en YouTube Short, en video på X eller en Spotlight, eller hent lyden fra en video på telefonen.",
            style = MaterialTheme.typography.bodyMedium,
            color = EkkoPalette.TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Legg til lyd", onAdd, icon = Icons.Rounded.Add)
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(onClick, pressedScale = 0.97f).padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(EkkoPalette.Surface),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = EkkoPalette.Lime, modifier = Modifier.size(28.dp))
        }
        Text("Ny spilleliste", style = MaterialTheme.typography.titleMedium, color = EkkoPalette.Text)
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, clips: List<Clip>, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(onClick, pressedScale = 0.97f).padding(8.dp),
    ) {
        PlaylistCover(playlist.id, clips, 60.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = EkkoPalette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatCount(clips.size, clips.sumOf { it.lengthSeconds }), style = MaterialTheme.typography.bodyMedium, color = EkkoPalette.TextDim)
        }
    }
}

/** Lyden som spilles, nederst på skjermen. Trykk for å åpne hele spilleren. */
@Composable
fun MiniPlayer(clip: Clip, player: EkkoPlayer, store: EkkoStore, onOpen: () -> Unit) {
    val progress = if (clip.lengthSeconds > 0) (player.position / clip.lengthSeconds).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(20.dp))
            .background(EkkoPalette.SurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .pressable(onOpen, pressedScale = 0.98f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
        ) {
            ClipCover(clip, 44.dp, corner = 10.dp, bars = 7)
            Column(Modifier.weight(1f)) {
                Text(clip.title, style = MaterialTheme.typography.titleSmall, color = EkkoPalette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    clip.artist.ifBlank { sourceName(player, store) },
                    style = MaterialTheme.typography.bodySmall,
                    color = EkkoPalette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconButton(
                if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (player.isPlaying) "Pause" else "Spill av",
                player::toggle,
                size = 42.dp,
                background = EkkoPalette.SurfaceHigh,
                iconSize = 28.dp,
            )
            RoundIconButton(Icons.Rounded.SkipNext, "Neste", player::next, size = 42.dp, background = EkkoPalette.SurfaceHigh, iconSize = 26.dp)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp).clip(CircleShape).background(EkkoPalette.Line)) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(EkkoPalette.Lime))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(EkkoPalette.SurfaceHigh)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = EkkoPalette.Danger, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = EkkoPalette.Text, modifier = Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.Close, "Lukk", onDismiss, size = 32.dp, background = EkkoPalette.SurfaceHigh, iconSize = 18.dp)
    }
}
