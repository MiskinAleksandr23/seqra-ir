package org.seqra.ir.impl.storage

import org.seqra.ir.api.jvm.JIRDatabase
import org.seqra.ir.api.jvm.JIRDatabasePersistence
import org.seqra.ir.api.jvm.JIRSettings
import org.seqra.ir.impl.JIRDatabaseImpl
import org.seqra.ir.impl.JIRDatabasePersistenceSPI
import org.seqra.ir.impl.LocationsRegistry
import org.seqra.ir.impl.fs.JavaRuntime

const val SQLITE_DATABASE_PERSISTENCE_SPI = "org.seqra.ir.impl.storage.SQLiteDatabasePersistenceSPI"

class SQLiteDatabasePersistenceSPI : JIRDatabasePersistenceSPI {

    override val id = SQLITE_DATABASE_PERSISTENCE_SPI

    override fun newPersistence(runtime: JavaRuntime, settings: JIRSettings): JIRDatabasePersistence {
        return SQLitePersistenceImpl(
            javaRuntime = runtime,
            location = settings.persistenceLocation,
            clearOnStart = settings.persistenceClearOnStart ?: false
        )
    }

    override fun newLocationsRegistry(jIRdb: JIRDatabase): LocationsRegistry {
        return PersistentLocationsRegistry(jIRdb as JIRDatabaseImpl)
    }
}