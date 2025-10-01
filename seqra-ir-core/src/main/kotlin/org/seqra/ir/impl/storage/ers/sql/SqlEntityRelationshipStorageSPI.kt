package org.seqra.ir.impl.storage.ers.sql

import org.seqra.ir.api.storage.ers.ErsSettings
import org.seqra.ir.api.storage.ers.EntityRelationshipStorage
import org.seqra.ir.api.storage.ers.EntityRelationshipStorageSPI
import org.seqra.ir.impl.storage.configuredSQLiteDataSource
import org.seqra.ir.impl.storage.ers.BuiltInBindingProvider

const val SQL_ERS_SPI = "org.seqra.ir.impl.storage.ers.sql.SQLEntityRelationshipStorageSPI"

class SqlEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = SQL_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage =
        SqlEntityRelationshipStorage(
            dataSource = configuredSQLiteDataSource(location = persistenceLocation),
            bindingProvider = BuiltInBindingProvider
        )
}
