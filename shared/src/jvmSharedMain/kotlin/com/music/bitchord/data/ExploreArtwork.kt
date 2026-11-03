package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection

/**
 * Whether a phone Explore tile still needs its cover from the category's
 * playlists. Charts are a standing entry point, not a playlist category, so
 * they keep the colour tile instead of spending a search on a cover.
 */
fun MoodGenre.needsExploreArtwork(): Boolean =
    thumbnailUrl.isNullOrBlank() && browseId != Innertube.CHARTS_BROWSE_ID

/** Applies one settled cover to the exact tile, leaving every other tile alone. */
fun List<MoodGenreSection>.withExploreArtwork(
    browseId: String,
    params: String?,
    artworkUrl: String,
): List<MoodGenreSection> = map { section ->
    section.copy(items = section.items.map { item ->
        if (item.browseId == browseId && item.params == params) {
            item.copy(thumbnailUrl = artworkUrl)
        } else {
            item
        }
    })
}
