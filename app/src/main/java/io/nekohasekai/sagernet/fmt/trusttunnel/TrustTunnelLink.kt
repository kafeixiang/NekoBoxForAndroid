package io.nekohasekai.sagernet.fmt.trusttunnel

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import java.io.EOFException

private const val QUIC_VARINT_ONE_BYTE_MAX = 0x3FL
private const val QUIC_VARINT_TWO_BYTE_MAX = 0x3FFFL
private const val QUIC_VARINT_FOUR_BYTE_MAX = 0x3FFFFFFFL
private const val QUIC_VARINT_MAX = 0x3FFFFFFFFFFFFFFFL

private const val QUIC_VARINT_TWO_BYTE_PREFIX = 0x4000L
private const val QUIC_VARINT_FOUR_BYTE_PREFIX = 0x80000000L
private const val QUIC_VARINT_EIGHT_BYTE_PREFIX = -0x4000000000000000L

private const val QUIC_VARINT_SIZE_SHIFT = 6
private const val QUIC_VARINT_FIRST_PAYLOAD_MASK = 0x3F
private const val BYTE_MASK = 0xFF

fun BufferedSink.writeQuicVarint(value: Long) {
    require(value in 0..QUIC_VARINT_MAX) { "QUIC varint out of range: $value" }
    when {
        value <= QUIC_VARINT_ONE_BYTE_MAX -> writeByte(value.toInt())
        value <= QUIC_VARINT_TWO_BYTE_MAX -> writeShort((value or QUIC_VARINT_TWO_BYTE_PREFIX).toInt())
        value <= QUIC_VARINT_FOUR_BYTE_MAX -> writeInt((value or QUIC_VARINT_FOUR_BYTE_PREFIX).toInt())
        else -> writeLong(value or QUIC_VARINT_EIGHT_BYTE_PREFIX)
    }
}

fun BufferedSource.readQuicVarint(): Long {
    val first = readByte().toInt() and BYTE_MASK
    val byteCount = 1 shl (first ushr QUIC_VARINT_SIZE_SHIFT)
    var value = (first and QUIC_VARINT_FIRST_PAYLOAD_MASK).toLong()
    repeat(byteCount - 1) {
        value = (value shl Byte.SIZE_BITS) or (readByte().toLong() and BYTE_MASK.toLong())
    }
    return value
}

fun splitAddress(address: String): Pair<String, String>? {
    val separator = address.lastIndexOf(':')
    if (separator <= 0 || separator == address.lastIndex) return null
    val host = address.substring(0, separator)
    val bracketed = host.startsWith("[") && host.endsWith("]")
    if (!bracketed && ':' in host) return null
    val unwrappedHost = if (bracketed) host.substring(1, host.length - 1) else host
    return unwrappedHost to address.substring(separator + 1)
}

fun joinAddress(host: String, port: Int): String {
    return if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

internal data class TrustTunnelLink(
    val hostname: String,
    val addresses: List<String>,
    val customSni: String = "",
    val username: String,
    val password: String,
    val skipVerification: Boolean = false,
    val certificate: ByteString = ByteString.EMPTY,
    val http3: Boolean = false,
    val name: String = "",
) {
    init {
        require(hostname.isNotEmpty()) { "missing hostname" }
        require(addresses.isNotEmpty()) { "missing addresses" }
        addresses.forEachIndexed { index, address ->
            require(isValidAddress(address)) { "address [$index] is invalid" }
        }
        require(username.isNotEmpty()) { "missing username" }
        require(password.isNotEmpty()) { "missing password" }
    }

    fun build(): String {
        val records = Buffer()
        records.writeRecord(TAG_VERSION, Buffer().also { it.writeQuicVarint(VERSION_WRITTEN) }.readByteArray())
        records.writeRecord(TAG_HOSTNAME, hostname)
        addresses.forEach { records.writeRecord(TAG_ADDRESSES, it) }
        if (customSni.isNotEmpty()) records.writeRecord(TAG_CUSTOM_SNI, customSni)
        records.writeRecord(TAG_USERNAME, username)
        records.writeRecord(TAG_PASSWORD, password)
        if (skipVerification) records.writeRecord(TAG_SKIP_VERIFICATION, byteArrayOf(1))
        if (certificate.size > 0) records.writeRecord(TAG_CERTIFICATE, certificate.toByteArray())
        if (http3) records.writeRecord(TAG_UPSTREAM_PROTOCOL, byteArrayOf(UPSTREAM_HTTP3))
        if (name.isNotEmpty()) records.writeRecord(TAG_NAME, name)
        val encoded = records.readByteString().base64Url().trimEnd('=')
        return "$SCHEME_PREFIX?$encoded"
    }

    companion object {
        private const val SCHEME_PREFIX = "tt://"

        private const val TAG_VERSION = 0x00L
        private const val TAG_HOSTNAME = 0x01L
        private const val TAG_ADDRESSES = 0x02L
        private const val TAG_CUSTOM_SNI = 0x03L
        private const val TAG_USERNAME = 0x05L
        private const val TAG_PASSWORD = 0x06L
        private const val TAG_SKIP_VERIFICATION = 0x07L
        private const val TAG_CERTIFICATE = 0x08L
        private const val TAG_UPSTREAM_PROTOCOL = 0x09L
        private const val TAG_CLIENT_RANDOM_PREFIX = 0x0BL
        private const val TAG_NAME = 0x0CL
        private const val TAG_SUBSCRIPTION_URL = 0x0EL
        private const val TAG_CLIENT_RANDOM_AUTH_KEY = 0x0FL

        private const val VERSION_WRITTEN = 1L
        private const val VERSION_MAX_SUPPORTED = 2L

        private const val BOOL_FALSE: Byte = 0
        private const val BOOL_TRUE: Byte = 1
        private const val UPSTREAM_HTTP2: Byte = 1
        private const val UPSTREAM_HTTP3: Byte = 2

        private const val MAX_RANDOM_LENGTH = 32

        fun parse(link: String): TrustTunnelLink {
            require(link.startsWith(SCHEME_PREFIX)) { "schema is not tt" }
            val encoded = link.removePrefix(SCHEME_PREFIX).removePrefix("?")
            val records = encoded.decodeBase64()
                ?.let { Buffer().write(it) }
                ?: throw IllegalArgumentException("invalid base64")

            var hostname = ""
            val addresses = mutableListOf<String>()
            var customSni = ""
            var username = ""
            var password = ""
            var skipVerification = false
            var certificate = ByteString.EMPTY
            var http3 = false
            var name = ""
            var subscriptionUrl: String? = null
            try {
                while (!records.exhausted()) {
                    val tag = records.readQuicVarint()
                    val length = records.readQuicVarint()
                    require(length <= records.size) { "invalid length of tag $tag: $length" }
                    val value = records.readByteString(length)
                    when (tag) {
                        TAG_VERSION -> {
                            val version = Buffer().write(value).readQuicVarint()
                            require(version <= VERSION_MAX_SUPPORTED) { "unsupported version: $version" }
                        }

                        TAG_HOSTNAME -> hostname = value.strictUtf8(tag)
                        TAG_ADDRESSES -> {
                            val address = value.strictUtf8(tag)
                            require(isValidAddress(address)) { "invalid address: $address" }
                            addresses += address
                        }

                        TAG_CUSTOM_SNI -> customSni = value.strictUtf8(tag)
                        TAG_USERNAME -> username = value.strictUtf8(tag)
                        TAG_PASSWORD -> password = value.strictUtf8(tag)
                        TAG_SKIP_VERIFICATION -> skipVerification = value.bool(tag)
                        TAG_CERTIFICATE -> certificate = value
                        TAG_UPSTREAM_PROTOCOL -> {
                            http3 = when (val protocol = value.singleByte(tag)) {
                                UPSTREAM_HTTP2 -> false
                                UPSTREAM_HTTP3 -> true
                                else -> throw IllegalArgumentException("invalid upstream protocol: $protocol")
                            }
                        }

                        TAG_NAME -> name = value.strictUtf8(tag)
                        TAG_SUBSCRIPTION_URL -> {
                            val url = value.strictUtf8(tag)
                            require(url.startsWith("https://")) { "subscription URL is not https: $url" }
                            subscriptionUrl = url
                        }

                        TAG_CLIENT_RANDOM_PREFIX -> {
                            val parts = value.strictUtf8(tag).split("/")
                            require(parts.size <= 2) { "invalid random prefix format" }
                            parts.forEach {
                                val v = it.decodeHex()
                                require(v.size <= MAX_RANDOM_LENGTH) { "invalid random length: $v" }
                            }
                        }

                        TAG_CLIENT_RANDOM_AUTH_KEY -> {
                            value.strictUtf8(tag).decodeHex()
                        }

                        else -> Unit
                    }
                }
            } catch (_: EOFException) {
                throw IllegalArgumentException("truncated record")
            }
            val hasServer = hostname.isNotEmpty() && addresses.isNotEmpty() &&
                username.isNotEmpty() && password.isNotEmpty()
            require(hasServer || subscriptionUrl == null) { "subscription-only links are not supported" }
            return TrustTunnelLink(
                hostname = hostname,
                addresses = addresses,
                customSni = customSni,
                username = username,
                password = password,
                skipVerification = skipVerification,
                certificate = certificate,
                http3 = http3,
                name = name,
            )
        }

        private fun isValidAddress(address: String): Boolean {
            return splitAddress(address)?.second?.toIntOrNull()?.let { it in 1..65535 } != null
        }

        private fun ByteString.singleByte(tag: Long): Byte {
            require(size == 1) { "invalid length of tag $tag: $size" }
            return this[0]
        }

        private fun ByteString.bool(tag: Long): Boolean = when (val byte = singleByte(tag)) {
            BOOL_FALSE -> false
            BOOL_TRUE -> true
            else -> throw IllegalArgumentException("invalid boolean of tag $tag: $byte")
        }

        private fun ByteString.strictUtf8(tag: Long): String = try {
            toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("invalid UTF-8 in tag $tag")
        }

        private fun Buffer.writeRecord(tag: Long, value: String) {
            writeRecord(tag, value.encodeToByteArray())
        }

        private fun Buffer.writeRecord(tag: Long, value: ByteArray) {
            writeQuicVarint(tag)
            writeQuicVarint(value.size.toLong())
            write(value)
        }
    }
}
