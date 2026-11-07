package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.openconnect.OpenConnectBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class OpenConnectSettingsActivity : ProfileSettingsActivity<OpenConnectBean>() {

    override fun createEntity() = OpenConnectBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val server = pbm.add(PreferenceBinding(Type.Text, "server"))
    private val flavor = pbm.add(PreferenceBinding(Type.Text, "flavor"))
    private val username = pbm.add(PreferenceBinding(Type.Text, "username"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val authGroup = pbm.add(PreferenceBinding(Type.Text, "authGroup"))
    private val reportedOS = pbm.add(PreferenceBinding(Type.Text, "reportedOS"))
    private val userAgent = pbm.add(PreferenceBinding(Type.Text, "userAgent"))
    private val localHostname = pbm.add(PreferenceBinding(Type.Text, "localHostname"))
    private val allowInsecureCrypto = pbm.add(PreferenceBinding(Type.Bool, "allowInsecureCrypto"))

    private val tlsInsecure = pbm.add(PreferenceBinding(Type.Bool, "tlsInsecure"))
    private val tlsServerName = pbm.add(PreferenceBinding(Type.Text, "tlsServerName"))
    private val tlsPeerFingerprint = pbm.add(PreferenceBinding(Type.Text, "tlsPeerFingerprint"))
    private val certificateAuthority = pbm.add(PreferenceBinding(Type.Text, "certificateAuthority"))
    private val clientCertificate = pbm.add(PreferenceBinding(Type.Text, "clientCertificate"))
    private val clientKey = pbm.add(PreferenceBinding(Type.Text, "clientKey"))
    private val clientKeyPassword = pbm.add(PreferenceBinding(Type.Text, "clientKeyPassword"))
    private val mcaCertificate = pbm.add(PreferenceBinding(Type.Text, "mcaCertificate"))
    private val mcaKey = pbm.add(PreferenceBinding(Type.Text, "mcaKey"))
    private val mcaKeyPassword = pbm.add(PreferenceBinding(Type.Text, "mcaKeyPassword"))

    override fun OpenConnectBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun OpenConnectBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.openconnect_preferences)
        pbm.setPreferenceFragment(this)

        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
        clientKeyPassword.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
        mcaKeyPassword.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }

        val authCategory = findPreference<PreferenceCategory>("category_openconnect_auth")
        val currentBean = (proxyEntity?.requireBean() as? OpenConnectBean)
        if (currentBean?.formEntries?.isNotEmpty() == true) {
            authCategory?.isVisible = true
            findPreference<Preference>("clearFormEntries")?.setOnPreferenceClickListener {
                currentBean.formEntries.clear()
                authCategory?.isVisible = false
                Snackbar.make(requireView(), android.R.string.ok, Snackbar.LENGTH_SHORT).show()
                true
            }
        } else {
            authCategory?.isVisible = false
        }
    }
}
