plugins {
    alias(libs.plugins.detekt) apply false
    id("com.sanjaya.buildlogic.jvm.koin") apply false
    id("com.sanjaya.buildlogic.jvm.serialization") apply false
    id("com.sanjaya.buildlogic.jvm.test") apply false
}
