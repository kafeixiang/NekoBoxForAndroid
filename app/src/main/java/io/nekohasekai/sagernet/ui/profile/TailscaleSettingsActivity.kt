package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.tailscale.TailscaleBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class TailscaleSettingsActivity : ProfileSettingsActivity<TailscaleBean>() {

    override fun createEntity() = TailscaleBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val authKey = pbm.add(PreferenceBinding(Type.Text, "authKey"))
    private val controlURL = pbm.add(PreferenceBinding(Type.Text, "controlURL"))
    private val hostname = pbm.add(PreferenceBinding(Type.Text, "hostname"))
    private val stateDirectory = pbm.add(PreferenceBinding(Type.Text, "stateDirectory"))
    private val ephemeral = pbm.add(PreferenceBinding(Type.Bool, "ephemeral"))
    private val acceptRoutes = pbm.add(PreferenceBinding(Type.Bool, "acceptRoutes"))
    private val exitNode = pbm.add(PreferenceBinding(Type.Text, "exitNode"))
    private val exitNodeAllowLANAccess = pbm.add(PreferenceBinding(Type.Bool, "exitNodeAllowLANAccess"))
    private val advertiseRoutes = pbm.add(PreferenceBinding(Type.Text, "advertiseRoutes"))
    private val advertiseFakeDNSRoutes = pbm.add(PreferenceBinding(Type.Bool, "advertiseFakeDNSRoutes"))
    private val advertiseExitNode = pbm.add(PreferenceBinding(Type.Bool, "advertiseExitNode"))
    private val advertiseTags = pbm.add(PreferenceBinding(Type.Text, "advertiseTags"))
    private val listenPort = pbm.add(PreferenceBinding(Type.TextToInt, "listenPort"))

    override fun TailscaleBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun TailscaleBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tailscale_preferences)
        pbm.setPreferenceFragment(this)

        listenPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        authKey.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }
    }
}
