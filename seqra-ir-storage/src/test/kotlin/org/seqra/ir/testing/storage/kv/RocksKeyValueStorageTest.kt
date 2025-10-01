package org.seqra.ir.testing.storage.kv

import org.seqra.ir.impl.storage.kv.rocks.ROCKS_KEY_VALUE_STORAGE_SPI

class RocksKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = ROCKS_KEY_VALUE_STORAGE_SPI
}
