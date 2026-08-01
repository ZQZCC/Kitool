package ka.kitool.settings

import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.widget.Toast
import ka.kitool.R
import ka.kitool.search.CUSTOM_SEARCH_ENGINE_ID
import ka.kitool.search.SearchUrl
import ka.kitool.search.searchEngineIds
import ka.kitool.search.searchEngineTitles

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class SettingsActivity :
    PreferenceActivity(),
    Preference.OnPreferenceChangeListener {
    private var searchEngine: ListPreference? = null
    private var customSearchTemplate: EditTextPreference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager.sharedPreferencesName = SettingsStore.FILE_NAME
        addPreferencesFromResource(R.xml.settings)

        val searchEngine =
            preferenceScreen.findPreference(SettingsStore.KEY_SEARCH_ENGINE) as ListPreference
        val customSearchTemplate =
            preferenceScreen.findPreference(SettingsStore.KEY_CUSTOM_SEARCH_TEMPLATE)
                as EditTextPreference
        this.searchEngine = searchEngine
        this.customSearchTemplate = customSearchTemplate

        val engineTitles = searchEngineTitles()
        searchEngine.entries =
            Array(engineTitles.size) { index -> getString(engineTitles[index]) }
        searchEngine.entryValues = searchEngineIds()
        searchEngine.onPreferenceChangeListener = this
        customSearchTemplate.onPreferenceChangeListener = this

        updateEngine(searchEngine.value)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (preference === searchEngine) {
            updateEngine(newValue.toString())
            return true
        }
        if (preference === customSearchTemplate) {
            val template = newValue.toString()
            if (!SearchUrl.isValidTemplate(template)) {
                Toast.makeText(
                        this,
                        R.string.custom_search_template_invalid,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
                return false
            }
            customSearchTemplate?.summary = template
        }
        return true
    }

    private fun updateEngine(engineId: String?) {
        val searchEngine = searchEngine ?: return
        val customSearchTemplate = customSearchTemplate ?: return
        val foundIndex = searchEngine.findIndexOfValue(engineId)
        val index = if (foundIndex >= 0) foundIndex else 0
        searchEngine.summary = searchEngine.entries[index]

        if (engineId?.equals(CUSTOM_SEARCH_ENGINE_ID) == true) {
            preferenceScreen.addPreference(customSearchTemplate)
            customSearchTemplate.summary =
                preferenceScreen.sharedPreferences.getString(
                    SettingsStore.KEY_CUSTOM_SEARCH_TEMPLATE,
                    SettingsStore.DEFAULT_CUSTOM_TEMPLATE,
                )
        } else {
            preferenceScreen.removePreference(customSearchTemplate)
        }
    }
}
