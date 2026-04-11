package com.ekagra.app.model

/** Items that can appear in the settings RecyclerView. */
sealed class SettingsItem {

    /** Non-interactive section label. */
    data class Header(val title: String) : SettingsItem()

    /**
     * Tappable row with a title, optional subtitle, and optional trailing value.
     * Handles navigation, dialogs, or any tap action.
     *
     * Adding a new setting: add an ActionRow to the list in SettingsActivity
     * and handle [id] in onActionClicked().
     */
    data class ActionRow(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val value: String? = null
    ) : SettingsItem()

    // Future slot: data class SwitchRow(val id: String, ..., val isChecked: Boolean)
    // Future slot: data class InfoRow(val message: String)
}