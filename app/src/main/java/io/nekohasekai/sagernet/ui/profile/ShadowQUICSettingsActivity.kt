package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.shadowquic.ShadowQUICBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues

class ShadowQUICSettingsActivity : ProfileSettingsActivity<ShadowQUICBean>() {

    override fun createEntity() = ShadowQUICBean().applyDefaultValues()

    override fun ShadowQUICBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverSNI = sni
        DataStore.serverALPN = alpn
        DataStore.serverCongestionController = congestionControl
        DataStore.serverUDPOverStream = udpOverStream ?: false
        DataStore.serverReduceRTT = zeroRttHandshake ?: false
        DataStore.serverSunnyQuic = sunnyQuic ?: false
    }

    override fun ShadowQUICBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        sni = DataStore.serverSNI
        alpn = DataStore.serverALPN
        congestionControl = DataStore.serverCongestionController
        udpOverStream = DataStore.serverUDPOverStream
        zeroRttHandshake = DataStore.serverReduceRTT
        sunnyQuic = DataStore.serverSunnyQuic
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowquic_preferences)

        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
    }
}
