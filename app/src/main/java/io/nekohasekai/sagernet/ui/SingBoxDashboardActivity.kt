package io.nekohasekai.sagernet.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutSingboxDashboardBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class SingBoxDashboardActivity : ThemedActivity() {

    private lateinit var binding: LayoutSingboxDashboardBinding
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val apiBaseUrl = "http://127.0.0.1:9090"
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutSingboxDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.swipeRefresh.setOnRefreshListener {
            refreshProxies()
        }

        binding.btnConnections.setOnClickListener {
            showConnectionsDialog()
        }
        binding.textConnections.setOnClickListener {
            showConnectionsDialog()
        }

        updateCoreStatus()
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    private fun updateCoreStatus() {
        val isRunning = DataStore.serviceState.connected
        binding.textCoreStatus.text = if (isRunning) "Running" else "Stopped"
        val statusColor = if (isRunning) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        binding.textCoreStatus.setTextColor(statusColor)
    }

    private fun startPolling() {
        stopPolling()
        pollJob = lifecycleScope.launch {
            // 首先拉取一次完整的策略组与节点数据
            refreshProxies()

            // 循环轮询实时速率与连接数
            while (isActive) {
                if (DataStore.serviceState.connected) {
                    fetchTrafficAndConnections()
                } else {
                    onMainDispatcher {
                        updateCoreStatus()
                        binding.textSpeedUp.text = "0 B/s"
                        binding.textSpeedDown.text = "0 B/s"
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun fetchTrafficAndConnections() {
        runOnDefaultDispatcher {
            try {
                // 1. 获取连接信息及累计流量
                val connReq = Request.Builder().url("$apiBaseUrl/connections").build()
                httpClient.newCall(connReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val upTotal = json.optLong("uploadTotal", 0L)
                        val downTotal = json.optLong("downloadTotal", 0L)
                        val connArr = json.optJSONArray("connections")
                        val activeCount = connArr?.length() ?: 0

                        onMainDispatcher {
                            binding.textTotalUp.text = "↑ ${formatBytes(upTotal)}"
                            binding.textTotalDown.text = "↓ ${formatBytes(downTotal)}"
                            binding.textConnections.text = "活跃: $activeCount"
                        }
                    }
                }
            } catch (_: Exception) {
            }

            try {
                // 2. 获取实时上下行瞬时速率 (如果支持 /traffic)
                val trafReq = Request.Builder().url("$apiBaseUrl/traffic").build()
                httpClient.newCall(trafReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val upSpeed = json.optLong("up", 0L)
                        val downSpeed = json.optLong("down", 0L)

                        onMainDispatcher {
                            binding.textSpeedUp.text = "${formatBytes(upSpeed)}/s"
                            binding.textSpeedDown.text = "${formatBytes(downSpeed)}/s"
                            updateCoreStatus()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshProxies() {
        if (!DataStore.serviceState.connected) {
            binding.swipeRefresh.isRefreshing = false
            Toast.makeText(this, R.string.dashboard_service_not_running, Toast.LENGTH_SHORT).show()
            return
        }

        runOnDefaultDispatcher {
            try {
                val req = Request.Builder().url("$apiBaseUrl/proxies").build()
                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val proxiesObj = json.optJSONObject("proxies") ?: return@use

                        onMainDispatcher {
                            binding.swipeRefresh.isRefreshing = false
                            renderGroups(proxiesObj)
                        }
                    } else {
                        onMainDispatcher {
                            binding.swipeRefresh.isRefreshing = false
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun renderGroups(proxiesObj: JSONObject) {
        val container = binding.containerGroups
        container.removeAllViews()

        val groupKeys = mutableListOf<String>()
        val keys = proxiesObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = proxiesObj.optJSONObject(key) ?: continue
            val type = item.optString("type")
            // 筛选出策略组
            if (type.equals("Selector", ignoreCase = true) ||
                type.equals("URLTest", ignoreCase = true) ||
                type.equals("Fallback", ignoreCase = true)
            ) {
                groupKeys.add(key)
            }
        }

        // 排序：将 proxy / auto 放在最前面，GLOBAL 放在最后
        groupKeys.sortWith { a, b ->
            when {
                a == "proxy" -> -1
                b == "proxy" -> 1
                a == "auto" -> -1
                b == "auto" -> 1
                a == "GLOBAL" -> 1
                b == "GLOBAL" -> -1
                else -> a.compareTo(b)
            }
        }

        val inflater = LayoutInflater.from(this)

        for (groupKey in groupKeys) {
            val groupObj = proxiesObj.optJSONObject(groupKey) ?: continue
            val groupType = groupObj.optString("type")
            val groupNow = groupObj.optString("now")
            val allArr = groupObj.optJSONArray("all") ?: continue

            val groupCard = inflater.inflate(R.layout.layout_dashboard_group_card, container, false)
            val textGroupName = groupCard.findViewById<TextView>(R.id.text_group_name)
            val textGroupType = groupCard.findViewById<TextView>(R.id.text_group_type)
            val textGroupNow = groupCard.findViewById<TextView>(R.id.text_group_now)
            val btnTestGroup = groupCard.findViewById<View>(R.id.btn_test_group)
            val containerNodes = groupCard.findViewById<LinearLayout>(R.id.container_nodes)

            textGroupName.text = groupKey
            textGroupType.text = groupType
            textGroupNow.text = if (groupNow.isNotBlank()) "当前: $groupNow" else ""

            // 测速按钮事件
            btnTestGroup.setOnClickListener {
                testGroupLatency(groupKey, groupCard)
            }

            // 渲染组内各节点
            val isSelector = groupType.equals("Selector", ignoreCase = true)

            for (i in 0 until allArr.length()) {
                val nodeName = allArr.optString(i)
                val nodeObj = proxiesObj.optJSONObject(nodeName)
                val nodeType = nodeObj?.optString("type") ?: ""
                val history = nodeObj?.optJSONArray("history")

                val nodeItem = inflater.inflate(R.layout.layout_dashboard_node_item, containerNodes, false)
                val cardNode = nodeItem.findViewById<MaterialCardView>(R.id.card_node)
                val textNodeName = nodeItem.findViewById<TextView>(R.id.text_node_name)
                val textNodeType = nodeItem.findViewById<TextView>(R.id.text_node_type)
                val textNodeDelay = nodeItem.findViewById<TextView>(R.id.text_node_delay)
                val iconSelected = nodeItem.findViewById<ImageView>(R.id.icon_selected)

                textNodeName.text = nodeName
                textNodeType.text = nodeType

                // 延迟展示
                var delayMs = -1
                if (history != null && history.length() > 0) {
                    val lastRecord = history.optJSONObject(history.length() - 1)
                    delayMs = lastRecord?.optInt("delay", -1) ?: -1
                }

                if (delayMs > 0) {
                    textNodeDelay.text = "$delayMs ms"
                    textNodeDelay.visibility = View.VISIBLE
                    when {
                        delayMs < 200 -> textNodeDelay.setTextColor(Color.parseColor("#4CAF50")) // 绿色
                        delayMs < 500 -> textNodeDelay.setTextColor(Color.parseColor("#FF9800")) // 橙色
                        else -> textNodeDelay.setTextColor(Color.parseColor("#F44336")) // 红色
                    }
                } else if (delayMs == 0) {
                    textNodeDelay.text = "Timeout"
                    textNodeDelay.setTextColor(Color.parseColor("#F44336"))
                    textNodeDelay.visibility = View.VISIBLE
                } else {
                    textNodeDelay.visibility = View.GONE
                }

                // 选中指示
                val isSelected = nodeName == groupNow
                if (isSelected) {
                    iconSelected.visibility = View.VISIBLE
                    cardNode.strokeColor = ContextCompat.getColor(this, R.color.color_route_proxy)
                    cardNode.strokeWidth = 2
                } else {
                    iconSelected.visibility = View.INVISIBLE
                    cardNode.strokeColor = Color.parseColor("#E0E0E0")
                    cardNode.strokeWidth = 1
                }

                // Selector 模式下支持点击实时切换
                if (isSelector) {
                    cardNode.setOnClickListener {
                        selectNode(groupKey, nodeName)
                    }
                } else {
                    cardNode.isClickable = false
                }

                containerNodes.addView(nodeItem)
            }

            container.addView(groupCard)
        }
    }

    private fun selectNode(groupName: String, nodeName: String) {
        runOnDefaultDispatcher {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val bodyJson = JSONObject().apply { put("name", nodeName) }
                val body = bodyJson.toString().toRequestBody(mediaType)
                val req = Request.Builder()
                    .url("$apiBaseUrl/proxies/$groupName")
                    .put(body)
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        onMainDispatcher {
                            Toast.makeText(this@SingBoxDashboardActivity, getString(R.string.dashboard_switched_to, nodeName), Toast.LENGTH_SHORT).show()
                            refreshProxies()
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }

    private fun testGroupLatency(groupName: String, groupCard: View) {
        val testUrl = DataStore.connectionTestURL.ifBlank { "http://cp.cloudflare.com/" }
        Toast.makeText(this, R.string.dashboard_testing, Toast.LENGTH_SHORT).show()

        runOnDefaultDispatcher {
            try {
                val testUrlEncoded = java.net.URLEncoder.encode(testUrl, "UTF-8")
                val req = Request.Builder()
                    .url("$apiBaseUrl/group/$groupName/delay?url=$testUrlEncoded&timeout=5000")
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        onMainDispatcher {
                            refreshProxies()
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return formatBytesInternal(bytes)
    }

    private fun showConnectionsDialog() {
        if (!DataStore.serviceState.connected) {
            Toast.makeText(this, R.string.dashboard_service_not_running, Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_dialog_connections, null)
        dialog.setContentView(dialogView)

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            val displayMetrics = resources.displayMetrics
            bottomSheet.layoutParams.height = (displayMetrics.heightPixels * 0.85).toInt()
        }

        val textCount = dialogView.findViewById<TextView>(R.id.text_connections_count)
        val btnCloseAll = dialogView.findViewById<ImageButton>(R.id.btn_close_all)
        val btnDismiss = dialogView.findViewById<ImageButton>(R.id.btn_dismiss)
        val textEmpty = dialogView.findViewById<TextView>(R.id.text_empty_connections)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recycler_connections)

        btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        val adapter = ConnectionsAdapter(
            onKillClick = { conn ->
                closeConnection(conn.id) {
                    refreshConnectionsList(textCount, textEmpty, recyclerView)
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnCloseAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.close_all_connections)
                .setMessage(R.string.confirm_close_all_connections)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    closeAllConnections {
                        refreshConnectionsList(textCount, textEmpty, recyclerView)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refreshConnectionsList(textCount, textEmpty, recyclerView)

        var dialogJob: Job? = null
        dialog.setOnShowListener {
            dialogJob = lifecycleScope.launch {
                while (isActive && dialog.isShowing) {
                    delay(2000)
                    if (dialog.isShowing) {
                        refreshConnectionsList(textCount, textEmpty, recyclerView)
                    }
                }
            }
        }
        dialog.setOnDismissListener {
            dialogJob?.cancel()
        }

        dialog.show()
    }

    private fun refreshConnectionsList(
        textCount: TextView,
        textEmpty: TextView,
        recyclerView: RecyclerView,
    ) {
        runOnDefaultDispatcher {
            val list = mutableListOf<ConnectionItem>()
            try {
                val req = Request.Builder().url("$apiBaseUrl/connections").build()
                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val connArr = json.optJSONArray("connections")
                        if (connArr != null) {
                            for (i in 0 until connArr.length()) {
                                val connObj = connArr.optJSONObject(i) ?: continue
                                val id = connObj.optString("id")
                                val meta = connObj.optJSONObject("metadata")
                                val network = meta?.optString("network") ?: "tcp"
                                val host = meta?.optString("host") ?: ""
                                val destIP = meta?.optString("destinationIP") ?: ""
                                val destPort = meta?.optString("destinationPort") ?: ""
                                val process = meta?.optString("processPath") ?: ""
                                val up = connObj.optLong("upload", 0L)
                                val down = connObj.optLong("download", 0L)
                                val rule = connObj.optString("rule") ?: ""
                                val chainsArr = connObj.optJSONArray("chains")
                                val chainsList = mutableListOf<String>()
                                if (chainsArr != null) {
                                    for (j in 0 until chainsArr.length()) {
                                        val chain = chainsArr.optString(j)
                                        if (chain.isNotBlank()) chainsList.add(chain)
                                    }
                                }
                                list.add(
                                    ConnectionItem(
                                        id = id,
                                        network = network.uppercase(Locale.ROOT),
                                        host = host,
                                        destinationIP = destIP,
                                        destinationPort = destPort,
                                        processPath = process,
                                        upload = up,
                                        download = down,
                                        rule = rule,
                                        chains = chainsList
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
            }

            onMainDispatcher {
                textCount.text = getString(R.string.connections_count, list.size)
                if (list.isEmpty()) {
                    textEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    textEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    (recyclerView.adapter as? ConnectionsAdapter)?.submitList(list)
                }
            }
        }
    }

    private fun closeConnection(id: String, onClosed: () -> Unit) {
        runOnDefaultDispatcher {
            try {
                val req = Request.Builder()
                    .url("$apiBaseUrl/connections/$id")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
                onMainDispatcher {
                    onClosed()
                }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }

    private fun closeAllConnections(onClosed: () -> Unit) {
        runOnDefaultDispatcher {
            try {
                val req = Request.Builder()
                    .url("$apiBaseUrl/connections")
                    .delete()
                    .build()
                httpClient.newCall(req).execute().close()
                onMainDispatcher {
                    onClosed()
                }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }
}

internal fun formatBytesInternal(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
}

data class ConnectionItem(
    val id: String,
    val network: String,
    val host: String,
    val destinationIP: String,
    val destinationPort: String,
    val processPath: String,
    val upload: Long,
    val download: Long,
    val rule: String,
    val chains: List<String>
) {
    val displayTarget: String
        get() {
            val dest = if (host.isNotBlank()) host else destinationIP
            return if (destinationPort.isNotBlank()) "$dest:$destinationPort" else dest
        }

    val displayRuleChain: String
        get() {
            return if (chains.isNotEmpty()) {
                chains.joinToString(" → ")
            } else if (rule.isNotBlank()) {
                rule
            } else {
                "DIRECT"
            }
        }
}

class ConnectionsAdapter(
    private val onKillClick: (ConnectionItem) -> Unit,
) : RecyclerView.Adapter<ConnectionsAdapter.ViewHolder>() {

    private val items = ArrayList<ConnectionItem>()

    fun submitList(newItems: List<ConnectionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textNetwork: TextView = view.findViewById(R.id.text_conn_network)
        val textHost: TextView = view.findViewById(R.id.text_conn_host)
        val textProcess: TextView = view.findViewById(R.id.text_conn_process)
        val textTraffic: TextView = view.findViewById(R.id.text_conn_traffic)
        val textRuleChain: TextView = view.findViewById(R.id.text_conn_rule_chain)
        val btnKill: ImageButton = view.findViewById(R.id.btn_kill_conn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_connection_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textNetwork.text = item.network
        if (item.network == "UDP") {
            holder.textNetwork.setTextColor(Color.parseColor("#FF9800"))
            holder.textNetwork.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#20FF9800"))
        } else {
            holder.textNetwork.setTextColor(Color.parseColor("#2196F3"))
            holder.textNetwork.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#202196F3"))
        }

        holder.textHost.text = item.displayTarget

        if (item.processPath.isNotBlank()) {
            holder.textProcess.text = item.processPath
            holder.textProcess.visibility = View.VISIBLE
        } else {
            holder.textProcess.visibility = View.GONE
        }

        holder.textTraffic.text = "↑ ${formatBytesInternal(item.upload)}  ↓ ${formatBytesInternal(item.download)}"
        holder.textRuleChain.text = item.displayRuleChain

        holder.btnKill.setOnClickListener {
            onKillClick(item)
        }
    }
}
