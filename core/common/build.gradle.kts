plugins {
  id("com.android.library")
  id("kotlin-android")
}

android {
  namespace = "com.teixeira.vcspace.common"

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
}

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.preference)
  implementation(libs.google.material)
  implementation(libs.google.gson)
  implementation(libs.common.utilcode)
  implementation(project(":core:resources"))
}