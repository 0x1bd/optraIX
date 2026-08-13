package org.kvxd.optraix

object Log {

    fun info(scope: String, message: String) {
        println("[$scope] $message")
    }

    fun warn(scope: String, message: String) {
        System.err.println("[$scope] $message")
    }

    fun error(scope: String, message: String, cause: Throwable) {
        System.err.println("[$scope] $message: ${describe(cause)}")
        cause.printStackTrace()
    }

    fun describe(cause: Throwable): String {
        val name = cause::class.qualifiedName ?: cause::class.simpleName ?: "error"
        val message = cause.message
        return if (message.isNullOrBlank()) name else "$name: $message"
    }
}
