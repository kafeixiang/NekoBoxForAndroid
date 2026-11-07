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
        DataStore.serverProtocolInt = protocol ?: HysteriaBean.PROTOCOL_UDP
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
        DataStore.serverHopIntervalMax = hopIntervalMax ?: 0
        DataStore.serverPinnedCertChainSha256 = pinSHA256
        DataStore.serverHysteriaBbrProfile = bbrProfile
        DataStore.serverHysteriaDisableChromeParrot = disableChromeParrot
        DataStore.serverHysteriaObfsType = obfsType
        DataStore.serverHysteriaRealmEnabled = enableRealm ?: false
        DataStore.serverHysteriaRealmServerURL = realmServerURL ?: ""
        DataStore.serverHysteriaRealmToken = realmToken ?: ""
        DataStore.serverHysteriaRealmId = realmId ?: ""
        DataStore.serverHysteriaRealmStunServers = realmStunServers ?: ""
        DataStore.serverHysteriaRealmIPVersion = realmIPVersion ?: 0
        DataStore.serverHysteriaRealmPortMapping = realmPortMapping ?: false
        DataStore.serverHysteriaRealmPortMappingTimeout = realmPortMappingTimeout ?: 10
        DataStore.serverHysteriaRealmPortMappingLifetime = realmPortMappingLifetime ?: 600
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
        hopIntervalMax = DataStore.serverHopIntervalMax
        pinSHA256 = DataStore.serverPinnedCertChainSha256
        bbrProfile = DataStore.serverHysteriaBbrProfile
        disableChromeParrot = DataStore.serverHysteriaDisableChromeParrot
        obfsType = DataStore.serverHysteriaObfsType
        enableRealm = DataStore.serverHysteriaRealmEnabled
        realmServerURL = DataStore.serverHysteriaRealmServerURL
        realmToken = DataStore.serverHysteriaRealmToken
        realmId = DataStore.serverHysteriaRealmId
        realmStunServers = DataStore.serverHysteriaRealmStunServers
        realmIPVersion = DataStore.serverHysteriaRealmIPVersion
        realmPortMapping = DataStore.serverHysteriaRealmPortMapping
        realmPortMappingTimeout = DataStore.serverHysteriaRealmPortMappingTimeout
        realmPortMappingLifetime = DataStore.serverHysteriaRealmPortMappingLifetime
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

        val realmCat = findPreference<androidx.preference.PreferenceCategory>("hysteriaRealmCat")
        val realmEnabled = findPreference<SwitchPreference>(Key.SERVER_HYSTERIA_REALM_ENABLED)
        val realmServerURL = findPreference<EditTextPreference>(Key.SERVER_HYSTERIA_REALM_SERVER_URL)
        val realmId = findPreference<EditTextPreference>(Key.SERVER_HYSTERIA_REALM_ID)
        val realmToken = findPreference<EditTextPreference>(Key.SERVER_HYSTERIA_REALM_TOKEN)
        val realmStun = findPreference<EditTextPreference>(Key.SERVER_HYSTERIA_REALM_STUN_SERVERS)
        val realmIPVersion = findPreference<SimpleMenuPreference>(Key.SERVER_HYSTERIA_REALM_IP_VERSION)
        val realmPortMapping = findPreference<SwitchPreference>(Key.SERVER_HYSTERIA_REALM_PORT_MAPPING)
        val realmPortMappingTimeout = findPreference<EditTextPreference>(Key.SERVER_HYSTERIA_REALM_PORT_MAPPING_TIMEOUT)
        val realmPortMappingLifetime = findPreference<EditTextPreference>(Key.SERVER_HYSTERIA_REALM_PORT_MAPPING_LIFETIME)

        realmToken?.summaryProvider = PasswordSummaryProvider
        realmPortMappingTimeout?.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        realmPortMappingLifetime?.setOnBindEditTextListener(EditTextPreferenceModifiers.Number)

        fun updateRealmVisibility(isV2: Boolean, isRealm: Boolean) {
            realmCat?.isVisible = isV2
            if (!isV2) return
            realmServerURL?.isVisible = isRealm
            realmId?.isVisible = isRealm
            realmToken?.isVisible = isRealm
            realmStun?.isVisible = isRealm
            realmIPVersion?.isVisible = isRealm
            realmPortMapping?.isVisible = isRealm
            val pm = isRealm && (realmPortMapping?.isChecked == true)
            realmPortMappingTimeout?.isVisible = pm
            realmPortMappingLifetime?.isVisible = pm
        }

        realmEnabled?.setOnPreferenceChangeListener { _, newValue ->
            updateRealmVisibility(DataStore.protocolVersion == 2, newValue as Boolean)
            true
        }
        realmPortMapping?.setOnPreferenceChangeListener { _, newValue ->
            val pm = (newValue as Boolean) && (realmEnabled?.isChecked == true)
            realmPortMappingTimeout?.isVisible = pm
            realmPortMappingLifetime?.isVisible = pm
            true
        }

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
            findPreference<EditTextPreference>(Key.SERVER_HOP_INTERVAL_MAX)?.isVisible = isV2

            updateRealmVisibility(isV2, realmEnabled?.isChecked == true)
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
        findPreference<EditTextPreference>(Key.SERVER_HOP_INTERVAL_MAX)?.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }

}
