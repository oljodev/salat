package no.jodal.salat.apps

import no.jodal.salat.MiniApp
import no.jodal.salat.MiniAppIcon
import no.jodal.salat.apps.kompis.KompisMascots
import no.jodal.salat.apps.kompis.ui.KompisPalette
import no.jodal.salat.apps.kompis.ui.KompisScreen

val Kompis = MiniApp(
    title = "Kompis",
    description = "Snakk med en AI, med stemmen",
    icon = MiniAppIcon.Logo(KompisMascots.current),
    color = KompisPalette.Night,
    customTopBar = true,
    content = { KompisScreen(it.bridge, onBack = it.onBack) },
)
