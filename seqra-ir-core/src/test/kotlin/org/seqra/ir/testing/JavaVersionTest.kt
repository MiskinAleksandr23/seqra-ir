package org.seqra.ir.testing

import org.seqra.ir.api.jvm.JIRSettings
import org.seqra.ir.impl.fs.JavaRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE

class JavaVersionTest {

    @Test
    @EnabledOnJre(JRE.JAVA_11)
    fun `java version should be proper for 11 java`() {
        assertEquals(11, JavaRuntime(JIRSettings().useProcessJavaRuntime().jre).version.majorVersion)
    }
    @Test
    @EnabledOnJre(JRE.JAVA_8)
    fun `java version should be proper for 8 java`() {
        assertEquals(8, JavaRuntime(JIRSettings().useProcessJavaRuntime().jre).version.majorVersion)
    }

}