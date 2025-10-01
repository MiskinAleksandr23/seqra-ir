package org.seqra.ir.impl.storage.ers.ram

import org.seqra.ir.api.storage.ers.EntityRelationshipStorage
import org.seqra.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.seqra.ir.api.storage.ers.ErsSettings
import org.seqra.ir.impl.RamErsSettings

const val RAM_ERS_SPI = "org.seqra.ir.impl.storage.ers.ram.RAMEntityRelationshipStorageSPI"

class RAMEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = RAM_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage {
        require(persistenceLocation == null) { "RAM ERS can't be persisted" }
        require(settings is RamErsSettings) { "RamErsSettings is expected" }
        return RAMEntityRelationshipStorage(settings = settings)
    }
}