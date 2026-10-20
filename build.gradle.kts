// Top-level build file. Versions are pinned to the same toolchain the ARMSX2 core
// (core/ submodule) is built and tested with, so the native core compiles unchanged.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
