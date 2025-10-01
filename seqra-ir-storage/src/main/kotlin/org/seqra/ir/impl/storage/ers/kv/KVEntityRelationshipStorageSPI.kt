package org.seqra.ir.impl.storage.ers.kv

import org.seqra.ir.api.storage.ers.EntityRelationshipStorage
import org.seqra.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.seqra.ir.api.storage.ers.ErsSettings
import org.seqra.ir.api.storage.kv.PluggableKeyValueStorageSPI
import org.seqra.ir.impl.JIRKvErsSettings

const val KV_ERS_SPI = "org.seqra.ir.impl.storage.ers.kv.KVEntityRelationshipStorageSPI"

/**
 * Service provider interface for creating instances of [org.seqra.ir.api.storage.ers.EntityRelationshipStorage]
 * running atop of an instance of [org.seqra.ir.api.storage.kv.PluggableKeyValueStorage] identified by its id.
 */
class KVEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = KV_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage {
        settings as JIRKvErsSettings
        val kvSpi = PluggableKeyValueStorageSPI.getProvider(settings.kvId)
        val kvStorage = kvSpi.newStorage(persistenceLocation, settings)
        kvStorage.isMapWithKeyDuplicates = { mapName -> mapName.isMapWithKeyDuplicates }
        return KVEntityRelationshipStorage(kvStorage)
    }
}