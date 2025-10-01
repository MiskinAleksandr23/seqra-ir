package org.seqra.ir.testing

import kotlinx.coroutines.runBlocking
import org.seqra.ir.impl.features.Builders
import org.seqra.ir.impl.features.InMemoryHierarchy
import org.seqra.ir.impl.features.Usages
import org.seqra.ir.impl.features.hierarchyExt
import org.seqra.ir.impl.seqraIrDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class HierarchyStabilityTest {

    companion object {
        private var listInheritorsCount: Int = 0
        private var setInheritorsCount: Int = 0

        @BeforeAll
        @JvmStatic
        fun setup() {
            val (sets, lists) = runBlocking { run(global = true) }
            listInheritorsCount = lists
            setInheritorsCount = sets
        }

        private suspend fun run(global: Boolean): Pair<Int, Int> {

            val db = when {
                global -> WithDb(Usages, Builders, InMemoryHierarchy()).db
                else -> seqraIrDb {
                    useProcessJavaRuntime()
                    loadByteCode(allJars)
                    installFeatures()
                }
            }
            val jIRClasspath = db.classpath(allJars)
            val hierarchy = jIRClasspath.hierarchyExt()

            val setSubclasses = hierarchy.findSubClasses(
                "java.util.Set",
                entireHierarchy = true, includeOwn = true
            ).toSet()
            val listSubclasses = hierarchy.findSubClasses(
                "java.util.List",
                entireHierarchy = true, includeOwn = true
            ).toSet()

            if (!global) {
                jIRClasspath.db.close()
            }
            return setSubclasses.size to listSubclasses.size
        }

    }

    @Test
    fun `should be ok`() {
        val (sets, lists) = runBlocking { run(global = false) }
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

    @Test
    fun `should ok with in-memory feature`() {
        val (sets, lists) = runBlocking { run(global = true) }
        assertEquals(listInheritorsCount, lists)
        assertEquals(setInheritorsCount, sets)
    }

}