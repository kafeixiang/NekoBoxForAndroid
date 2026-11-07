package io.nekohasekai.sagernet.fmt.wireguard

import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun genReserved(reserved: String): List<Int> {
    return reserved
        .replace("[", "")
        .replace("]", "")
        .split(",")
        .mapNotNull { it.trim().toIntOrNull() }
}

fun parseWireGuardLink(url: String): WireGuardBean {
    val link = url
        .replace("wireguard://", "https://")
        .replace("wg://", "https://")
        .toHttpUrlOrNull() ?: error("invalid wireguard link $url")
    return WireGuardBean().apply {
        serverAddress = link.host
        serverPort = if (link.port > 0) link.port else 51820
        privateKey = link.username.replace(" ", "+")
        name = link.fragment

        (link.queryParameter("publickey") ?: link.queryParameter("public-key") ?: link.queryParameter("public_key"))?.let {
            peerPublicKey = it.replace(" ", "+")
        }
        (link.queryParameter("presharedkey") ?: link.queryParameter("preshared-key") ?: link.queryParameter("preshared_key"))?.let {
            peerPreSharedKey = it.replace(" ", "+")
        }
        (link.queryParameter("address") ?: link.queryParameter("ip"))?.let {
            localAddress = it.replace(",", "\n")
        }
        link.queryParameter("mtu")?.toIntOrNull()?.let {
            mtu = it
        }
        link.queryParameter("reserved")?.let {
            reserved = it
        }
        link.queryParameter("workers")?.toIntOrNull()?.let {
            workers = it
        }
        (link.queryParameter("keepalive") ?: link.queryParameter("persistent-keepalive"))?.toIntOrNull()?.let {
            persistentKeepalive = it
        }
        (link.queryParameter("system") ?: link.queryParameter("system-interface"))?.let {
            systemInterface = it == "1" || it == "true"
        }
    }
}

fun WireGuardBean.toUri(): String {
    val builder = linkBuilder()
        .username(privateKey)
        .host(serverAddress)
        .port(serverPort)
    if (peerPublicKey.isNotBlank()) builder.addQueryParameter("publickey", peerPublicKey)
    if (peerPreSharedKey.isNotBlank()) builder.addQueryParameter("presharedkey", peerPreSharedKey)
    if (localAddress.isNotBlank()) builder.addQueryParameter("address", localAddress.replace("\n", ","))
    if (mtu != null && mtu != 1420) builder.addQueryParameter("mtu", mtu.toString())
    if (reserved.isNotBlank()) builder.addQueryParameter("reserved", reserved)
    if (workers != null && workers > 0) builder.addQueryParameter("workers", workers.toString())
    if (persistentKeepalive != null && persistentKeepalive > 0) builder.addQueryParameter("keepalive", persistentKeepalive.toString())
    if (systemInterface == true) builder.addQueryParameter("system", "1")
    if (name.isNotBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("wireguard")
}

fun buildSingBoxOutboundWireguardBean(bean: WireGuardBean): SingBoxOptions.Outbound_WireGuardOptions {
    val addrs = bean.localAddress.listByLineOrComma().mapNotNull { raw ->
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            null
        } else if (!trimmed.contains("/")) {
            if (trimmed.contains(":")) "$trimmed/128" else "$trimmed/32"
        } else {
            trimmed
        }
    }
    val peer = SingBoxOptions.WireGuardPeer().apply {
        address = bean.serverAddress
        port = bean.serverPort
        public_key = bean.peerPublicKey.trim()
        if (bean.peerPreSharedKey.isNotBlank()) {
            pre_shared_key = bean.peerPreSharedKey.trim()
        }
        allowed_ips = mutableListOf("0.0.0.0/0", "::/0")
        if (bean.reserved.isNotBlank()) {
            val list = genReserved(bean.reserved)
            if (list.isNotEmpty()) {
                reserved = list
            }
        }
        if (bean.persistentKeepalive != null && bean.persistentKeepalive > 0) {
            persistent_keepalive_interval = bean.persistentKeepalive
        }
    }

    return SingBoxOptions.Outbound_WireGuardOptions().apply {
        type = "wireguard"
        if (bean.systemInterface == true) {
            system = true
        }
        peers = mutableListOf(peer)
        address = addrs
        private_key = bean.privateKey.trim()
        mtu = bean.mtu
        if (bean.workers != null && bean.workers > 0) {
            workers = bean.workers
        }
    }
}
