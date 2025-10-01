package org.seqra.ir.impl.fs

import org.seqra.ir.api.jvm.JavaVersion

private class JavaVersionImpl(override val majorVersion: Int) : JavaVersion

fun parseRuntimeVersion(version: String): JavaVersion {
    return when {
        version.startsWith("1.") -> org.seqra.ir.impl.fs.JavaVersionImpl(version.substring(2, 3).toInt())
        else -> {
            val dot = version.indexOf(".")
            if (dot != -1) {
                org.seqra.ir.impl.fs.JavaVersionImpl(version.substring(0, dot).toInt())
            } else {
                org.seqra.ir.impl.fs.JavaVersionImpl(8)
            }
        }
    }
}
