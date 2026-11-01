package io.github.eyalshimshi.shidur.ui

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class BrowseRoute(val collectionId: String? = null)

@Serializable
object FavoritesRoute

@Serializable
object SearchRoute

@Serializable
object SettingsRoute

@Serializable
data class AddStationRoute(val url: String? = null)

/** Navigation requested from outside the UI: notification/widget taps and shidur:// links. */
sealed interface NavigationRequest {
    data object OpenPlayer : NavigationRequest

    data class OpenStation(val stationId: String) : NavigationRequest

    /** From the app's launcher shortcuts: start playing right away. */
    data class PlayStation(val stationId: String) : NavigationRequest
}
