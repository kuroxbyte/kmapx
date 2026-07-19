package dev.kmapx.codegen

/**
 * The backend's output contract. The frontend writes [content] to KSP's
 * CodeGenerator without knowing HOW it was produced (KotlinPoet is an implementation
 * detail of this module; a future IR backend implements the same contract).
 */
public data class GeneratedFile(
    val packageName: String,
    /** Without the `.kt` extension (mirrors KSP's CodeGenerator API). */
    val fileName: String,
    val content: String,
)
