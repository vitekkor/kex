package org.jetbrains.research.kex.concolic

import org.jetbrains.research.kex.KexRunnerTest
import kotlin.math.round
import kotlin.system.measureTimeMillis
import kotlin.test.Test

class ObjectTest : KexRunnerTest() {

    @Test
    fun testBasicReachability() {
        val `class` = cm["$packageName/ObjectTests"]
        val time = measureTimeMillis {  concolic(`class`) }
        println("${round(time.toFloat() / (1000 * 60))} minutes")
    }

    @Test
    fun testJavaBasicReachability() {
        val `class` = cm["$packageName/ObjectJavaTests"]
        val time = measureTimeMillis {  concolic(`class`) }
        println("${round(time.toFloat() / (1000 * 60))} minutes")
    }

}