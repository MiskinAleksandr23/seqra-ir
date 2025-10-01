
@file:JvmName("Diagnostics")
package org.seqra.ir.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.seqra.ir.api.jvm.JIRClasspath
import org.seqra.ir.impl.storage.dslContext
import org.seqra.ir.impl.storage.ers.filterDeleted
import org.seqra.ir.impl.storage.execute
import org.seqra.ir.impl.storage.jooq.tables.references.CLASSES
import org.seqra.ir.impl.storage.jooq.tables.references.SYMBOLS
import org.seqra.ir.impl.storage.txn
import org.jooq.impl.DSL

/**
 * finds out duplicates classes
 *
 * @return map with name and count of classes
 */
suspend fun JIRClasspath.duplicatedClasses(): Map<String, Int> {
    db.awaitBackgroundJobs()
    val persistence = db.persistence
    return persistence.read { context ->
        context.execute(
            sqlAction = {
                context.dslContext.select(SYMBOLS.NAME, DSL.count(SYMBOLS.NAME)).from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(CLASSES.LOCATION_ID.`in`(registeredLocationIds))
                    .groupBy(SYMBOLS.NAME)
                    .having(DSL.count(SYMBOLS.NAME).greaterThan(1))
                    .fetch()
                    .map { (name, count) -> name!! to count!! }
                    .toMap()
            },
            noSqlAction = {
                val result = mutableMapOf<String, Int>().also { result ->
                    context.txn.all("Class").filterDeleted().forEach { clazz ->
                        val className = persistence.findSymbolName(clazz.getCompressed<Long>("nameId")!!)
                        result[className] = result.getOrDefault(className, 0) + 1
                    }
                }
                result.filterKeys { className -> result[className]!! > 1 }
            }
        )
    }
}

fun JIRClasspath.asyncDuplicatedClasses() = GlobalScope.future { duplicatedClasses() }
