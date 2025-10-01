package org.seqra.ir.api.jvm

interface Hook {

    suspend fun afterStart()

    fun afterStop() {}
}
