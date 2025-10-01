package org.seqra.ir.testing.storage.ers

import org.seqra.ir.impl.JIRKvErsSettings
import org.seqra.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.seqra.ir.impl.storage.kv.rocks.ROCKS_KEY_VALUE_STORAGE_SPI

class RocksKVEntityRelationshipStorageTest : EntityRelationshipStorageTest() {

    override val ersSettings = JIRKvErsSettings(ROCKS_KEY_VALUE_STORAGE_SPI)

    override val ersId = KV_ERS_SPI
}
