package org.jetbrains.research.kex.sbst

import java.io.File

interface Tool {
    fun initialize(src: File, bin: File, classPath: List<File>)
    fun getExtraClassPath(): List<File>
    fun run(className: String)
    fun finalize()
}