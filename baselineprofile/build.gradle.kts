plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "app.arju.mpvgo.baselineprofile"
  compileSdk = 37

  defaultConfig {
    minSdk = 34
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["targetAppId"] = "io.github.arju22.mpvgo"
  }

  // Only the GitHub (standard) APK is profiled; other app flavors fall back to it.
  flavorDimensions += "distribution"
  productFlavors {
    create("standard") { dimension = "distribution" }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  targetProjectPath = ":app"
}

baselineProfile {
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
