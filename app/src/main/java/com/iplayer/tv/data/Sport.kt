package com.iplayer.tv.data

/** Stable IDs are persisted; labels can change independently. */
enum class Sport(val label: String) {
    FOOTBALL("Football"), BASKETBALL("Basket"), TENNIS("Tennis"),
    F1("Formule 1"), MMA("MMA"), RUGBY("Rugby"), CYCLING("Cyclisme"),
    HANDBALL("Handball"), BOXING("Boxe"), MOTORCYCLING("MotoGP"),
    VOLLEYBALL("Volley"), ICE_HOCKEY("Hockey sur glace");

    companion object {
        val defaults = setOf(FOOTBALL, BASKETBALL, TENNIS, F1, MMA)
    }
}
