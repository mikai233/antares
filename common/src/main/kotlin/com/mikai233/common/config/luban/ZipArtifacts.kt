package com.mikai233.common.config.luban

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

fun unpackZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    return linkedMapOf<String, ByteArray>().also { filesByPath ->
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    filesByPath[entry.name] = zip.readAllBytes()
                }
                zip.closeEntry()
            }
        }
    }
}
