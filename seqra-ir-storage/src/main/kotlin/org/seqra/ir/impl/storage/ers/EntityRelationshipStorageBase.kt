package org.seqra.ir.impl.storage.ers

import org.seqra.ir.api.storage.ers.EntityRelationshipStorage
import org.seqra.ir.impl.storage.ers.ram.toImmutable

abstract class EntityRelationshipStorageBase : EntityRelationshipStorage {

    override fun asImmutable(databaseId: String): EntityRelationshipStorage = toImmutable()
}