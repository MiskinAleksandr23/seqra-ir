package org.seqra.ir.testing.storage.ers

import org.seqra.ir.impl.JIRKvErsSettings
import org.seqra.ir.impl.storage.ers.kv.KV_ERS_SPI
import org.seqra.ir.impl.storage.kv.lmdb.LMDB_KEY_VALUE_STORAGE_SPI

class LmdbKVEntityRelationshipStorageTest : EntityRelationshipStorageTest() {

    override val ersSettings = JIRKvErsSettings(LMDB_KEY_VALUE_STORAGE_SPI)

    override val ersId = KV_ERS_SPI
}