package io.nekohasekai.sagernet.fmt.trusttunnel

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

private const val PEM_BEGIN_PREFIX = "-----BEGIN "
private const val PEM_END_PREFIX = "-----END "
private const val PEM_LINE_LENGTH = 64
private const val PEM_TYPE_CERTIFICATE = "CERTIFICATE"

private const val ASN1_SEQUENCE: Byte = 0x30
private const val ASN1_LONG_FORM_FLAG = 0x80
private const val ASN1_LONG_FORM_LENGTH_SIZE_MASK = 0x7F
private const val ASN1_MAX_LENGTH_SIZE = 3

fun decodePemBlocks(pem: String): List<ByteString> {
    val blocks = mutableListOf<ByteString>()
    var body: StringBuilder? = null
    for (line in pem.lineSequence().map(String::trim)) {
        when {
            line.startsWith(PEM_BEGIN_PREFIX) -> body = StringBuilder()
            line.startsWith(PEM_END_PREFIX) -> {
                body?.toString()?.decodeBase64()?.let { blocks += it }
                body = null
            }
            else -> body?.append(line)
        }
    }
    return blocks
}

fun encodePemBlock(type: String, der: ByteString): String = buildString {
    append(PEM_BEGIN_PREFIX).append(type).append("-----\n")
    der.base64().chunked(PEM_LINE_LENGTH).forEach { append(it).append('\n') }
    append(PEM_END_PREFIX).append(type).append("-----\n")
}

internal fun certificateChainToPem(chain: ByteString): String {
    return splitDerCertificates(chain).joinToString("") { encodePemBlock(PEM_TYPE_CERTIFICATE, it) }
}

internal fun pemToCertificateChain(pem: String): ByteString {
    val certificates = decodePemBlocks(pem)
    require(certificates.isNotEmpty()) { "no PEM block in certificate" }
    val chain = Buffer()
    certificates.forEach { chain.write(it) }
    return chain.readByteString()
}

internal fun splitDerCertificates(chain: ByteString): List<ByteString> {
    val certificates = mutableListOf<ByteString>()
    var start = 0
    while (start < chain.size) {
        require(chain[start] == ASN1_SEQUENCE) { "no ASN.1 SEQUENCE at offset $start" }
        var cursor = start + 1
        require(cursor < chain.size) { "truncated DER certificate" }
        val lengthByte = chain[cursor++].toInt() and 0xFF
        val bodyLength = if (lengthByte and ASN1_LONG_FORM_FLAG == 0) {
            lengthByte
        } else {
            val lengthSize = lengthByte and ASN1_LONG_FORM_LENGTH_SIZE_MASK
            require(lengthSize in 1..ASN1_MAX_LENGTH_SIZE && cursor + lengthSize <= chain.size) {
                "invalid ASN.1 length at offset $start"
            }
            var length = 0
            repeat(lengthSize) { length = (length shl 8) or (chain[cursor++].toInt() and 0xFF) }
            length
        }
        val end = cursor + bodyLength
        require(end <= chain.size) { "truncated DER certificate" }
        certificates += chain.substring(start, end)
        start = end
    }
    return certificates
}
