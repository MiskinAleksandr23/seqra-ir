import org.seqra.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    api(project(":seqra-ir-api-common"))
    api(project(":seqra-ir-api-storage"))

    api(Libs.asm)
    api(Libs.asm_tree)
    api(Libs.asm_commons)
    api(Libs.asm_util)

    api(KotlinDependency.Libs.kotlinx_coroutines_core)
}
