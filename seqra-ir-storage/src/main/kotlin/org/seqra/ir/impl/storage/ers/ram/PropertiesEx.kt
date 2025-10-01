package org.seqra.ir.impl.storage.ers.ram

import org.seqra.ir.util.ByteArrayBuilder
import java.io.InputStream

internal fun PropertiesMutable.toImmutable(builder: ByteArrayBuilder): PropertiesImmutable {
    return PropertiesImmutable(props.beginRead().toAttributesImmutable(builder))
}

internal fun InputStream.readPropertiesImmutable(): PropertiesImmutable = PropertiesImmutable(
    attributes = readAttributesImmutable()
)