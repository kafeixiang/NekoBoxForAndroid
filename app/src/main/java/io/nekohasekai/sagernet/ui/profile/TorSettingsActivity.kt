package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.tor.TorBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class TorSettingsActivity : ProfileSettingsActivity<TorBean>() {

    override fun createEntity() = TorBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val executablePath = pbm.add(PreferenceBinding(Type.Text, "executablePath"))
    private val dataDirectory = pbm.add(PreferenceBinding(Type.Text, "dataDirectory"))
    private val extraArgs = pbm.add(PreferenceBinding(Type.Text, "extraArgs"))
    private val torrc = pbm.add(PreferenceBinding(Type.Text, "torrc"))

    override fun TorBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun TorBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.tor_preferences)
        pbm.setPreferenceFragment(this)
    }
}
