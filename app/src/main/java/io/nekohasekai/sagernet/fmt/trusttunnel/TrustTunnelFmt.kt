package io.nekohasekai.sagernet.fmt.trusttunnel

import io.nekohasekai.sagernet.database.DataStore
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.SingBoxOptions.Outbound_TrustTunnelOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okio.ByteString

fun parseTrustTunnel(link: String): TrustTunnelBean {
    val url = TrustTunnelLink.parse(link)
    val (host, port) = checkNotNull(splitAddress(url.addresses.first()))
    return TrustTunnelBean().apply {
        serverAddress = host
        serverPort = checkNotNull(port.toIntOrNull())
        sni = url.customSni
        username = url.username
        password = url.password
        allowInsecure = url.skipVerification
        certificates = if (url.certificate.size > 0) certificateChainToPem(url.certificate) else ""
        quic = url.http3
        name = url.name
    }
}

fun TrustTunnelBean.toUri(): String {
    return TrustTunnelLink(
        hostname = sni.ifEmpty { serverAddress },
        addresses = listOf(joinAddress(serverAddress, serverPort)),
        customSni = sni,
        username = username,
        password = password,
        skipVerification = allowInsecure,
        certificate = certificates.takeIf { it.isNotBlank() }?.let(::pemToCertificateChain) ?: ByteString.EMPTY,
        http3 = quic,
        name = name,
    ).build()
}

fun buildSingBoxOutboundTrustTunnelBean(bean: TrustTunnelBean): Outbound_TrustTunnelOptions {
    return Outbound_TrustTunnelOptions().apply {
        type = "trusttunnel"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        if (bean.healthCheck == true) health_check = true
        if (bean.quic == true) {
            quic = true
            quic_congestion_control = bean.quicCongestionControl.takeIf { !it.isNullOrBlank() }
        }

        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            if (!bean.sni.isNullOrBlank()) {
                server_name = bean.sni
            }
            if (bean.allowInsecure == true || DataStore.globalAllowInsecure) {
                insecure = true
            }
            if (!bean.alpn.isNullOrBlank()) {
                alpn = bean.alpn.listByLineOrComma()
            }
            if (!bean.certificates.isNullOrBlank()) {
                certificate = bean.certificates
            }
        }
    }
}
