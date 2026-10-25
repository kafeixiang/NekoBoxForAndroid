package io.nekohasekai.sagernet.fmt.wireguard

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma

fun genReserved(anyStr: String): String {
    try {
        val list = anyStr.listByLineOrComma()
        val ba = ByteArray(3)
        if (list.size == 3) {
            list.forEachIndexed { index, s ->
                val i = s
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .toIntOrNull() ?: return anyStr
                ba[index] = i.toByte()
            }
            return Util.b64EncodeOneLine(ba)
        } else {
            return anyStr
        }
    } catch (e: Exception) {
        return anyStr
    }
}

fun buildSingBoxOutboundWireguardBean(bean: WireGuardBean): SingBoxOptions.Outbound_WireGuardOptions {
    val addrs = bean.localAddress.listByLineOrComma()
    val peer = SingBoxOptions.WireGuardPeer().apply {
        address = bean.serverAddress
        port = bean.serverPort
        server = bean.serverAddress
        server_port = bean.serverPort
        public_key = bean.peerPublicKey
        if (bean.peerPreSharedKey.isNotBlank()) {
            pre_shared_key = bean.peerPreSharedKey
        }
        allowed_ips = mutableListOf("0.0.0.0/0", "::/0")
        if (bean.reserved.isNotBlank()) {
            reserved = genReserved(bean.reserved)
        }
    }

    return SingBoxOptions.Outbound_WireGuardOptions().apply {
        type = "wireguard"
        peers = mutableListOf(peer)
        address = addrs
        local_address = addrs
        private_key = bean.privateKey
        mtu = bean.mtu
        server = bean.serverAddress
        server_port = bean.serverPort
        peer_public_key = bean.peerPublicKey
        pre_shared_key = bean.peerPreSharedKey
        if (bean.reserved.isNotBlank()) reserved = genReserved(bean.reserved)
    }
}
