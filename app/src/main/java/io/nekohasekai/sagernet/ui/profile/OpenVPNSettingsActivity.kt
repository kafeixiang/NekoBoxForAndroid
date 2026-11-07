package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.openvpn.OpenVPNBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class OpenVPNSettingsActivity : ProfileSettingsActivity<OpenVPNBean>() {

    override fun createEntity() = OpenVPNBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val network = pbm.add(PreferenceBinding(Type.Text, "network"))
    private val username = pbm.add(PreferenceBinding(Type.Text, "username"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val cipher = pbm.add(PreferenceBinding(Type.Text, "cipher"))
    private val dataCiphers = pbm.add(PreferenceBinding(Type.Text, "dataCiphers"))
    private val auth = pbm.add(PreferenceBinding(Type.Text, "auth"))
    private val compression = pbm.add(PreferenceBinding(Type.Text, "compression"))
    private val redirectGateway = pbm.add(PreferenceBinding(Type.Bool, "redirectGateway"))
    private val mtu = pbm.add(PreferenceBinding(Type.TextToInt, "mtu"))

    private val serverName = pbm.add(PreferenceBinding(Type.Text, "serverName"))
    private val serverNameType = pbm.add(PreferenceBinding(Type.Text, "serverNameType"))
    private val certificate = pbm.add(PreferenceBinding(Type.Text, "certificate"))
    private val clientCertificate = pbm.add(PreferenceBinding(Type.Text, "clientCertificate"))
    private val clientKey = pbm.add(PreferenceBinding(Type.Text, "clientKey"))
    private val peerFingerprint = pbm.add(PreferenceBinding(Type.Text, "peerFingerprint"))
    private val remoteCertificateKU = pbm.add(PreferenceBinding(Type.Text, "remoteCertificateKU"))
    private val remoteCertificateEKU = pbm.add(PreferenceBinding(Type.Text, "remoteCertificateEKU"))
    private val controlWrapType = pbm.add(PreferenceBinding(Type.Text, "controlWrapType"))
    private val controlWrapKey = pbm.add(PreferenceBinding(Type.Text, "controlWrapKey"))
    private val controlWrapDirection = pbm.add(PreferenceBinding(Type.Text, "controlWrapDirection"))

    override fun OpenVPNBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun OpenVPNBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.openvpn_preferences)
        pbm.setPreferenceFragment(this)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
    }
}
