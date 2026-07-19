package dev.kmapx.ksp.contract

import dev.kmapx.ksp.GeneratedOutput
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.kmapx.codegen.PlanEmitter

/**
 * Koin— acumula los mappers `componentModel = KOIN` por paquete y, al final de la ronda,
 * emite UN módulo por paquete (`single<Interface> { Impl() }`). Única responsabilidad: la
 * agregación por paquete + su emisión, con estado propio (antes vivía suelto en `process()`).
 */
internal class KoinModuleWriter(
    private val emitter: PlanEmitter,
    private val output: GeneratedOutput,
) {
    private data class Entry(
        val interfaceSimpleName: String,
        val implSimpleName: String,
        /** Nº de converters inyectados en el constructor del impl. */
        val injectedDeps: Int,
        val decl: KSClassDeclaration,
    )

    private val byPackage = mutableMapOf<String, MutableList<Entry>>()

    fun add(pkg: String, interfaceSimpleName: String, implSimpleName: String, injectedDeps: Int, decl: KSClassDeclaration) {
        byPackage.getOrPut(pkg) { mutableListOf() } += Entry(interfaceSimpleName, implSimpleName, injectedDeps, decl)
    }

    fun flush() {
        byPackage.forEach { (pkg, impls) ->
            val file = emitter.emitKoinModule(pkg, impls.map { Triple(it.interfaceSimpleName, it.implSimpleName, it.injectedDeps) })
            output.writeMerged(file, impls.mapNotNull { it.decl.containingFile })
        }
    }
}
