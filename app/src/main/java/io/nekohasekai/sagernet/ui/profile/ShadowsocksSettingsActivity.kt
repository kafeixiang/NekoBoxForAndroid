package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.shadowsocks.plugin.PluginContract
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class ShadowsocksSettingsActivity : ProfileSettingsActivity<ShadowsocksBean>() {

    override fun createEntity() = ShadowsocksBean()

    private val pbm = PreferenceBindingManager()
    private val name = pbm.add(PreferenceBinding(Type.Text, "name"))
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val password = pbm.add(PreferenceBinding(Type.Text, "password"))
    private val method = pbm.add(PreferenceBinding(Type.Text, "method"))
    private val pluginName = pbm.add(PreferenceBinding(Type.Text, "pluginName"))
    private val pluginConfig = pbm.add(PreferenceBinding(Type.Text, "pluginConfig"))
    private val sUoT = pbm.add(PreferenceBinding(Type.Bool, "sUoT"))

    private fun getConfigKey(name: String) = "pluginConfig_$name"

    private val configurePlugin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(PluginContract.EXTRA_OPTIONS)?.let {
                val pn = (pluginName.preference as? SimpleMenuPreference)?.value ?: pluginName.readStringFromCache()
                DataStore.profileCacheStore.putString(pluginConfig.cacheName, it)
                if (pn.isNotBlank()) {
                    DataStore.profileCacheStore.putString(getConfigKey(pn), it)
                }
                (pluginConfig.preference as? EditTextPreference)?.text = it
            }
        }
    }

    override fun ShadowsocksBean.init() {
        // 1. 先清理缓存中的脏数据，确保本次编辑环境纯净
        DataStore.profileCacheStore.remove(getConfigKey("v2ray-plugin"))
        DataStore.profileCacheStore.remove(getConfigKey("obfs-local"))

        // 2. 执行标准的初始化绑定
        pbm.writeToCacheAll(this)

        // 3. 解析并备份当前插件配置
        val pn = plugin.substringBefore(";")
        val pc = plugin.substringAfter(";")
        DataStore.profileCacheStore.putString("pluginName", pn)
        DataStore.profileCacheStore.putString("pluginConfig", pc)
        if (pn.isNotBlank()) {
            DataStore.profileCacheStore.putString(getConfigKey(pn), pc)
        }
    }

    override fun ShadowsocksBean.serialize() {
        // serialize 应该保持极简，只负责数据同步，不操作不相关的缓存键
        pbm.fromCacheAll(this)

        val pn = pluginName.readStringFromCache()
        val pc = pluginConfig.readStringFromCache()
        plugin = if (pn.isNotBlank()) "$pn;$pc" else ""
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.shadowsocks_preferences)
        pbm.setPreferenceFragment(this)

        serverPort.preference.apply {
            this as EditTextPreference
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        password.preference.apply {
            this as EditTextPreference
            summaryProvider = PasswordSummaryProvider
        }

        fun updatePluginConfigState(pn: String) {
            pluginConfig.preference.isEnabled = pn.isNotBlank() && pn != "none"
        }

        updatePluginConfigState(pluginName.readStringFromCache())

        pluginName.preference.setOnPreferenceChangeListener { _, newValue ->
            val newName = newValue as String
            updatePluginConfigState(newName)
            
            val savedConfig = DataStore.profileCacheStore.getString(getConfigKey(newName)) ?: ""
            DataStore.profileCacheStore.putString(pluginConfig.cacheName, savedConfig)
            (pluginConfig.preference as? EditTextPreference)?.text = savedConfig
            true
        }

        pluginConfig.preference.setOnPreferenceChangeListener { _, newValue ->
            val pn = (pluginName.preference as? SimpleMenuPreference)?.value ?: pluginName.readStringFromCache()
            if (pn.isNotBlank()) {
                DataStore.profileCacheStore.putString(getConfigKey(pn), newValue as String)
            }
            true
        }
    }

    override fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        if (preference.key == "pluginConfig") {
            val pn = (pluginName.preference as? SimpleMenuPreference)?.value ?: pluginName.readStringFromCache()
            val pc = pluginConfig.readStringFromCache()

            val intent = when (pn) {
                "v2ray-plugin" -> Intent().setClassName(requireContext().packageName, "com.github.shadowsocks.plugin.v2ray.ConfigActivity")
                "obfs-local" -> Intent().setClassName(requireContext().packageName, "com.github.shadowsocks.plugin.obfs_local.ConfigActivity")
                else -> null
            }

            if (intent != null) {
                intent.putExtra(PluginContract.EXTRA_OPTIONS, pc)
                configurePlugin.launch(intent)
                return true
            }
        }
        return false
    }

}
