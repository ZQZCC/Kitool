package ka.kitool.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.widget.Toast
import ka.kitool.R
import ka.kitool.search.SearchEngine
import ka.kitool.search.SearchUrl

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SettingsActivity :
    PreferenceActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var preferences: SharedPreferences
    private lateinit var searchEngine: ListPreference
    private lateinit var customSearchTemplate: EditTextPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager.sharedPreferencesName = SettingsStore.FILE_NAME
        addPreferencesFromResource(R.xml.settings)

        searchEngine =
            preferenceScreen.findPreference(SettingsStore.KEY_SEARCH_ENGINE) as ListPreference
        customSearchTemplate =
            preferenceScreen.findPreference(SettingsStore.KEY_CUSTOM_SEARCH_TEMPLATE)
                as EditTextPreference

        val engines = SearchEngine.all
        searchEngine.entries =
            Array(engines.size) { index -> getString(engines[index].titleRes) }
        searchEngine.entryValues =
            Array(engines.size) { index -> engines[index].id }

        customSearchTemplate.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                validate(
                    SearchUrl.isValidTemplate(newValue.toString()),
                    R.string.custom_search_template_invalid,
                )
            }

        preferences = preferenceScreen.sharedPreferences
    }

    override fun onResume() {
        super.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
        updatePreferences()
    }

    override fun onPause() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        updatePreferences()
    }

    private fun updatePreferences() {
        val engine =
            SearchEngine.fromId(
                preferences.getString(SettingsStore.KEY_SEARCH_ENGINE, null)
            )
        searchEngine.summary = getString(engine.titleRes)

        if (engine == SearchEngine.CUSTOM) {
            preferenceScreen.addPreference(customSearchTemplate)
            customSearchTemplate.summary =
                preferences.getString(
                    SettingsStore.KEY_CUSTOM_SEARCH_TEMPLATE,
                    SettingsStore.DEFAULT_CUSTOM_TEMPLATE,
                )
        } else {
            preferenceScreen.removePreference(customSearchTemplate)
        }
    }

    private fun validate(
        valid: Boolean,
        errorMessage: Int,
    ): Boolean {
        if (!valid) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
        return valid
    }
}
