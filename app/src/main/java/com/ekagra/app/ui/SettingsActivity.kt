package com.ekagra.app.ui



import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ekagra.app.R
import com.ekagra.app.adapter.SettingsAdapter
import com.ekagra.app.databinding.ActivitySettingsBinding
import com.ekagra.app.model.SettingsItem
import com.ekagra.app.utils.PreferencesManager
import com.ekagra.app.utils.ThemeMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupToolbar()
        setupRecyclerView()
        loadSettings()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SettingsAdapter(onActionClick = ::onActionClicked)
        binding.recyclerSettings.layoutManager = LinearLayoutManager(this)
        binding.recyclerSettings.adapter = adapter
    }

    private fun loadSettings() {
        val currentMode = PreferencesManager.getThemeMode(this)
        adapter.submitList(buildSettingsList(currentMode))
    }

    /**
     * Builds the flat settings list from sections.
     * Add new sections and rows here — the adapter handles the rest.
     */
    private fun buildSettingsList(currentTheme: ThemeMode): List<SettingsItem> = listOf(

        // ── Appearance ──────────────────────────────────────────────────────
        SettingsItem.Header(getString(R.string.settings_section_appearance)),
        SettingsItem.ActionRow(
            id       = "theme",
            title    = getString(R.string.settings_theme_title),
            subtitle = getString(R.string.settings_theme_subtitle),
            value    = currentTheme.displayName(this)
        )

        // ── Future sections (uncomment and expand as needed) ────────────────
        // SettingsItem.Header(getString(R.string.settings_section_behavior)),
        // SettingsItem.ActionRow(id = "cooldown_duration", title = "Cooldown Duration", value = "5s"),

        // SettingsItem.Header(getString(R.string.settings_section_permissions)),
        // SettingsItem.ActionRow(id = "accessibility", title = "Accessibility Service"),
    )

    /**
     * Routes taps to the correct handler.
     * To add a new setting: add a new `when` branch matching [SettingsItem.ActionRow.id].
     */
    private fun onActionClicked(item: SettingsItem.ActionRow) {
        when (item.id) {
            "theme" -> showThemePicker()
        }
    }

    private fun showThemePicker() {
        val modes   = ThemeMode.entries.toTypedArray()
        val labels  = modes.map { it.displayName(this) }.toTypedArray()
        val current = modes.indexOf(PreferencesManager.getThemeMode(this))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val selected = modes[which]
                PreferencesManager.setThemeMode(this, selected)
                AppCompatDelegate.setDefaultNightMode(selected.toNightMode())
                dialog.dismiss()
                // Refresh the displayed value after selection
                loadSettings()
            }
            .show()
    }
}