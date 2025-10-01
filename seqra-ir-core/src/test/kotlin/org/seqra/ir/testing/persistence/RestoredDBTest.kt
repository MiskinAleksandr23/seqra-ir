package org.seqra.ir.testing.persistence

import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.api.jvm.JIRPersistenceImplSettings
import org.seqra.ir.api.jvm.ext.HierarchyExtension
import org.seqra.ir.impl.JIRXodusKvErsSettings
import org.seqra.ir.impl.features.hierarchyExt
import org.seqra.ir.testing.LifecycleTest
import org.seqra.ir.testing.WithRestoredDb
import org.seqra.ir.testing.allClasspath
import org.seqra.ir.testing.tests.DatabaseEnvTest
import org.seqra.ir.testing.withDb

@LifecycleTest
open class RestoredDBTest : DatabaseEnvTest() {

    companion object : WithRestoredDb()

    override val cp: JIRClasspath by lazy {
        runBlocking {
            val withDB = this@RestoredDBTest.javaClass.withDb
            withDB.db.classpath(allClasspath)
        }
    }

    override val hierarchyExt: HierarchyExtension by lazy { runBlocking { cp.hierarchyExt() } }
}

@LifecycleTest
class RestoredXodusDBTest : RestoredDBTest() {

    companion object : WithRestoredDb() {

        override val implSettings: JIRPersistenceImplSettings get() = JIRXodusKvErsSettings
    }
}