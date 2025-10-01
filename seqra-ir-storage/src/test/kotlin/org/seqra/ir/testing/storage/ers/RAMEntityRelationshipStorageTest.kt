package org.seqra.ir.testing.storage.ers

import org.seqra.ir.impl.RamErsSettings
import org.seqra.ir.impl.storage.ers.ram.RAM_ERS_SPI

class RAMEntityRelationshipStorageTest : EntityRelationshipStorageTest() {

    override val ersSettings = RamErsSettings()

    override val ersId: String
        get() = RAM_ERS_SPI
}
