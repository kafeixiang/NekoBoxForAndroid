package io.nekohasekai.sagernet.fmt.openconnect

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.blankAsNull
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun parseOpenConnectConfig(conf: String): OpenConnectBean {
    val bean = OpenConnectBean().applyDefaultValues()
    var hasServer = false

    for (line in conf.lineSequence()) {
        val (name, value) = parseOpenConnectConfigOption(line) ?: continue
        when (name) {
            "server" -> {
                bean.server = value.orEmpty()
                hasServer = bean.server.isNotBlank()
            }

            "protocol" -> bean.flavor = value.orEmpty()
            "user" -> bean.username = value.orEmpty()
            "usergroup", "authgroup" -> bean.authGroup = value.orEmpty()
            "os" -> bean.reportedOS = value.orEmpty()
            "useragent" -> bean.userAgent = value.orEmpty()
            "allow-insecure-crypto" -> bean.allowInsecureCrypto = true
            "cafile" -> bean.certificateAuthority = value.orEmpty()
            "certificate" -> bean.clientCertificate = value.orEmpty()
            "sslkey" -> bean.clientKey = value.orEmpty()
            "key-password" -> bean.clientKeyPassword = value.orEmpty()
            "mca-certificate" -> bean.mcaCertificate = value.orEmpty()
            "mca-key" -> bean.mcaKey = value.orEmpty()
            "mca-key-password" -> bean.mcaKeyPassword = value.orEmpty()
            "form-entry" -> value?.toOpenConnectFormEntry()?.let { entry ->
                bean.formEntries.add(entry)
            }
        }
    }

    require(hasServer) { "missing server" }
    return bean
}

private fun parseOpenConnectConfigOption(rawLine: String): Pair<String, String?>? {
    val line = rawLine.trim()
    if (line.isEmpty() || line.startsWith('#')) return null

    val separator = line.indexOfFirst { it == '=' || it.isWhitespace() }
    if (separator == -1) return line.lowercase() to null

    val name = line.substring(0, separator).lowercase()
    val value = line.substring(separator)
        .trimStart()
        .removePrefix("=")
        .trimStart()
    return name to value
}

private fun String.toOpenConnectFormEntry(): OpenConnectBean.FormEntry? {
    val key = substringBefore('=')
    val formId = key.substringBefore(':')
    val name = key.substringAfter(':', missingDelimiterValue = "")
    if (formId.isBlank() || name.isBlank() || '=' !in this) return null

    return OpenConnectBean.FormEntry(
        formId,
        "",
        name,
        substringAfter('='),
    )
}

fun buildSingBoxEndpointOpenConnectBean(bean: OpenConnectBean): SingBoxOptions.Endpoint_OpenConnectOptions {
    return SingBoxOptions.Endpoint_OpenConnectOptions().apply {
        type = "openconnect"
        server = bean.server
        flavor = bean.flavor.blankAsNull()
        username = bean.username.blankAsNull()
        password = bean.password.blankAsNull()
        auth_group = bean.authGroup.blankAsNull()
        reported_os = bean.reportedOS.blankAsNull()
        user_agent = bean.userAgent.blankAsNull()
        local_hostname = bean.localHostname.blankAsNull()
        if (bean.allowInsecureCrypto == true) {
            allow_insecure_crypto = true
        }
        form_entries = bean.formEntries?.takeIf { it.isNotEmpty() }?.map { entry ->
            SingBoxOptions.OpenConnectFormEntryOptions().apply {
                form_id = entry.formId.blankAsNull()
                submission_key = entry.submissionKey.blankAsNull()
                name = entry.name.blankAsNull()
                value = entry.value
            }
        }?.toMutableList()
        tls = SingBoxOptions.OpenConnectTLSOptions().apply {
            if (bean.tlsInsecure == true) {
                insecure = true
            }
            server_name = bean.tlsServerName.blankAsNull()
            peer_fingerprint = bean.tlsPeerFingerprint
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            certificate_authority = bean.certificateAuthority
                .blankAsNull()
                ?.listByLineOrComma()
                ?.toMutableList()
            client_certificate = bean.clientCertificate.blankAsNull()?.let { mutableListOf(it) }
            client_key = bean.clientKey.blankAsNull()?.let { mutableListOf(it) }
            client_key_password = bean.clientKeyPassword.blankAsNull()
            mca_certificate = bean.mcaCertificate.blankAsNull()?.let { mutableListOf(it) }
            mca_key = bean.mcaKey.blankAsNull()?.let { mutableListOf(it) }
            mca_key_password = bean.mcaKeyPassword.blankAsNull()
        }
    }
}
