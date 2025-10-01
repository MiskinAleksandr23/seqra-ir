package org.seqra.ir.testing.caches

import org.seqra.ir.impl.caches.xodus.XODUS_CACHE_PROVIDER_ID

class XodusCacheTest : PluggableCacheTest() {

    override val cacheId = XODUS_CACHE_PROVIDER_ID
}