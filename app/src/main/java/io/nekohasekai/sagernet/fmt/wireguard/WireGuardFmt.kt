package io.nekohasekai.sagernet.fmt.wireguard

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun genReserved(reserved: String): List<Int> {
    return reserved
        .replace("[", "")
        .replace("]", "")
        .split(",")
        .mapNotNull { it.trim().toIntOrNull() }
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
        public_key = bean.peerPublicKey
        if (bean.peerPreSharedKey.isNotBlank()) {
            pre_shared_key = bean.peerPreSharedKey
        }
        allowed_ips = mutableListOf("0.0.0.0/0", "::/0")
        if (bean.reserved.isNotBlank()) {
            val list = genReserved(bean.reserved)
            if (list.isNotEmpty()) {
                reserved = list
            }
        }
    }

    return SingBoxOptions.Outbound_WireGuardOptions().apply {
        type = "wireguard"
        peers = mutableListOf(peer)
        address = addrs
        private_key = bean.privateKey
        mtu = bean.mtu
    }
}
