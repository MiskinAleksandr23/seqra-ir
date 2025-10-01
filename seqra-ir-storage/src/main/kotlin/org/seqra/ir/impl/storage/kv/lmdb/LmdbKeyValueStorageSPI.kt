package org.seqra.ir.impl.storage.kv.lmdb

import org.seqra.ir.api.storage.ers.ErsSettings
import org.seqra.ir.api.storage.kv.PluggableKeyValueStorage
import org.seqra.ir.api.storage.kv.PluggableKeyValueStorageSPI
import kotlin.io.path.createTempDirectory

const val LMDB_KEY_VALUE_STORAGE_SPI = "org.seqra.ir.impl.storage.kv.lmdb.LmdbKeyValueStorageSPI"

class LmdbKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = LMDB_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage {
        return LmdbKeyValueStorage(
            location ?: createTempDirectory(prefix = "lmdbKeyValueStorage").toString(),
            if (settings is org.seqra.ir.impl.JIRLmdbErsSettings) settings else org.seqra.ir.impl.JIRLmdbErsSettings()
        )
    }
}