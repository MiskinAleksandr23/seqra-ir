package org.seqra.ir.testing.caches

import org.seqra.ir.impl.caches.guava.GUAVA_CACHE_PROVIDER_ID

class GuavaCacheTest : PluggableCacheTest() {

    override val cacheId: String = GUAVA_CACHE_PROVIDER_ID
}