package io.nekohasekai.sagernet.fmt.openconnect

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun looksLikeOpenConnectConfig(conf: String): Boolean {
    return conf.lineSequence().any { rawLine ->
        val line = rawLine.trim()
        line.startsWith("server ") || line.startsWith("server=") ||
        line.startsWith("protocol ") || line.startsWith("protocol=") ||
        line.startsWith("authgroup ") || line.startsWith("authgroup=")
    }
}

fun parseOpenConnect(url: String): OpenConnectBean {
    val link = url.replace("anyconnect://", "https://")
        .replace("openconnect://", "https://")
        .toHttpUrlOrNull() ?: error("Invalid OpenConnect URL: $url")

    return OpenConnectBean().apply {
        initializeDefaultValues()
        server = "${link.host}:${link.port}"
        serverAddress = link.host
        serverPort = link.port
        if (link.username.isNotEmpty()) username = link.username.unUrlSafe()
        if (link.password.isNotEmpty()) password = link.password.unUrlSafe()
        if (!link.fragment.isNullOrEmpty()) name = link.fragment!!.unUrlSafe()

        link.queryParameter("flavor")?.let { flavor = it.unUrlSafe() }
        (link.queryParameter("authgroup") ?: link.queryParameter("group"))?.let { authGroup = it.unUrlSafe() }
        (link.queryParameter("allowInsecureCrypto") ?: link.queryParameter("allow_insecure_crypto"))?.let {
            allowInsecureCrypto = it == "true" || it == "1"
        }
        (link.queryParameter("insecure") ?: link.queryParameter("allowInsecure") ?: link.queryParameter("allow_insecure"))?.let {
            tlsInsecure = it == "true" || it == "1"
        }
        (link.queryParameter("sni") ?: link.queryParameter("serverName"))?.let { tlsServerName = it.unUrlSafe() }
        (link.queryParameter("fingerprint") ?: link.queryParameter("fp"))?.let { tlsPeerFingerprint = it.unUrlSafe() }
        parseServerAddress()
    }
}

fun OpenConnectBean.toUri(): String {
    val builder = StringBuilder("anyconnect://")
    val user = username.orEmpty()
    val pass = password.orEmpty()
    if (user.isNotBlank() || pass.isNotBlank()) {
        builder.append(user.urlSafe()).append(":").append(pass.urlSafe()).append("@")
    }
    val s = server.orEmpty().trim().removePrefix("https://").removePrefix("http://")
    builder.append(s)

    val params = mutableListOf<String>()
    if (!flavor.isNullOrBlank() && flavor != "anyconnect") params.add("flavor=${flavor.urlSafe()}")
    if (!authGroup.isNullOrBlank()) params.add("authgroup=${authGroup.urlSafe()}")
    if (allowInsecureCrypto == true) params.add("allowInsecureCrypto=true")
    if (tlsInsecure == true) params.add("insecure=true")
    if (!tlsServerName.isNullOrBlank()) params.add("sni=${tlsServerName.urlSafe()}")
    if (!tlsPeerFingerprint.isNullOrBlank()) params.add("fingerprint=${tlsPeerFingerprint.urlSafe()}")
    if (params.isNotEmpty()) {
        builder.append("?").append(params.joinToString("&"))
    }
    if (!name.isNullOrBlank()) {
        builder.append("#").append(name.urlSafe())
    }
    return builder.toString()
}

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

            "protocol", "flavor" -> bean.flavor = value.orEmpty()
            "user", "username" -> bean.username = value.orEmpty()
            "password" -> bean.password = value.orEmpty()
            "usergroup", "authgroup", "group" -> bean.authGroup = value.orEmpty()
            "os" -> bean.reportedOS = value.orEmpty()
            "useragent" -> bean.userAgent = value.orEmpty()
            "allow-insecure-crypto" -> bean.allowInsecureCrypto = true
            "insecure" -> bean.tlsInsecure = true
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
    bean.parseServerAddress()
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
