package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.hysteria.HysteriaBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class HysteriaSettingsActivity : ProfileSettingsActivity<HysteriaBean>() {

    override fun createEntity() = HysteriaBean().applyDefaultValues()

    override fun HysteriaBean.init() {
        DataStore.profileName = name
        DataStore.protocolVersion = protocolVersion
        DataStore.serverAddress = serverAddress
        DataStore.serverPorts = serverPorts
        DataStore.serverObfs = obfuscation
        DataStore.serverAuthType = authPayloadType
        DataStore.serverProtocolInt = protocol
        DataStore.serverPassword = authPayload
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCertificates = caText
        DataStore.serverAllowInsecure = allowInsecure
        DataStore.serverUploadSpeed = uploadMbps
        DataStore.serverDownloadSpeed = downloadMbps
        DataStore.serverStreamReceiveWindow = streamReceiveWindow
        DataStore.serverConnectionReceiveWindow = connectionReceiveWindow
        DataStore.serverDisableMtuDiscovery = disableMtuDiscovery
        DataStore.serverHopInterval = hopInterval
        DataStore.serverPinnedCertChainSha256 = pinSHA256
        DataStore.serverHysteriaBbrProfile = bbrProfile
        DataStore.serverHysteriaDisableChromeParrot = disableChromeParrot
        DataStore.serverHysteriaObfsType = obfsType
    }

    override fun HysteriaBean.serialize() {
        name = DataStore.profileName
        protocolVersion = DataStore.protocolVersion
        serverAddress = DataStore.serverAddress
        serverPorts = DataStore.serverPorts
        obfuscation = DataStore.serverObfs
        authPayloadType = DataStore.serverAuthType
        authPayload = DataStore.serverPassword
        protocol = DataStore.serverProtocolInt
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        caText = DataStore.serverCertificates
        allowInsecure = DataStore.serverAllowInsecure
        uploadMbps = DataStore.serverUploadSpeed
        downloadMbps = DataStore.serverDownloadSpeed
        streamReceiveWindow = DataStore.serverStreamReceiveWindow
        connectionReceiveWindow = DataStore.serverConnectionReceiveWindow
        disableMtuDiscovery = DataStore.serverDisableMtuDiscovery
        hopInterval = DataStore.serverHopInterval
        pinSHA256 = DataStore.serverPinnedCertChainSha256
        bbrProfile = DataStore.serverHysteriaBbrProfile
        disableChromeParrot = DataStore.serverHysteriaDisableChromeParrot
        obfsType = DataStore.serverHysteriaObfsType
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.hysteria_preferences)

        val authType = findPreference<SimpleMenuPreference>(Key.SERVER_AUTH_TYPE)!!
        val authPayload = findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!
        authPayload.isVisible = authType.value != "${HysteriaBean.TYPE_NONE}"
        authType.setOnPreferenceChangeListener { _, newValue ->
            authPayload.isVisible = newValue != "${HysteriaBean.TYPE_NONE}"
            true
        }

        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!
        val alpn = findPreference<EditTextPreference>(Key.SERVER_ALPN)!!

        fun updateVersion(v: Int) {
            val isV2 = v == 2
            authPayload.isVisible = true
            authType.isVisible = !isV2
            protocol.isVisible = !isV2
            alpn.isVisible = !isV2
            authPayload.title = resources.getString(if (isV2) R.string.password else R.string.hysteria_auth_payload)

            // Both HY1 and HY2 (sing-box 1.15 QUICOptions) support receive windows and MTU discovery
            findPreference<EditTextPreference>(Key.SERVER_STREAM_RECEIVE_WINDOW)?.isVisible = true
            findPreference<EditTextPreference>(Key.SERVER_CONNECTION_RECEIVE_WINDOW)?.isVisible = true
            findPreference<SwitchPreference>(Key.SERVER_DISABLE_MTU_DISCOVERY)?.isVisible = true

            // HY2 specific sing-box 1.15 features
            findPreference<EditTextPreference>(Key.SERVER_PINNED_CERT_CHAIN_SHA256)?.isVisible = isV2
            findPreference<SimpleMenuPreference>(Key.SERVER_HYSTERIA_BBR_PROFILE)?.isVisible = isV2
            findPreference<SwitchPreference>(Key.SERVER_HYSTERIA_DISABLE_CHROME_PARROT)?.isVisible = isV2
            findPreference<SimpleMenuPreference>(Key.SERVER_HYSTERIA_OBFS_TYPE)?.isVisible = isV2
        }
        findPreference<SimpleMenuPreference>(Key.PROTOCOL_VERSION)!!.setOnPreferenceChangeListener { _, newValue ->
            updateVersion(newValue.toString().toIntOrNull() ?: 1)
            true
        }
        updateVersion(DataStore.protocolVersion)

        findPreference<EditTextPreference>(Key.SERVER_UPLOAD_SPEED)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_DOWNLOAD_SPEED)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_STREAM_RECEIVE_WINDOW)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        findPreference<EditTextPreference>(Key.SERVER_CONNECTION_RECEIVE_WINDOW)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        findPreference<EditTextPreference>(Key.SERVER_OBFS)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }

        findPreference<EditTextPreference>(Key.SERVER_HOP_INTERVAL)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }

}
