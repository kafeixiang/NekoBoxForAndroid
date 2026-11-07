package com.github.shadowsocks.plugin.shadowtls

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.shadowsocks.plugin.PluginOptions
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity.PasswordSummaryProvider
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class ConfigFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private val host by lazy { findPreference<EditTextPreference>("host")!! }
    private val password by lazy { findPreference<EditTextPreference>("password")!! }
    private val version by lazy { findPreference<SimpleMenuPreference>("version")!! }
    private val fingerprint by lazy { findPreference<SimpleMenuPreference>("fingerprint")!! }

    val options get() = PluginOptions().apply {
        putWithDefault("host", host.text, "")
        putWithDefault("password", password.text, "")
        putWithDefault("version", version.value, "3")
        if (!fingerprint.value.isNullOrBlank()) {
            putWithDefault("fingerprint", fingerprint.value, "")
        }
    }

    fun onInitializePluginOptions(options: PluginOptions) {
        host.text = options["host"] ?: options["sni"] ?: ""
        password.text = options["password"] ?: ""
        version.value = options["version"] ?: "3"
        fingerprint.value = options["fingerprint"] ?: options["client-fingerprint"] ?: ""
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.shadowtls_plugin_config)
        host.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_TEXT_VARIATION_URI
            it.setSelection(it.text.length)
        }
        host.onPreferenceChangeListener = this
        password.onPreferenceChangeListener = this
        password.summaryProvider = PasswordSummaryProvider
        version.onPreferenceChangeListener = this
        fingerprint.onPreferenceChangeListener = this
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        val oldValue = when (preference) {
            is EditTextPreference -> preference.text
            is SimpleMenuPreference -> preference.value
            else -> null
        }
        if (oldValue != newValue) {
            (requireActivity() as? ConfigActivity)?.let {
                it.dirty = true
            }
        }
        return true
    }
}
