package io.nekohasekai.sagernet.fmt.tor

import io.nekohasekai.sagernet.ktx.blankAsNull
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma

fun buildSingBoxOutboundTorBean(bean: TorBean): SingBoxOptions.Outbound_TorOptions {
    return SingBoxOptions.Outbound_TorOptions().apply {
        type = "tor"
        executable_path = bean.executablePath.blankAsNull()
        data_directory = bean.dataDirectory.blankAsNull()
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
