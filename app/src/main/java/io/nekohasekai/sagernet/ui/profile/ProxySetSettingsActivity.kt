package io.nekohasekai.sagernet.ui.profile

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.databinding.LayoutAddEntityBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileBinding
import io.nekohasekai.sagernet.fmt.internal.ProxySetBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.ui.ProfileSelectActivity
import moe.matsuri.nb4a.Protocols.getProtocolColor
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class ProxySetSettingsActivity :
    ProfileSettingsActivity<ProxySetBean>(R.layout.layout_chain_settings) {

    override fun createEntity() = ProxySetBean().apply { initializeDefaultValues() }

    val proxyList = ArrayList<ProxyEntity>()

    override fun ProxySetBean.init() {
        DataStore.profileName = name
        DataStore.serverManagement = management
        DataStore.serverInterruptExistConnections = interruptExistConnections
        DataStore.serverTestURL = testURL
        DataStore.serverTestInterval = testInterval
        DataStore.serverIdleTimeout = testIdleTimeout
        DataStore.serverTolerance = testTolerance
        DataStore.serverType = type
        DataStore.serverGroup = groupId
        DataStore.serverGroupFilterNotRegex = groupFilterNotRegex
        DataStore.serverProxies = proxies?.joinToString(",") ?: ""
    }

    override fun ProxySetBean.serialize() {
        name = DataStore.profileName
        management = DataStore.serverManagement
        interruptExistConnections = DataStore.serverInterruptExistConnections
        testURL = DataStore.serverTestURL
        testInterval = DataStore.serverTestInterval
        testIdleTimeout = DataStore.serverIdleTimeout
        testTolerance = DataStore.serverTolerance
        type = DataStore.serverType
        groupId = DataStore.serverGroup
        groupFilterNotRegex = DataStore.serverGroupFilterNotRegex
        proxies = proxyList.map { it.id }
        initializeDefaultValues()
    }

    private var groupPreference: Preference? = null
    private var groupFilterPreference: EditTextPreference? = null

    private fun updateType(type: Int) {
        when (type) {
            ProxySetBean.TYPE_LIST -> {
                groupPreference?.isVisible = false
                groupFilterPreference?.isVisible = false
                if (::configurationList.isInitialized) configurationList.isVisible = true
                if (::itemView.isInitialized) itemView.isVisible = true
            }

            ProxySetBean.TYPE_GROUP -> {
                groupPreference?.isVisible = true
                groupFilterPreference?.isVisible = true
                if (::configurationList.isInitialized) configurationList.isVisible = false
                if (::itemView.isInitialized) itemView.isVisible = false
            }
        }
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.proxy_set_preferences)

        val groupType = findPreference<SimpleMenuPreference>(Key.SERVER_TYPE)!!
        groupPreference = findPreference(Key.SERVER_GROUP)
        groupFilterPreference = findPreference(Key.SERVER_GROUP_FILTER_NOT_REGEX)
        val serverManagement = findPreference<SimpleMenuPreference>(Key.SERVER_MANAGEMENT)!!
        val interruptExistConnections = findPreference<SwitchPreference>(Key.SERVER_INTERRUPT_EXIST_CONNECTIONS)!!
        val testURL = findPreference<EditTextPreference>(Key.SERVER_TEST_URL)!!
        val testInterval = findPreference<EditTextPreference>(Key.SERVER_TEST_INTERVAL)!!
        val idleTimeout = findPreference<EditTextPreference>(Key.SERVER_IDLE_TIMEOUT)!!
        val tolerance = findPreference<EditTextPreference>(Key.SERVER_TOLERANCE)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        updateType(groupType.value?.toIntOrNull() ?: ProxySetBean.TYPE_LIST)
        groupType.setOnPreferenceChangeListener { _, newValue ->
            updateType(newValue.toString().toIntOrNull() ?: ProxySetBean.TYPE_LIST)
            true
        }

        fun updateManagement(management: Int = serverManagement.value?.toIntOrNull() ?: ProxySetBean.MANAGEMENT_SELECTOR) {
            when (management) {
                ProxySetBean.MANAGEMENT_SELECTOR -> {
                    testURL.isVisible = false
                    testInterval.isVisible = false
                    idleTimeout.isVisible = false
                    tolerance.isVisible = false
                    interruptExistConnections.isVisible = true
                }

                ProxySetBean.MANAGEMENT_URLTEST -> {
                    testURL.isVisible = true
                    testInterval.isVisible = true
                    idleTimeout.isVisible = true
                    tolerance.isVisible = true
                    interruptExistConnections.isVisible = true
                }

                ProxySetBean.MANAGEMENT_BALANCER -> {
                    testURL.isVisible = false
                    testInterval.isVisible = true
                    idleTimeout.isVisible = false
                    tolerance.isVisible = false
                    interruptExistConnections.isVisible = false
                }
            }
        }
        updateManagement()
        serverManagement.setOnPreferenceChangeListener { _, newValue ->
            updateManagement(newValue.toString().toIntOrNull() ?: ProxySetBean.MANAGEMENT_SELECTOR)
            true
        }
    }

    lateinit var itemView: View
    lateinit var configurationList: RecyclerView
    lateinit var configurationAdapter: ProxiesAdapter
    lateinit var layoutManager: LinearLayoutManager

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setTitle(R.string.proxy_set_settings)
        itemView = findViewById(R.id.list_cell)
        configurationList = findViewById(R.id.configuration_list)
        layoutManager = FixedLinearLayoutManager(configurationList)
        configurationList.layoutManager = layoutManager
        configurationAdapter = ProxiesAdapter()
        configurationList.adapter = configurationAdapter

        updateType(DataStore.serverType)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) = if (viewHolder is ProfileHolder) {
                super.getSwipeDirs(recyclerView, viewHolder)
            } else 0

            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) = if (viewHolder is ProfileHolder) {
                super.getDragDirs(recyclerView, viewHolder)
            } else 0

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                return if (target !is ProfileHolder) false else {
                    configurationAdapter.move(
                        viewHolder.bindingAdapterPosition, target.bindingAdapterPosition
                    )
                    true
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                configurationAdapter.remove(viewHolder.bindingAdapterPosition)
            }

        }).attachToRecyclerView(configurationList)
    }

    override fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        view.rootView.findViewById<RecyclerView>(R.id.recycler_view)?.apply {
            (layoutParams ?: LinearLayout.LayoutParams(-1, -2)).apply {
                height = -2
                layoutParams = this
            }
        }

        runOnDefaultDispatcher {
            configurationAdapter.reload()
        }
    }

    inner class ProxiesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        suspend fun reload() {
            val idList = DataStore.serverProxies.split(",")
                .mapNotNull { it.takeIf { it.isNotBlank() }?.toLongOrNull() }
            if (idList.isNotEmpty()) {
                val dbList = ProfileManager.getProfiles(idList).associateBy { it.id }
                proxyList.clear()
                idList.mapNotNullTo(proxyList) { dbList[it] }
            }
            onMainDispatcher {
                notifyDataSetChanged()
            }
        }

        fun move(from: Int, to: Int) {
            val fromIndex = from - 1
            val toIndex = to - 1
            val item = proxyList.removeAt(fromIndex)
            proxyList.add(toIndex, item)
            notifyItemMoved(from, to)
        }

        fun remove(position: Int) {
            proxyList.removeAt(position - 1)
            notifyItemRemoved(position)
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                AddHolder(LayoutAddEntityBinding.inflate(layoutInflater, parent, false))
            } else {
                ProfileHolder(LayoutProfileBinding.inflate(layoutInflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is AddHolder) {
                holder.bind()
            } else if (holder is ProfileHolder) {
                holder.bind(proxyList[position - 1])
            }
        }

        override fun getItemCount(): Int {
            return proxyList.size + 1
        }

    }

    fun testProfileAllowed(profile: ProxyEntity): Boolean {
        if (profile.id == DataStore.editingId) return false

        for (entity in proxyList) {
            if (testProfileContains(entity, profile)) return false
        }

        return true
    }

    fun testProfileContains(profile: ProxyEntity, anotherProfile: ProxyEntity): Boolean {
        if (profile.id == anotherProfile.id) return true
        if (profile.type == ProxyEntity.TYPE_CHAIN) {
            val proxies = profile.chainBean?.proxies ?: emptyList()
            if (proxies.contains(anotherProfile.id)) return true
            for (entity in ProfileManager.getProfiles(proxies)) {
                if (testProfileContains(entity, anotherProfile)) return true
            }
        } else if (profile.type == ProxyEntity.TYPE_PROXY_SET) {
            val proxies = profile.proxySetBean?.proxies ?: emptyList()
            if (proxies.contains(anotherProfile.id)) return true
            for (entity in ProfileManager.getProfiles(proxies)) {
                if (testProfileContains(entity, anotherProfile)) return true
            }
        }
        return false
    }

    var replacing = 0

    val selectProfileForAdd =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { (resultCode, data) ->
            if (resultCode == Activity.RESULT_OK) runOnDefaultDispatcher {
                DataStore.dirty = true

                val profile = ProfileManager.getProfile(
                    data!!.getLongExtra(
                        ProfileSelectActivity.EXTRA_PROFILE_ID, 0
                    )
                ) ?: return@runOnDefaultDispatcher

                if (!testProfileAllowed(profile)) {
                    onMainDispatcher {
                        MaterialAlertDialogBuilder(this@ProxySetSettingsActivity).setTitle(R.string.circular_reference)
                            .setMessage(R.string.circular_reference_sum)
                            .setPositiveButton(android.R.string.ok, null).show()
                    }
                } else {
                    configurationList.post {
                        if (replacing != 0) {
                            proxyList[replacing - 1] = profile
                            configurationAdapter.notifyItemChanged(replacing)
                        } else {
                            proxyList.add(profile)
                            configurationAdapter.notifyItemInserted(proxyList.size)
                        }
                    }
                }
            }
        }

    inner class AddHolder(val binding: LayoutAddEntityBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener {
                replacing = 0
                selectProfileForAdd.launch(
                    Intent(
                        this@ProxySetSettingsActivity, ProfileSelectActivity::class.java
                    )
                )
            }
        }
    }

    inner class ProfileHolder(binding: LayoutProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val profileName = binding.profileName
        val profileType = binding.profileType
        val trafficText: TextView = binding.trafficText
        val editButton = binding.edit
        val shareLayout = binding.share

        fun bind(proxyEntity: ProxyEntity) {
            profileName.text = proxyEntity.displayName()
            profileType.text = proxyEntity.displayType()
            profileType.setTextColor(getProtocolColor(proxyEntity.type))

            val rx = proxyEntity.rx
            val tx = proxyEntity.tx

            val showTraffic = rx + tx != 0L
            trafficText.isVisible = showTraffic
            if (showTraffic) {
                trafficText.text = itemView.context.getString(
                    R.string.traffic,
                    Formatter.formatFileSize(itemView.context, tx),
                    Formatter.formatFileSize(itemView.context, rx)
                )
            }

            editButton.setOnClickListener {
                replacing = bindingAdapterPosition
                selectProfileForAdd.launch(Intent(
                    this@ProxySetSettingsActivity, ProfileSelectActivity::class.java
                ).apply {
                    putExtra(ProfileSelectActivity.EXTRA_SELECTED, proxyEntity)
                })
            }

            shareLayout.isVisible = false
        }

    }

}
