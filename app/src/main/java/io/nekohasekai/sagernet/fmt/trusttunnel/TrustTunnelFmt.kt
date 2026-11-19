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
        utlsFingerprint = url.tlsProfile.toUtlsFingerprint()
        name = url.name
    }
}

fun TrustTunnelBean.toUri(): String {
    val host = sni?.takeIf { it.isNotBlank() } ?: serverAddress
    return TrustTunnelLink(
        hostname = host,
        addresses = listOf(joinAddress(serverAddress, serverPort)),
        customSni = sni ?: "",
        username = username ?: "",
        password = password ?: "",
        skipVerification = allowInsecure == true,
        certificate = certificates?.takeIf { it.isNotBlank() }?.let(::pemToCertificateChain) ?: ByteString.EMPTY,
        http3 = quic == true,
        tlsProfile = utlsFingerprint?.toTlsProfile() ?: "",
        name = name ?: "",
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
            if (bean.allowInsecure == true || (try { DataStore.globalAllowInsecure } catch (_: Throwable) { false })) {
                insecure = true
            }
            if (!bean.alpn.isNullOrBlank()) {
                alpn = bean.alpn.listByLineOrComma()
            }
            if (!bean.certificates.isNullOrBlank()) {
                certificate = bean.certificates
            }
            if (!bean.utlsFingerprint.isNullOrBlank()) {
                utls = SingBoxOptions.OutboundUTLSOptions().apply {
                    enabled = true
                    fingerprint = bean.utlsFingerprint
                }
            }
        }
    }
}

private const val TLS_PROFILE_CHROME_CANARY = "chrome_canary"
private const val TLS_PROFILE_OKHTTP = "okhttp"

private const val FINGERPRINT_CHROME = "chrome"
private const val FINGERPRINT_SAFARI = "safari"
private const val FINGERPRINT_FIREFOX = "firefox"
private const val FINGERPRINT_ANDROID = "android"

private fun String.toUtlsFingerprint(): String = when (this) {
    FINGERPRINT_CHROME,
    FINGERPRINT_SAFARI,
    FINGERPRINT_FIREFOX,
        -> this

    TLS_PROFILE_CHROME_CANARY -> FINGERPRINT_CHROME
    TLS_PROFILE_OKHTTP -> FINGERPRINT_ANDROID
    else -> ""
}

private fun String.toTlsProfile(): String = when (this) {
    FINGERPRINT_CHROME,
    FINGERPRINT_SAFARI,
    FINGERPRINT_FIREFOX,
        -> this

    FINGERPRINT_ANDROID -> TLS_PROFILE_OKHTTP
    else -> ""
}
