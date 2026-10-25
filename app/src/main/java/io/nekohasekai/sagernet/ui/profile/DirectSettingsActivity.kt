package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.fmt.direct.DirectBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class DirectSettingsActivity : ProfileSettingsActivity<DirectBean>() {

    override fun createEntity() = DirectBean().applyDefaultValues()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))

    override fun DirectBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun DirectBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.direct_preferences)
        pbm.setPreferenceFragment(this)
    }

}
