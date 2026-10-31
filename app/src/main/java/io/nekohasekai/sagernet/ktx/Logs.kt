package io.nekohasekai.sagernet.ktx

import libcore.Libcore
import java.io.InputStream
import java.io.OutputStream

object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    private fun printLog(log: String) {
        try {
            Libcore.nekoLogPrintln(log)
        } catch (_: Throwable) {
            println(log)
        }
    }

    // level int use logrus.go

    fun d(message: String) {
        printLog("[Debug] [${mkTag()}] $message")
    }

    fun d(message: String, exception: Throwable) {
        printLog("[Debug] [${mkTag()}] $message\n${exception.stackTraceToString()}")
    }

    fun i(message: String) {
        printLog("[Info] [${mkTag()}] $message")
    }

    fun i(message: String, exception: Throwable) {
        printLog("[Info] [${mkTag()}] $message\n${exception.stackTraceToString()}")
    }

    fun w(message: String) {
        printLog("[Warning] [${mkTag()}] $message")
    }

    fun w(message: String, exception: Throwable) {
        printLog("[Warning] [${mkTag()}] $message\n${exception.stackTraceToString()}")
    }

    fun w(exception: Throwable) {
        printLog("[Warning] [${mkTag()}] ${exception.stackTraceToString()}")
    }

    fun e(message: String) {
        printLog("[Error] [${mkTag()}] $message")
    }

    fun e(message: String, exception: Throwable) {
        printLog("[Error] [${mkTag()}] $message\n${exception.stackTraceToString()}")
    }

    fun e(exception: Throwable) {
        printLog("[Error] [${mkTag()}] ${exception.stackTraceToString()}")
    }

}

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}