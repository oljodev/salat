package no.jodal.salat.apps.kompis

import no.jodal.salat.resources.Res
import no.jodal.salat.resources.kompis_bonne
import no.jodal.salat.resources.kompis_drope
import no.jodal.salat.resources.kompis_firkant
import no.jodal.salat.resources.kompis_hjerte
import no.jodal.salat.resources.kompis_pille
import no.jodal.salat.resources.kompis_sky
import no.jodal.salat.resources.kompis_spokelse
import no.jodal.salat.resources.kompis_stjerne
import no.jodal.salat.resources.kompis_trekant
import org.jetbrains.compose.resources.DrawableResource

/** Kompis har mange ansikter. Én figur velges tilfeldig hver gang appen starter, og brukes overalt. */
object KompisMascots {
    private val all = listOf(
        Res.drawable.kompis_drope,
        Res.drawable.kompis_sky,
        Res.drawable.kompis_firkant,
        Res.drawable.kompis_trekant,
        Res.drawable.kompis_hjerte,
        Res.drawable.kompis_stjerne,
        Res.drawable.kompis_pille,
        Res.drawable.kompis_spokelse,
        Res.drawable.kompis_bonne,
    )

    val current: DrawableResource by lazy { all.random() }
}
