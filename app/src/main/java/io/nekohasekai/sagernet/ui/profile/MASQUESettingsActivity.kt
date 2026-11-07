package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.masque.MASQUEBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class MASQUESettingsActivity : ProfileSettingsActivity<MASQUEBean>() {

    override fun createEntity() = MASQUEBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val username = pbm.add(PreferenceBinding(Type.Text, "username"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val path = pbm.add(PreferenceBinding(Type.Text, "path"))
    private val headers = pbm.add(PreferenceBinding(Type.Text, "headers"))
    private val httpVersion = pbm.add(PreferenceBinding(Type.TextToInt, "httpVersion"))
    private val disableVersionFallback = pbm.add(PreferenceBinding(Type.Bool, "disableVersionFallback"))
    private val mtu = pbm.add(PreferenceBinding(Type.TextToInt, "mtu"))

    private val enableTLS = pbm.add(PreferenceBinding(Type.Bool, "enableTLS"))
    private val serverName = pbm.add(PreferenceBinding(Type.Text, "serverName"))
    private val alpn = pbm.add(PreferenceBinding(Type.Text, "alpn"))
    private val certificates = pbm.add(PreferenceBinding(Type.Text, "certificates"))
    private val allowInsecure = pbm.add(PreferenceBinding(Type.Bool, "allowInsecure"))
    private val disableSNI = pbm.add(PreferenceBinding(Type.Bool, "disableSNI"))
    private val utlsFingerprint = pbm.add(PreferenceBinding(Type.Text, "utlsFingerprint"))

    override fun MASQUEBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun MASQUEBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.masque_preferences)
        pbm.setPreferenceFragment(this)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }

        val tlsPref = enableTLS.preference as SwitchPreference
        val sniPref = serverName.preference
        val alpnPref = alpn.preference
        val certPref = certificates.preference
        val insecurePref = allowInsecure.preference
        val disableSniPref = disableSNI.preference
        val utlsPref = utlsFingerprint.preference

        fun updateTlsFields(enabled: Boolean) {
            sniPref.isVisible = enabled
            alpnPref.isVisible = enabled
            certPref.isVisible = enabled
            insecurePref.isVisible = enabled
            disableSniPref.isVisible = enabled
            utlsPref.isVisible = enabled
        }

        updateTlsFields(tlsPref.isChecked)
        tlsPref.setOnPreferenceChangeListener { _, newValue ->
            updateTlsFields(newValue as Boolean)
            true
        }
    }

}
