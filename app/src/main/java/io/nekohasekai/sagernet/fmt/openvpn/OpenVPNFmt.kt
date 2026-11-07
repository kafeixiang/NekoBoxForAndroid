package io.nekohasekai.sagernet.fmt.openvpn

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.blankAsNull
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun looksLikeOpenVPNConfig(conf: String): Boolean {
    return conf.lineSequence().any { rawLine ->
        val line = rawLine.trim()
        line == "client" || line.startsWith("remote ")
    }
}

private val openVPNMaterialBlocks = setOf(
    "ca",
    "cert",
    "key",
    "tls-auth",
    "tls-crypt",
    "tls-crypt-v2",
    "auth-user-pass",
)

private fun parseOpenVPNInlineBlocks(conf: String): Map<String, String> {
    val blocks = mutableMapOf<String, String>()
    var activeBlockName: String? = null
    val content = StringBuilder()

    for (rawLine in conf.lineSequence()) {
        val line = rawLine.trim()
        val currentBlockName = activeBlockName
        if (currentBlockName == null) {
            if (line.startsWith('<') && line.endsWith('>') && !line.startsWith("</")) {
                val blockName = line.substring(1, line.length - 1).lowercase()
                if (blockName in openVPNMaterialBlocks) {
                    activeBlockName = blockName
                    content.clear()
                }
            }
        } else if (line.equals("</$currentBlockName>", ignoreCase = true)) {
            blocks[currentBlockName] = content.toString().trim()
            activeBlockName = null
        } else {
            if (content.isNotEmpty()) content.append('\n')
            content.append(rawLine)
        }
    }

    check(activeBlockName == null) { "OpenVPN inline <$activeBlockName> block is not closed." }
    return blocks
}

private fun openVPNDirectiveLines(conf: String): Sequence<String> = sequence {
    var closingTag: String? = null

    for (rawLine in conf.lineSequence()) {
        val line = rawLine.trim()
        val currentClosingTag = closingTag
        if (currentClosingTag != null) {
            if (line.equals(currentClosingTag, ignoreCase = true)) closingTag = null
            continue
        }
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
        if (line.startsWith('<') && line.endsWith('>')) {
            if (!line.startsWith("</")) {
                val blockName = line.substring(1, line.length - 1).lowercase()
                if (blockName in openVPNMaterialBlocks) closingTag = "</$blockName>"
            }
            continue
        }
        yield(line)
    }
}

private fun parseOpenVPNKeyDirection(value: String?): String? = when (value) {
    "0" -> "server"
    "1" -> "client"
    else -> null
}

private fun parseOpenVPNNetwork(value: String): String {
    return if (value.lowercase().startsWith("tcp")) "tcp" else "udp"
}

private fun OpenVPNBean.discardUnpairedClientCertificate() {
    if (clientCertificate.isNotBlank() && clientKey.isNotBlank()) return
    clientCertificate = ""
    clientKey = ""
}

fun parseOpenVPNConfig(conf: String): OpenVPNBean {
    val inlineBlocks = parseOpenVPNInlineBlocks(conf)
    val bean = OpenVPNBean().applyDefaultValues().also {
        it.certificate = inlineBlocks["ca"].orEmpty()
        it.clientCertificate = inlineBlocks["cert"].orEmpty()
        it.clientKey = inlineBlocks["key"].orEmpty()
        listOf("tls-auth", "tls-crypt", "tls-crypt-v2").firstOrNull { directive ->
            !inlineBlocks[directive].isNullOrBlank()
        }?.let { directive ->
            it.controlWrapType = directive.replace('-', '_')
            it.controlWrapKey = inlineBlocks[directive].orEmpty()
        }
        inlineBlocks["auth-user-pass"]?.lines()?.let { credentials ->
            it.username = credentials.getOrNull(0)?.trim().orEmpty()
            it.password = credentials.getOrNull(1)?.trim().orEmpty()
        }
    }
    var foundRemote = false

    for (line in openVPNDirectiveLines(conf)) {
        val arguments = line.split(' ', '\t').filter { it.isNotEmpty() }
        val directive = arguments.first().lowercase()
        val values = arguments.drop(1)
        when (directive) {
            "remote" -> {
                val server = values.getOrNull(0).orEmpty()
                if (server.isBlank() || foundRemote) continue
                bean.serverAddress = server
                bean.serverPort = values.getOrNull(1)?.toIntOrNull() ?: 1194
                values.getOrNull(2)?.let { bean.network = parseOpenVPNNetwork(it) }
                foundRemote = true
            }

            "proto" -> values.firstOrNull()?.let { bean.network = parseOpenVPNNetwork(it) }

            "ca" -> inlineBlocks["ca"]?.let { certificate -> bean.certificate = certificate }

            "cert" -> inlineBlocks["cert"]?.let { certificate ->
                bean.clientCertificate = certificate
            }

            "key" -> inlineBlocks["key"]?.let { key -> bean.clientKey = key }

            "tls-auth", "tls-crypt", "tls-crypt-v2" -> {
                bean.controlWrapType = directive.replace('-', '_')
                inlineBlocks[directive]?.let { key -> bean.controlWrapKey = key }
                if (directive == "tls-auth") {
                    parseOpenVPNKeyDirection(values.getOrNull(1))?.let { direction ->
                        bean.controlWrapDirection = direction
                    }
                }
            }

            "key-direction" -> parseOpenVPNKeyDirection(values.firstOrNull())?.let { direction ->
                bean.controlWrapDirection = direction
            }

            "data-ciphers" -> bean.dataCiphers = values.joinToString("\n") { it.replace(':', '\n') }

            "cipher" -> if (bean.dataCiphers.isBlank()) bean.dataCiphers =
                values.firstOrNull().orEmpty()

            "auth" -> bean.auth = values.firstOrNull().orEmpty()

            "compress" -> bean.compression = values.firstOrNull() ?: "stub"

            "redirect-gateway" -> bean.redirectGateway = true

            "tun-mtu" -> values.firstOrNull()?.toIntOrNull()?.let { bean.mtu = it }

            "verify-x509-name" -> {
                bean.serverName = values.firstOrNull().orEmpty()
                bean.serverNameType = values.getOrNull(1).orEmpty()
            }

            "remote-cert-ku" -> bean.remoteCertificateKU = values.joinToString("\n")

            "remote-cert-tls" -> bean.remoteCertificateEKU = when (values.firstOrNull()) {
                "server" -> "server"
                "client" -> "client"
                else -> ""
            }

            "peer-fingerprint" -> bean.peerFingerprint = values.joinToString("\n")
        }
    }

    check(foundRemote) { "OpenVPN configuration is missing a remote server." }
    check(bean.certificate.isNotBlank() || bean.peerFingerprint.isNotBlank()) {
        "OpenVPN configuration requires an inline <ca> block or peer-fingerprint."
    }
    bean.discardUnpairedClientCertificate()
    check(bean.controlWrapType.isBlank() || bean.controlWrapKey.isNotBlank()) {
        "OpenVPN control channel protection requires an inline key block."
    }
    if (bean.controlWrapType != "tls_auth") bean.controlWrapDirection = ""
    return bean.applyDefaultValues()
}

fun buildSingBoxEndpointOpenVPNBean(bean: OpenVPNBean): SingBoxOptions.Endpoint_OpenVPNClientOptions {
    return SingBoxOptions.Endpoint_OpenVPNClientOptions().apply {
        type = "openvpn-client"
        server = bean.serverAddress
        server_port = bean.serverPort
        network = bean.network
        username = bean.username.blankAsNull()
        password = bean.password.blankAsNull()
        cipher = bean.cipher.blankAsNull()
        data_ciphers = bean.dataCiphers.blankAsNull()?.listByLineOrComma()?.toMutableList()
        auth = bean.auth.blankAsNull()
        compression = bean.compression.blankAsNull()
        if (bean.redirectGateway == true) {
            redirect_gateway = true
        }
        mtu = bean.mtu
        tls = SingBoxOptions.OpenVPNOutboundTLSOptions().apply {
            server_name = bean.serverName.blankAsNull()
            server_name_type = bean.serverNameType.blankAsNull()
            certificate = bean.certificate.blankAsNull()?.let { mutableListOf(it) }
            client_certificate = bean.clientCertificate.blankAsNull()?.let { mutableListOf(it) }
            client_key = bean.clientKey.blankAsNull()?.let { mutableListOf(it) }
            peer_fingerprint = bean.peerFingerprint
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            remote_certificate_ku = bean.remoteCertificateKU
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            remote_certificate_eku = bean.remoteCertificateEKU.blankAsNull()
            if (!bean.controlWrapType.isNullOrBlank() || !bean.controlWrapKey.isNullOrBlank()) {
                control_wrap = SingBoxOptions.OpenVPNControlWrapOptions().apply {
                    type = bean.controlWrapType.blankAsNull()
                    key = bean.controlWrapKey.blankAsNull()?.let { mutableListOf(it) }
                    direction = bean.controlWrapDirection.blankAsNull()
                }
            }
        }
    }
}
