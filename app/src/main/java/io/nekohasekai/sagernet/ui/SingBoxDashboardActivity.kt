package io.nekohasekai.sagernet.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
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
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }
}
