package no.jodal.salat.apps.kurs

/** Poengene for hver vane. Maks [MaxDayPoints] om dagen. */
object Points {
    const val ScreensOff = 45
    const val Reflection = 25
    const val Reading = 25
    const val QualityHigh = 35
    const val QualityMixed = 15
    const val AppLimits = 20
    const val Moved = 25
    const val ShipAndEarn = 25
    const val SleepHigh = 20
    const val SleepMid = 10

    /** En dag med minst så mange poeng er godkjent. */
    const val ApprovedDay = 110

    /** Bonus for en uke med 7 av 7 godkjente dager. */
    const val PerfectWeekBonus = 150

    /** Hver gang totalen passerer et nytt tusen, får man en freeze. */
    const val PointsPerFreeze = 1000
    const val MaxFreezes = 2
}

const val MaxDayPoints = Points.ScreensOff + Points.Reflection + Points.Reading + Points.QualityHigh +
    Points.AppLimits + Points.Moved + Points.ShipAndEarn + Points.SleepHigh

/** Bonusen for Sleep Score: 85 eller mer gir 20, 75–84 gir 10. */
fun sleepBonus(score: Int?): Int = when {
    score == null -> 0
    score >= 85 -> Points.SleepHigh
    score >= 75 -> Points.SleepMid
    else -> 0
}

fun qualityPoints(quality: ScreenQuality?): Int = when (quality) {
    ScreenQuality.High -> Points.QualityHigh
    ScreenQuality.Mixed -> Points.QualityMixed
    else -> 0
}

/** Poengene for dagen, uten ukesbonus. */
val DayLog.points: Int
    get() = (if (screensOffBefore22 == true) Points.ScreensOff else 0) +
        (if (paperReflection == true) Points.Reflection else 0) +
        (if (physicalReading == true) Points.Reading else 0) +
        qualityPoints(screenQuality) +
        (if (keptAppLimits == true) Points.AppLimits else 0) +
        (if (movedOrRested == true) Points.Moved else 0) +
        (if (shipAndEarn == true) Points.ShipAndEarn else 0) +
        sleepBonus(sleepScore)

val DayLog.isApproved: Boolean get() = points >= Points.ApprovedDay
