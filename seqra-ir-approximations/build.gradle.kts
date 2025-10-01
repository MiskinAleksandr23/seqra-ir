import org.seqra.common.KotlinDependency

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(project(":seqra-ir-api-jvm"))
    implementation(project(":seqra-ir-core"))
//    implementation(Libs.jooq)

    testImplementation(testFixtures(project(":seqra-ir-core")))
    testImplementation(testFixtures(project(":seqra-ir-storage")))
    testImplementation(KotlinDependency.Libs.kotlin_logging)
//    testRuntimeOnly(Libs.guava)
}
