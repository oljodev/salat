package no.jodal.salat

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import no.jodal.salat.apps.Brems
import no.jodal.salat.apps.Ekko
import no.jodal.salat.apps.Fornyelse
import no.jodal.salat.apps.Kompis
import no.jodal.salat.apps.Rise
import no.jodal.salat.apps.Varseltest
import org.jetbrains.compose.resources.DrawableResource

/** En «app inni appen» som vises på forsiden. */
class MiniApp(
    val title: String,
    val description: String,
    val icon: MiniAppIcon,
    /** Fargen på kortet og toppen av mini-appen. */
    val color: Color,
    /** Om kortet på forsiden bruker [color] som den er, uten å blandes med bakgrunnen. Teksten blir da hvit. */
    val solidTile: Boolean = false,
    val section: Section = Section.Apps,
    /** Om mini-appen tegner sin egen topplinje med tilbakeknapp, i stedet for Salat sin. */
    val customTopBar: Boolean = false,
    /** Innholdet i mini-appen, eller `null` hvis den ikke er laget ennå. */
    val content: (@Composable (MiniAppContext) -> Unit)? = null,
) {
    enum class Section { Apps, Tools }
}

/** Det en mini-app får tilgang til. */
class MiniAppContext(
    val bridge: NativeBridge,
    /** Går tilbake til forsiden. */
    val onBack: () -> Unit,
)

sealed interface MiniAppIcon {
    /**
     * En egen logo, som Rise sin risbolle. Er logoen selv et ferdig app-ikon, som Kompis sin,
     * fyller den hele firkanten ([fullBleed]) i stedet for å ligge på mini-appens farge.
     */
    data class Logo(val image: DrawableResource, val fullBleed: Boolean = false) : MiniAppIcon

    /** Et ikon fra ikonbiblioteket. */
    data class Symbol(val vector: ImageVector) : MiniAppIcon
}

/** Alle mini-appene, i den rekkefølgen de vises på forsiden. Nye mini-apper legges til her. */
val miniApps = listOf(
    Rise,
    Kompis,
    Ekko,
    Brems,
    Fornyelse,
    Varseltest,
)
