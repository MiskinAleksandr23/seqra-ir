package org.seqra.ir.impl.vfs

import org.seqra.ir.api.jvm.ClassSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    internal val source: ClassSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode) {

    val location get() = source.location

}
