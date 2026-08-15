package dev.galex.toyapp.ui

/**
 * Every automation id the toy list owns, in one place.
 *
 * The `*Segment` constants are what the composables hand to `Modifier.automationId`, and the full
 * ids are what `AutomationContext` composes out of them: the id the probe reads back, and the id
 * the navigation map is built from. Rename one here and both sides move together, or the build
 * breaks. A map nobody compiles is a map that lies.
 */
object ToysIds {

    const val Context = "toys"

    const val TitleSegment = "title"
    const val ListSegment = "list"
    const val CardSegment = "card"
    const val NameSegment = "name"
    const val SubtitleSegment = "subtitle"

    const val Title = "${Context}_$TitleSegment"
    const val List = "${Context}_$ListSegment"

    /**
     * A row carries its index, so six cards can never share an id. Pass a real index to tap one,
     * or a placeholder to describe the whole family of them in the navigation map.
     */
    fun card(index: Any): String = "${Context}_index_${index}_$CardSegment"
}

/** Same idea, for the toy detail screen. */
object ToyDetailIds {

    const val Context = "toy_detail"

    const val NameSegment = "name"
    const val MetaSegment = "meta"
    const val DescriptionSegment = "description"
    const val BackButtonSegment = "back_button"

    const val Name = "${Context}_$NameSegment"
    const val Meta = "${Context}_$MetaSegment"
    const val Description = "${Context}_$DescriptionSegment"
    const val BackButton = "${Context}_$BackButtonSegment"
}
