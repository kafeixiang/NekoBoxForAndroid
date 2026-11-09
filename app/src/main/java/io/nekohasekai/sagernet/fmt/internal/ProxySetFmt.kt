package io.nekohasekai.sagernet.fmt.internal

import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.JavaUtil

fun ProxySetBean.resolveMembers(selfId: Long = -1L): List<ProxyEntity> {
    val members = mutableListOf<ProxyEntity>()
    when (type ?: ProxySetBean.TYPE_LIST) {
        ProxySetBean.TYPE_LIST -> {
            val pList = proxies
            if (!pList.isNullOrEmpty()) {
                val entities = SagerDatabase.proxyDao.getEntities(pList)
                val map = entities.associateBy { it.id }
                for (id in pList) {
                    val entity = map[id] ?: continue
                    if (entity.id == selfId) continue
                    members.add(entity)
                }
            }
        }

        ProxySetBean.TYPE_GROUP -> {
            val gid = groupId ?: 0L
            if (gid > 0L) {
                val groupEntities = SagerDatabase.proxyDao.getByGroup(gid)
                val filterRegexStr = groupFilterNotRegex
                val regex = if (!filterRegexStr.isNullOrBlank()) {
                    runCatching { Regex(filterRegexStr) }.getOrNull()
                } else null
                for (entity in groupEntities) {
                    if (entity.id == selfId) continue
                    if (regex != null && !regex.containsMatchIn(entity.displayName())) {
                        continue
                    }
                    members.add(entity)
                }
            }
        }
    }
    return members
}

fun buildSingBoxOutboundProxySetBean(
    bean: ProxySetBean,
    outbounds: List<String>,
): SingBoxOptions.Outbound {
    return when (bean.management ?: ProxySetBean.MANAGEMENT_SELECTOR) {
        ProxySetBean.MANAGEMENT_SELECTOR -> SingBoxOptions.Outbound_SelectorOptions().apply {
            type = "selector"
            this.outbounds = outbounds.toMutableList()
            interrupt_exist_connections = bean.interruptExistConnections
        }

        ProxySetBean.MANAGEMENT_URLTEST -> SingBoxOptions.Outbound_URLTestOptions().apply {
            type = "urltest"
            this.outbounds = outbounds.toMutableList()
            url = if (JavaUtil.isNotBlank(bean.testURL)) bean.testURL else "http://cp.cloudflare.com"
            tolerance = bean.testTolerance ?: 50
            interrupt_exist_connections = bean.interruptExistConnections
            if (JavaUtil.isNotBlank(bean.testInterval)) {
                _hack_config_map["interval"] = bean.testInterval
            }
            if (JavaUtil.isNotBlank(bean.testIdleTimeout)) {
                _hack_config_map["idle_timeout"] = bean.testIdleTimeout
            }
        }

        ProxySetBean.MANAGEMENT_BALANCER -> SingBoxOptions.Outbound_BalancerOptions().apply {
            type = "balancer"
            this.outbounds = outbounds.toMutableList()
            if (JavaUtil.isNotBlank(bean.testInterval)) {
                _hack_config_map["interval"] = bean.testInterval
            }
        }

        else -> throw IllegalStateException("Unknown management: ${bean.management}")
    }
}
