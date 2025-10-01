package org.seqra.ir.testing.persistence

import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.jvm.JIRSettings
import org.seqra.ir.impl.JIRSQLitePersistenceSettings
import org.seqra.ir.impl.features.Builders
import org.seqra.ir.impl.features.Usages
import org.seqra.ir.impl.fs.JavaRuntime
import org.seqra.ir.impl.seqraIrDb
import org.seqra.ir.impl.storage.LocationState
import org.seqra.ir.impl.storage.SQLitePersistenceImpl
import org.seqra.ir.impl.storage.dslContext
import org.seqra.ir.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.seqra.ir.testing.LifecycleTest
import org.seqra.ir.testing.allClasspath
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files


@LifecycleTest
class IncompleteDataTest {

    companion object {
        private val jdbcLocation = Files.createTempFile("jIRdb-", null).toFile().absolutePath
        private val javaHome: File = JIRSettings().useProcessJavaRuntime().jre
        val db = newDB(true).also {
            it.close()
        }

        private fun newDB(awaitBackground: Boolean) = runBlocking {
            seqraIrDb {
                useProcessJavaRuntime()
                persistent(jdbcLocation)
                installFeatures(Usages, Builders)
                loadByteCode(allClasspath)
                persistenceImpl(JIRSQLitePersistenceSettings)
            }.also {
                if (awaitBackground) {
                    it.awaitBackgroundJobs()
                }
            }
        }
    }

    @Test
    fun `if runtime is not processed schema should be dropped`() {
        withPersistence { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.AWAITING_INDEXING.ordinal)
                .execute()
        }
        val db = newDB(true)
        db.persistence.read {
            val count = it.dslContext.fetchCount(
                BYTECODELOCATIONS,
                BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal)
            )
            assertEquals(0, count)
        }
    }

    @Test
    fun `if runtime is processed unprocessed libraries should be outdated`() {
        val ids = arrayListOf<Long>()
        withPersistence { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.AWAITING_INDEXING.ordinal)
                .where(BYTECODELOCATIONS.RUNTIME.isFalse)
                .execute()
            jooq.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.RUNTIME.isFalse)
                .fetch {
                    ids.add(it.id!!)
                }
        }
        val db = newDB(true)
        db.persistence.read {
            it.dslContext.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                .fetch {
                    assertTrue(
                        ids.contains(it.id!!),
                        "expected ${it.path} to be in PROCESSED state buy is in ${LocationState.values()[it.state!!]}"
                    )

                }
        }
    }


    private fun withPersistence(action: (DSLContext) -> Unit) {
        val persistence = SQLitePersistenceImpl(
            JavaRuntime(javaHome), false, jdbcLocation
        )
        persistence.use {
            it.write {
                action(it.dslContext)
            }
        }
    }

}