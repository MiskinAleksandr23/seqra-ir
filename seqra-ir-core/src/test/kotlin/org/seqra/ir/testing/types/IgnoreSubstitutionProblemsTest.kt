package org.seqra.ir.testing.types

import kotlinx.coroutines.runBlocking
import org.seqra.ir.api.jvm.JIRClassOrInterface
import org.seqra.ir.api.jvm.ext.cfg.fieldRef
import org.seqra.ir.api.jvm.ext.findClass
import org.seqra.ir.api.jvm.ext.packageName
import org.seqra.ir.api.jvm.ext.toType
import org.seqra.ir.impl.bytecode.JIRDatabaseClassWriter
import org.seqra.ir.impl.types.substition.IgnoreSubstitutionProblems
import org.seqra.ir.testing.BaseTest
import org.seqra.ir.testing.WithDb
import org.seqra.ir.testing.WithSQLiteDb
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.nio.file.Files

open class IgnoreSubstitutionProblemsTest : BaseTest() {

    // NB! Cannot use WithDbImmutable here since new location is being loaded in test
    companion object : WithDb(IgnoreSubstitutionProblems)

    private val target = Files.createTempDirectory("jIRdb-temp")

    @Test
    fun `should work when params number miss match`() {
        val modifiedType = tweakClass {
            signature = "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/lang/Object;"
        }.toType()
        modifiedType.methods.forEach {
            it.parameters
            it.typeParameters
            it.returnType
            it.method.instList.forEach {
                it.fieldRef?.field?.type
            }
        }
    }

    private fun tweakClass(action: ClassNode.() -> Unit): JIRClassOrInterface {
        cp.findClass("GenericsApi").tweakClass(action)
        cp.findClass("GenericsApiConsumer").tweakClass()
        runBlocking {
            cp.db.load(target.toFile())
        }
        return runBlocking {
            db.classpath(listOf(target.toFile()), listOf(IgnoreSubstitutionProblems)).findClass("GenericsApiConsumer")
        }
    }

    private fun JIRClassOrInterface.tweakClass(action: ClassNode.() -> Unit = {}): Unit = withAsmNode { classNode ->
        classNode.action()
        val cw = JIRDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(cw)
        classNode.accept(checker)
        val targetDir = target.resolve(packageName.replace('.', '/'))
        val targetFile = targetDir.resolve("${simpleName}.class").toFile().also {
            it.parentFile?.mkdirs()
        }
        targetFile.writeBytes(cw.toByteArray())
    }
}

class IgnoreSubstitutionProblemsSQLiteTest : IgnoreSubstitutionProblemsTest() {
    companion object : WithSQLiteDb(IgnoreSubstitutionProblems)
}
