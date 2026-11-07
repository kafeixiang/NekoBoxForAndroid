package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class MieruSettingsActivity : ProfileSettingsActivity<MieruBean>() {

    override fun createEntity() = MieruBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val serverPorts = pbm.add(PreferenceBinding(Type.Text, "serverPorts"))
    private val protocol = pbm.add(PreferenceBinding(Type.Text, "protocol"))
    private val username = pbm.add(PreferenceBinding(Type.Text, "username"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val mtu = pbm.add(PreferenceBinding(Type.TextToInt, "mtu"))
    private val multiplexing = pbm.add(PreferenceBinding(Type.Text, "multiplexing"))
    private val trafficPattern = pbm.add(PreferenceBinding(Type.Text, "trafficPattern"))

    override fun MieruBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun MieruBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.mieru_preferences)
        pbm.setPreferenceFragment(this)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
        val protocolPref = protocol.preference as SimpleMenuPreference
        val mtuPref = mtu.preference as EditTextPreference
        mtuPref.isVisible = protocolPref.value == "UDP"
        protocolPref.setOnPreferenceChangeListener { _, newValue ->
            mtuPref.isVisible = newValue == "UDP"
            true
        }
    }

}
