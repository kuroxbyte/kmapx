package dev.kmapx.ksp.contract

import dev.kmapx.ksp.Ann
import dev.kmapx.ksp.DeclarationHandler
import dev.kmapx.ksp.FrontendContext
import dev.kmapx.ksp.IgnoreAudit
import dev.kmapx.ksp.PlanReferences
import dev.kmapx.ksp.ResolvedSource
import dev.kmapx.ksp.qualifiedName
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.kmapx.codegen.ReportEmitter
import dev.kmapx.core.diagnostics.Diagnostics
import dev.kmapx.core.diagnostics.MLocation
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MParam
import dev.kmapx.core.plan.MapperImplPlan
import dev.kmapx.core.plan.MapperMethod
import dev.kmapx.core.plan.MethodBody

/**
 * Modo interfaz `@Mapper` (dominio limpio). ORQUESTA: valida la forma de la
 * interfaz, clasifica cada método por su FORMA ([MethodShape], Strategy) y delega
 * en el resolver de esa forma; al final emite el impl, el reporte de cobertura y el módulo Koin.
 * El "cómo" de cada forma vive en [MapperMethods.kt](MappingMethodResolver y compañía).
 */
internal class MapperHandler : DeclarationHandler {
    override val annotation = Ann.MAPPER

    override fun handle(decl: KSClassDeclaration, ctx: FrontendContext): ResolvedSource? {
        val mapper = decl
        val location = MLocation(mapper.qualifiedName?.asString() ?: mapper.simpleName.asString())

        if (mapper.classKind != ClassKind.INTERFACE) {
            ctx.reporter.report(Diagnostics.unsupportedMapperShape(location, "@Mapper only applies to interfaces"), mapper)
            return null
        }
        if (mapper.typeParameters.isNotEmpty()) {
            ctx.reporter.report(Diagnostics.unsupportedMapperShape(location, "generic @Mapper interfaces are not supported in v1"), mapper)
            return null
        }
        val extendsMapper = mapper.superTypes.any { superType ->
            (superType.resolve().declaration as? KSClassDeclaration)
                ?.annotations?.any { it.qualifiedName() == Ann.MAPPER } == true
        }
        if (extendsMapper) {
            ctx.reporter.report(Diagnostics.unsupportedMapperShape(location, "inheritance between @Mapper interfaces is not supported in v1"), mapper)
            return null
        }

        val declaredFunctions = mapper.getDeclaredFunctions().toList()
        val abstractMethods = declaredFunctions.filter { it.isAbstract }

        // Settings efectivos (fundidos con el profile @MapperConfig si lo hay).
        val optIns = ctx.reader.mapperOptIns(mapper)
        val componentModel = optIns.componentModel
        // El framework del componentModel debe estar en el classpath — KMX030, nunca un
        // error críptico del compilador sobre el código generado.
        val frameworkProbe = when (componentModel) {
            Emission.Component.SPRING ->
                "org.springframework.stereotype.Component" to "org.springframework:spring-context"
            Emission.Component.KOIN ->
                "org.koin.core.module.Module" to "io.insert-koin:koin-core"
            Emission.Component.NONE -> null
        }
        if (frameworkProbe != null &&
            ctx.resolver.getClassDeclarationByName(ctx.resolver.getKSNameFromString(frameworkProbe.first)) == null
        ) {
            ctx.reporter.report(Diagnostics.frameworkMissing(location, componentModel.name, frameworkProbe.second), mapper)
            return null
        }

        val methodContext = MapperMethodContext(
            ctx = ctx,
            mapper = mapper,
            location = location,
            abstractMethods = abstractMethods,
            defaultFunctions = declaredFunctions.filterNot { it.isAbstract },
            // Config heredada de otro @Mapper (por nombre de método); la propia gana por campo.
            baseMethods = ctx.reader.inheritFromOf(mapper)
                ?.getDeclaredFunctions()?.filter { it.isAbstract }
                ?.associateBy { it.simpleName.asString() }
                ?: emptyMap(),
            optIns = optIns,
            // Los targets de los métodos son heterogéneos — un nombre de la lista `ignore`
            // de la interfaz debe existir EN AL MENOS UNO (validación diferida al final del loop).
            ignoreAudit = IgnoreAudit(),
        )
        val mapping = MappingMethodResolver(methodContext)
        val patch = PatchMethodResolver(methodContext)
        val inverse = InverseMethodResolver(methodContext)
        val collection = CollectionMethodResolver(methodContext)

        val methods = mutableListOf<MapperMethod>()
        var anyInvalid = false

        for (method in abstractMethods) {
            val methodName = method.simpleName.asString()
            val sourceParam = method.parameters.firstOrNull()
            val sourceDecl = sourceParam?.type?.resolve()?.declaration as? KSClassDeclaration
            val returnDecl = method.returnType?.resolve()?.declaration as? KSClassDeclaration
            if (sourceParam == null || sourceDecl == null || returnDecl == null) {
                ctx.reporter.report(
                    Diagnostics.internalError(
                        location.copy(member = methodName),
                        "mapper method must have a class source parameter and a class return type",
                    ),
                    method,
                )
                anyInvalid = true
                continue
            }

            val parameters = method.parameters.map {
                MParam(it.name?.asString() ?: "<unnamed>", ctx.translator.translateType(it.type))
            }
            val returns = ctx.translator.translateType(method.returnType!!)

            val resolved = when (val shape = MethodShape.of(method, sourceDecl, returnDecl, parameters.first().type, returns)) {
                is MethodShape.Inverse ->
                    inverse.resolve(method, shape.forwardName, parameters, returns)
                MethodShape.Patch ->
                    patch.resolve(method, sourceDecl, parameters, returns)
                MethodShape.InvalidPatchArity -> {
                    ctx.reporter.report(
                        Diagnostics.unsupportedMapperShape(
                            location.copy(member = methodName),
                            "a method returning its first parameter type is a PATCH and must have " +
                                "exactly the shape fun $methodName(target: T, patch: P): T",
                        ),
                        method,
                    )
                    null
                }
                MethodShape.CollectionMapping ->
                    collection.resolve(method, sourceParam, parameters, returns)
                MethodShape.Mapping ->
                    mapping.resolve(method, sourceParam, sourceDecl, returnDecl, parameters, returns)
            }
            if (resolved == null) {
                anyInvalid = true
            } else {
                // Todos los errores en una pasada: un plan inline inválido igual entra a la
                // lista — se siguen procesando los demás métodos; la emisión se aborta al final.
                methods += resolved
                if ((resolved.body as? MethodBody.InlineConstruction)?.plan?.valid == false) anyInvalid = true
            }
        }

        // Nombres de la lista `ignore` que no existen en NINGÚN target de la interfaz.
        methodContext.ignoreAudit.reportUnmatched(
            optIns.ignore, location,
            mapper.qualifiedName?.asString() ?: mapper.simpleName.asString(),
            mapper, ctx.reporter,
        )
        if (anyInvalid || optIns.ignore.minus(methodContext.ignoreAudit.matched).isNotEmpty()) return null

        // Converters-class inyectados (por constructor). Con SPRING deben ser @Component (KMX035).
        val injectedConverters = PlanReferences.injectedConverters(
            methods.mapNotNull { (it.body as? MethodBody.InlineConstruction)?.plan },
        )
        if (componentModel == Emission.Component.SPRING) {
            val notComponents = injectedConverters.filterNot { fqn ->
                ctx.resolver.getClassDeclarationByName(ctx.resolver.getKSNameFromString(fqn))
                    ?.annotations?.any { it.qualifiedName() == Ann.SPRING_COMPONENT } == true
            }
            if (notComponents.isNotEmpty()) {
                notComponents.forEach { ctx.reporter.report(Diagnostics.injectedConverterNotComponent(location, it), mapper) }
                return null
            }
        }

        val implPlan = MapperImplPlan(
            interfaceQualifiedName = mapper.qualifiedName?.asString() ?: mapper.simpleName.asString(),
            componentModel = componentModel,
            methods = methods,
            injectedConverters = injectedConverters,
        )
        ctx.output.write(ctx.emitter.emitMapper(implPlan), mapper)
        recordReport(ctx, mapper, implPlan)
        if (componentModel == Emission.Component.KOIN) {
            ctx.koin.add(
                mapper.packageName.asString(), implPlan.interfaceSimpleName, implPlan.implSimpleName,
                injectedConverters.size, mapper,
            )
        }
        return null
    }

    /** Una entrada de reporte por método, con el modo real (interface/patch). */
    private fun recordReport(ctx: FrontendContext, mapper: KSClassDeclaration, implPlan: MapperImplPlan) {
        if (!ctx.report.enabled) return
        val (file, line) = ctx.report.locationOf(mapper)
        implPlan.methods.forEach { method ->
            val entry = when (val body = method.body) {
                is MethodBody.DelegateToExtension -> ReportEmitter.ReportMapping(
                    source = method.parameters.first().type.qualifiedName,
                    target = method.returns.qualifiedName,
                    mode = "interface", function = method.name, file = file, line = line,
                    delegatesTo = body.qualifiedFunction,
                )
                is MethodBody.InlineConstruction -> ReportEmitter.ReportMapping(
                    source = method.parameters.first().type.qualifiedName,
                    target = method.returns.qualifiedName,
                    mode = "interface", function = method.name, file = file, line = line,
                    fields = when (val c = body.plan.construction) {
                        is Construction.ConstructorCall ->
                            c.arguments.map { ctx.report.reportField(it.paramName, it.value) }
                        else -> emptyList()
                    },
                )
                // El método de colección reporta el par de CONTENEDORES y su delegado.
                is MethodBody.CollectionDelegate -> ReportEmitter.ReportMapping(
                    source = method.parameters.first().type.qualifiedName,
                    target = method.returns.qualifiedName,
                    mode = "interface", function = method.name, file = file, line = line,
                    delegatesTo = when (val e = body.element) {
                        is dev.kmapx.core.plan.ElementCall.SelfMethod -> "this.${e.name}"
                        is dev.kmapx.core.plan.ElementCall.Extension -> e.qualifiedFunction
                    },
                )
                // El patch reporta patch → target (como el viejo modo @PatchMapper).
                is MethodBody.PatchApplication -> ReportEmitter.ReportMapping(
                    source = body.patchParam.type.qualifiedName,
                    target = body.targetParam.type.qualifiedName,
                    mode = "patch", function = method.name, file = file, line = line,
                    fields = body.fields.map { ctx.report.reportField(it.name, it.value, it.fallbackToTarget) },
                )
            }
            ctx.report.record(entry, mapper)
        }
    }
}
