package io.nekohasekai.sagernet.fmt.tor

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.linkBuilder
import io.nekohasekai.sagernet.ktx.toLink
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.urlSafe
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

fun buildSingBoxOutboundTorBean(bean: TorBean): SingBoxOptions.Outbound_TorOptions {
    return SingBoxOptions.Outbound_TorOptions().apply {
        type = "tor"
        executable_path = bean.executablePath.blankAsNull()
        data_directory = bean.dataDirectory.blankAsNull()
            ?: runCatching { File(SagerNet.application.filesDir, "tor").apply { mkdirs() }.absolutePath }.getOrNull()
        extra_args = bean.extraArgs.blankAsNull()?.listByLineOrComma()?.toMutableList()
        if (!bean.torrc.isNullOrBlank()) {
            val map = mutableMapOf<String, String>()
            bean.torrc.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val idx = trimmed.indexOfFirst { it == ' ' || it == '=' || it == '\t' }
                    if (idx > 0) {
                        val key = trimmed.substring(0, idx).trim()
                        val value = trimmed.substring(idx + 1).trim()
                        map[key] = value
                    } else {
                        map[trimmed] = "1"
                    }
                }
            }
            if (map.isNotEmpty()) {
                torrc = map
            }
        }
    }
}

fun TorBean.toUri(): String {
    val builder = linkBuilder()
    builder.host("tor")

    if (!executablePath.isNullOrBlank()) builder.addQueryParameter("executable_path", executablePath)
    if (!dataDirectory.isNullOrBlank()) builder.addQueryParameter("data_directory", dataDirectory)
    if (!extraArgs.isNullOrBlank()) builder.addQueryParameter("extra_args", extraArgs.replace("\n", ","))
    if (!torrc.isNullOrBlank()) builder.addQueryParameter("torrc", torrc)

    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("tor", appendDefaultPort = false)
}

fun parseTor(url: String): TorBean {
    val link = url.replace("tor://", "https://").toHttpUrlOrNull()
        ?: error("Invalid Tor URL: $url")

    return TorBean().apply {
        initializeDefaultValues()
        if (!link.fragment.isNullOrEmpty()) {
            name = link.fragment!!
        }

        (link.queryParameter("executable_path") ?: link.queryParameter("exe") ?: link.queryParameter("bin"))?.let {
            executablePath = it
        }
        (link.queryParameter("data_directory") ?: link.queryParameter("data_dir"))?.let {
            dataDirectory = it
        }
        (link.queryParameter("extra_args") ?: link.queryParameter("args"))?.let {
            extraArgs = it.replace(",", "\n")
        }
        link.queryParameter("torrc")?.let {
            torrc = it
        }
    }
}

fun parseTorOutbound(map: Map<String, Any?>): TorBean = TorBean().apply {
    initializeDefaultValues()
    for ((key, value) in map) {
        if (value == null) continue
        when (key.replace("_", "-").lowercase()) {
            "tag", "name" -> name = value.toString()
            "executable-path", "executablepath", "exe-path", "bin" -> executablePath = value.toString()
            "data-directory", "datadirectory", "data-dir" -> dataDirectory = value.toString()
            "extra-args", "extraargs", "args" -> extraArgs = when (value) {
                is List<*> -> value.mapNotNull { it?.toString() }.joinToString("\n")
                else -> value.toString()
            }
            "torrc", "options" -> torrc = when (value) {
                is Map<*, *> -> value.entries.joinToString("\n") { "${it.key} ${it.value}" }
                else -> value.toString()
            }
        }
    }
}
