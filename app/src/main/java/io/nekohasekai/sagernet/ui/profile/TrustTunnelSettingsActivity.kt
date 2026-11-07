package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.trusttunnel.TrustTunnelBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class TrustTunnelSettingsActivity : ProfileSettingsActivity<TrustTunnelBean>() {

    override fun createEntity() = TrustTunnelBean().applyDefaultValues()

    override fun TrustTunnelBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverQuic = quic ?: false
        DataStore.serverHealthCheck = healthCheck ?: false
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = certificates
        DataStore.serverAllowInsecure = allowInsecure ?: false
    }

    override fun TrustTunnelBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        quic = DataStore.serverQuic
        healthCheck = DataStore.serverHealthCheck
        alpn = DataStore.serverALPN
        certificates = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.trusttunnel_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }
}
