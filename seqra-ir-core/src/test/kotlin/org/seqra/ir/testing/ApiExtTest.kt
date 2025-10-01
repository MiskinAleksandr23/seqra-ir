package org.seqra.ir.testing

import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.JIRType
import org.seqra.ir.api.jvm.ext.autoboxIfNeeded
import org.seqra.ir.api.jvm.ext.findClass
import org.seqra.ir.api.jvm.ext.findTypeOrNull
import org.seqra.ir.api.jvm.ext.isSubClassOf
import org.seqra.ir.api.jvm.ext.short
import org.seqra.ir.api.jvm.ext.unboxIfNeeded
import org.seqra.ir.testing.hierarchies.Creature
import org.seqra.ir.testing.hierarchies.Creature.Animal
import org.seqra.ir.testing.hierarchies.Creature.Bird
import org.seqra.ir.testing.hierarchies.Creature.Dinosaur
import org.seqra.ir.testing.hierarchies.Creature.DinosaurImpl
import org.seqra.ir.testing.hierarchies.Creature.Fish
import org.seqra.ir.testing.hierarchies.Creature.Pterodactyl
import org.seqra.ir.testing.hierarchies.Creature.TRex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class ApiExtTest : BaseTest() {

    companion object : WithGlobalDbImmutable()

    @Test
    fun `unboxing primitive type`() {
        val clazz = typeOf<java.lang.Short>()
        assertEquals(cp.short, clazz.unboxIfNeeded())
    }

    @Test
    fun `unboxing regular type`() {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.unboxIfNeeded())
    }

    @Test
    fun `autoboxing primitive type`() {
        val type = cp.findTypeOrNull("short")

        assertEquals(typeOf<java.lang.Short>(), type?.autoboxIfNeeded())
    }

    @Test
    fun `autoboxing regular type`() {
        val clazz = typeOf<String>()
        assertEquals(clazz, clazz.autoboxIfNeeded())
    }

    @Test
    fun `isSubtype for regular classes`() = runBlocking {
        assertTrue(classOf<Dinosaur>() isSubClassOf classOf<Creature>())

        assertFalse(classOf<Dinosaur>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<Creature>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<Animal>())
        assertTrue(classOf<TRex>() isSubClassOf classOf<DinosaurImpl>())

        assertFalse(classOf<TRex>() isSubClassOf classOf<Fish>())
        assertFalse(classOf<Pterodactyl>() isSubClassOf classOf<Fish>())
        assertTrue(classOf<Pterodactyl>() isSubClassOf classOf<Bird>())
    }

    private inline fun <reified T> typeOf(): JIRType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }

    private inline fun <reified T> classOf(): JIRClassOrInterface {
        return cp.findClass<T>()
    }
}
