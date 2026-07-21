package dev.kmapx.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeSpec
import dev.kmapx.core.model.MType
import dev.kmapx.core.plan.Argument
import dev.kmapx.core.plan.Construction
import dev.kmapx.core.plan.Emission
import dev.kmapx.core.plan.MapperImplPlan
import dev.kmapx.core.plan.MapperMethod
import dev.kmapx.core.plan.MappingPlan
import dev.kmapx.core.plan.MethodBody
import dev.kmapx.core.plan.PostAssignment
import dev.kmapx.core.plan.ValueSource

/**
 * Backend emitter: materializes valid plans into Kotlin files (as [GeneratedFile],
 * never KotlinPoet types). It applies NO mapping rules — it only obeys the plans.
 * Scope embedded: extension functions con ConstructorCall/FactoryCall y `.also`.
 * Scope contract: `@Mapper interface X` → `XImpl` (object/class) delegando o materializando inline.
 */
public class PlanEmitter {

    public fun emit(plan: MappingPlan): GeneratedFile = emit(listOf(plan))

    public fun emit(plans: List<MappingPlan>): GeneratedFile {
        require(plans.isNotEmpty()) { "nothing to emit: empty plan list" }
        require(plans.all { it.valid }) { "refusing to emit an invalid plan; report diagnostics instead" }
        val source = plans.first().source
        require(plans.all { it.source == source }) { "all plans of one file must share the source type" }

        return generatedFile(source.packageName(), fileNameFor(source)) {
            for (plan in plans) addPlanFunctions(this, plan)
        }
    }

    /** `@BiMapTo`: ambas direcciones en el archivo del lado A (una declaración, una verdad). */
    public fun emitBidirectional(forward: MappingPlan, reverse: MappingPlan): GeneratedFile {
        require(forward.valid && reverse.valid) { "refusing to emit an invalid plan; report diagnostics instead" }
        return generatedFile(forward.source.packageName(), fileNameFor(forward.source)) {
            addPlanFunctions(this, forward)
            addPlanFunctions(this, reverse)
        }
    }

    private fun addPlanFunctions(builder: FileSpec.Builder, plan: MappingPlan) {
        when (val construction = plan.construction) {
            is Construction.SealedDispatch -> {
                // La función raíz es un `when` exhaustivo SIN else; cada rama no-object
                // delega en su propia función nombrada.
                builder.addFunction(markGenerated(sealedRootFunction(plan, construction), plan))
                construction.branches
                    .filter { it.plan.construction !is Construction.ObjectReference }
                    .forEach { builder.addFunction(extensionFunction(it.plan)) }  // sub-plan: sin marker
            }
            // When por IGUALDAD de entries — sin else, mismo principio que el dispatch de sealed.
            is Construction.EnumDispatch -> builder.addFunction(markGenerated(enumDispatchFunction(plan, construction), plan))
            else -> builder.addFunction(markGenerated(extensionFunction(plan), plan))
        }
    }

    /** El marcador `@GeneratedMapping(source, target)` — SOLO en la función top-level del par
     *  (no en sub-funciones anidadas), para que otro módulo descubra el par en el classpath. */
    private fun markGenerated(fn: FunSpec, plan: MappingPlan): FunSpec =
        fn.toBuilder()
            .addAnnotation(
                com.squareup.kotlinpoet.AnnotationSpec
                    .builder(ClassName("dev.kmapx.annotations", "GeneratedMapping"))
                    .addMember("source = %S", plan.source.qualifiedName)
                    .addMember("target = %S", plan.target.qualifiedName)
                    .build(),
            )
            .build()

    private fun sealedRootFunction(plan: MappingPlan, dispatch: Construction.SealedDispatch): FunSpec {
        val emission = plan.emission
        require(emission is Emission.ExtensionFunction) { "phase 1 emits extension functions only" }

        val body = CodeBlock.builder().add("return when (this) {\n").indent()
        for (branch in dispatch.branches) {
            when (val c = branch.plan.construction) {
                is Construction.ObjectReference ->
                    body.add("is %T -> %T\n", branch.plan.source.toClassName(), branch.plan.target.toClassName())
                else -> {
                    val subEmission = branch.plan.emission
                    require(subEmission is Emission.ExtensionFunction) { "sealed branches emit extension functions" }
                    body.add("is %T -> %L()\n", branch.plan.source.toClassName(), subEmission.name)
                }
            }
        }
        body.unindent().add("}\n")

        return FunSpec.builder(emission.name)
            .addKdoc("%L", kdocFor(plan))
            .apply { if (emission.isInternal) addModifiers(KModifier.INTERNAL) }
            .receiver(plan.source.toClassName())
            .returns(plan.target.toClassName())
            .addCode(body.build())
            .build()
    }

    /** El ÚNICO camino de FileSpec a [GeneratedFile] (header estándar incluido). */
    private fun generatedFile(
        packageName: String,
        fileName: String,
        configure: FileSpec.Builder.() -> Unit,
    ): GeneratedFile {
        val file = FileSpec.builder(packageName, fileName)
            .addFileComment("Generated by kmapx. Do not edit.")
            .apply(configure)
            .build()
        return GeneratedFile(packageName, fileName, file.toString())
    }

    /** Descubribilidad: ancla la función generada a su par de tipos de origen. */
    private fun kdocFor(plan: MappingPlan): String =
        "Generated by kmapx: ${plan.source.simpleName} → ${plan.target.simpleName}."

    /** `@Mapper interface X` → `XImpl`. */
    public fun emitMapper(plan: MapperImplPlan): GeneratedFile {
        require(plan.valid) { "refusing to emit an invalid mapper plan; report diagnostics instead" }

        val interfaceName = ClassName(plan.packageName, plan.interfaceSimpleName)
        // `object` cuando no hay deps ni componentModel; SPRING/KOIN o converters inyectados
        // fuerzan `class` (necesita constructor).
        val needsClass = plan.componentModel != Emission.Component.NONE || plan.injectedConverters.isNotEmpty()
        val typeBuilder = if (needsClass) {
            TypeSpec.classBuilder(plan.implSimpleName)
        } else {
            TypeSpec.objectBuilder(plan.implSimpleName)
        }
        // La anotación del framework se emite por nombre calificado — el classpath del
        // USUARIO la provee (regla cero); el processor ya verificó su presencia (KMX030).
        if (plan.componentModel == Emission.Component.SPRING) {
            typeBuilder.addAnnotation(ClassName("org.springframework.stereotype", "Component"))
        }
        // Converters-class inyectados → parámetros del constructor + propiedades privadas.
        if (plan.injectedConverters.isNotEmpty()) {
            val ctor = FunSpec.constructorBuilder()
            plan.injectedConverters.forEach { fqn ->
                val p = injectedParamName(fqn)
                val type = fqn.toConverterClassName()
                ctor.addParameter(p, type)
                typeBuilder.addProperty(
                    com.squareup.kotlinpoet.PropertySpec.builder(p, type)
                        .addModifiers(KModifier.PRIVATE).initializer(p).build(),
                )
            }
            typeBuilder.primaryConstructor(ctor.build())
        }
        typeBuilder.addSuperinterface(interfaceName)
        for (method in plan.methods) typeBuilder.addFunction(mapperMethod(method))

        return generatedFile(plan.packageName, plan.implSimpleName) { addType(typeBuilder.build()) }
    }

    /**
     * KOIN: un módulo generado POR PAQUETE con todos los mappers KOIN de ese paquete.
     * El usuario lo incluye en su `startKoin` (Koin no usa anotaciones: el módulo es el contrato).
     */
    public fun emitKoinModule(
        packageName: String,
        /**
         * (interfaz, impl, nº de deps inyectadas). Binding POR INTERFAZ:
         * `single<PersonMapper> { PersonMapperImpl() }`; con deps → `{ OrderMapperImpl(get()) }`.
         */
        mappers: List<Triple<String, String, Int>>,
    ): GeneratedFile {
        require(mappers.isNotEmpty()) { "no KOIN mappers to emit" }
        val moduleType = ClassName("org.koin.core.module", "Module")
        val moduleDsl = MemberName("org.koin.dsl", "module")
        val body = CodeBlock.builder().add("%M {\n", moduleDsl).indent()
        mappers.sortedBy { it.first }.forEach { (iface, impl, deps) ->
            val args = List(deps) { "get()" }.joinToString(", ")
            body.add("single<%T> { %L(%L) }\n", ClassName(packageName, iface), impl, args)
        }
        body.unindent().add("}")

        val property = com.squareup.kotlinpoet.PropertySpec.builder("kmapxModule", moduleType)
            .initializer(body.build())
            .build()
        return generatedFile(packageName, "KmapxKoinModule") { addProperty(property) }
    }

    /** El `target.copy(...)` de un método PATCH (detectado por forma). */
    private fun patchExpression(body: MethodBody.PatchApplication): CodeBlock {
        val targetName = body.targetParam.name
        val patchName = body.patchParam.name

        val patchKeep = ClassName("dev.kmapx.runtime", "Patch", "Keep")
        val patchSet = ClassName("dev.kmapx.runtime", "Patch", "Set")
        val copy = CodeBlock.builder().add("%L.copy(\n", targetName).indent()
        for (field in body.fields) {
            when {
                // Campo Patch<T> → `when` exhaustivo (Keep conserva, Set asigna p.value).
                field.tristate -> {
                    val setValue = field.value.render { "p.$it" }
                    copy.add(
                        "%L = when (val p = %L.%L) {\n", field.name, patchName, field.name,
                    ).indent()
                        .add("%T -> %L.%L\n", patchKeep, targetName, field.name)
                        .add("is %T -> %L\n", patchSet, setValue)
                        .unindent().add("},\n")
                }
                // Las refs del value son propiedades del PATCH; null en el patch = no tocar:
                field.fallbackToTarget ->
                    copy.add("%L = %L ?: %L.%L,\n", field.name, field.value.render { "$patchName.$it" }, targetName, field.name)
                else ->
                    copy.add("%L = %L,\n", field.name, field.value.render { "$patchName.$it" })
            }
        }
        copy.unindent().add(")")
        return copy.build()
    }

    private fun mapperMethod(method: MapperMethod): FunSpec {
        val expression = when (val body = method.body) {
            is MethodBody.DelegateToExtension -> {
                val pkg = body.qualifiedFunction.substringBeforeLast('.', missingDelimiterValue = "")
                val fn = body.qualifiedFunction.substringAfterLast('.')
                CodeBlock.of("%L.%M()", body.receiverParam, MemberName(pkg, fn, isExtension = true))
            }
            is MethodBody.InlineConstruction -> {
                val receiver = body.receiverParam
                val supplementary = body.supplementaryParams
                constructionExpression(body.plan) { name ->
                    if (name in supplementary) name else "$receiver.$name"
                }
            }
            // Delegación por elemento — jamás un plan inline dentro del lambda.
            is MethodBody.CollectionDelegate -> {
                val elementCode = when (val e = body.element) {
                    is dev.kmapx.core.plan.ElementCall.SelfMethod -> CodeBlock.of("%L(it)", e.name)
                    is dev.kmapx.core.plan.ElementCall.Extension -> {
                        val pkg = e.qualifiedFunction.substringBeforeLast('.', missingDelimiterValue = "")
                        val fn = e.qualifiedFunction.substringAfterLast('.')
                        CodeBlock.of("it.%M()", MemberName(pkg, fn, isExtension = true))
                    }
                }
                when (body.into.kind) {
                    dev.kmapx.core.model.TypeKind.COLLECTION_SET ->
                        CodeBlock.of("%L.mapTo(mutableSetOf()) { %L }", body.receiverParam, elementCode)
                    else -> CodeBlock.of("%L.map { %L }", body.receiverParam, elementCode)
                }
            }
            is MethodBody.PatchApplication -> patchExpression(body)
        }
        // `after<Método>(source, result)`; para PATCH la forma es (target, patch, result).
        val returned = method.afterFunction?.let { after ->
            if (method.body is MethodBody.PatchApplication) {
                CodeBlock.of(
                    "%L(%L, %L, %L)",
                    after, method.parameters[0].name, method.parameters[1].name, expression,
                )
            } else {
                CodeBlock.of("%L(%L, %L)", after, method.parameters.first().name, expression)
            }
        } ?: expression

        return FunSpec.builder(method.name)
            .addModifiers(KModifier.OVERRIDE)
            // Las firmas de método llevan el tipo COMPLETO (genéricos y nulabilidad).
            .apply { method.parameters.forEach { addParameter(it.name, it.type.toTypeName()) } }
            .returns(method.returns.toTypeName())
            .addCode(CodeBlock.builder().add("return %L\n", returned).build())
            .build()
    }

    private fun enumDispatchFunction(plan: MappingPlan, dispatch: Construction.EnumDispatch): FunSpec {
        val emission = plan.emission
        require(emission is Emission.ExtensionFunction) { "phase 1 emits extension functions only" }

        val body = CodeBlock.builder().add("return when (this) {\n").indent()
        for (branch in dispatch.entries) {
            body.add(
                "%T.%L -> %T.%L\n",
                plan.source.toClassName(), branch.sourceEntry,
                plan.target.toClassName(), branch.targetEntry,
            )
        }
        body.unindent().add("}\n")

        return FunSpec.builder(emission.name)
            .apply { if (emission.isInternal) addModifiers(KModifier.INTERNAL) }
            .receiver(plan.source.toClassName())
            .returns(plan.target.toClassName())
            .addCode(body.build())
            .build()
    }

    private fun extensionFunction(plan: MappingPlan): FunSpec {
        val emission = plan.emission
        require(emission is Emission.ExtensionFunction) { "phase 1 emits extension functions only" }

        return FunSpec.builder(emission.name)
            .addKdoc("%L", kdocFor(plan))
            .apply { if (emission.isInternal) addModifiers(KModifier.INTERNAL) }
            .receiver(plan.source.toClassName())
            .returns(plan.target.toClassName())
            .addCode(CodeBlock.builder().add("return %L\n", constructionExpression(plan) { it }).build())
            .build()
    }

    /** La expresión de construcción del plan (sin `return`), con [resolveRef] para calificar fuentes. */
    private fun constructionExpression(plan: MappingPlan, resolveRef: (String) -> String): CodeBlock =
        when (val c = plan.construction) {
            is Construction.ConstructorCall ->
                callCode(plan.target, CodeBlock.of("%T", plan.target.toClassName()), c.arguments, c.postAssignments, resolveRef)
            is Construction.FactoryCall ->
                callCode(plan.target, factoryReference(c), c.arguments, c.postAssignments, resolveRef)
            else -> throw IllegalArgumentException("unsupported construction for phase 1: $c")
        }

    private fun factoryReference(c: Construction.FactoryCall): CodeBlock {
        val functionName = c.qualifiedFunction.substringAfterLast('.')
        val companionOf = c.companionOf
        return if (companionOf != null) {
            val owner = ClassName(
                companionOf.substringBeforeLast('.', missingDelimiterValue = ""),
                companionOf.substringAfterLast('.'),
            )
            CodeBlock.of("%T.%L", owner, functionName)
        } else {
            val pkg = c.qualifiedFunction.substringBeforeLast('.', missingDelimiterValue = "")
            CodeBlock.of("%M", MemberName(pkg, functionName))
        }
    }

    private fun callCode(
        target: MType,
        callee: CodeBlock,
        arguments: List<Argument>,
        postAssignments: List<PostAssignment>,
        resolveRef: (String) -> String,
    ): CodeBlock {
        // Los argumentos con NullFallbackToDefault bifurcan la LLAMADA (la omisión
        // es la única forma de aplicar el default del constructor: KSP no expone su valor).
        val omissible = arguments.filter { it.value is ValueSource.NullFallbackToDefault }
        val call = when (omissible.size) {
            0 -> singleCall(callee, arguments, emptySet(), resolveRef)
            1 -> {
                val ref = omissible.single().omissibleRef(resolveRef)
                CodeBlock.of(
                    "if (%L != null) %L else %L",
                    ref,
                    singleCall(callee, arguments, emptySet(), resolveRef),
                    singleCall(callee, arguments, setOf(omissible.single().paramName), resolveRef),
                )
            }
            2 -> {
                val (a, b) = omissible
                val refA = a.omissibleRef(resolveRef)
                val refB = b.omissibleRef(resolveRef)
                CodeBlock.builder().add("when {\n").indent()
                    .add("%L != null && %L != null -> %L\n", refA, refB, singleCall(callee, arguments, emptySet(), resolveRef))
                    .add("%L != null -> %L\n", refA, singleCall(callee, arguments, setOf(b.paramName), resolveRef))
                    .add("%L != null -> %L\n", refB, singleCall(callee, arguments, setOf(a.paramName), resolveRef))
                    .add("else -> %L\n", singleCall(callee, arguments, setOf(a.paramName, b.paramName), resolveRef))
                    .unindent().add("}").build()
            }
            else -> throw IllegalArgumentException(
                "more than 2 omissible defaults must be KMX022 upstream; got ${omissible.size}",
            )
        }
        if (postAssignments.isEmpty()) return call

        // `var`s de cuerpo se asignan post-construcción, nunca dentro del mecanismo.
        val builder = CodeBlock.builder()
        if (omissible.isEmpty()) builder.add("%L", call) else builder.add("(%L)", call)
        builder.add(".also {\n").indent()
        for (assignment in postAssignments) {
            builder.add("it.%L = %L\n", assignment.propertyName, assignment.value.render(resolveRef))
        }
        return builder.unindent().add("}").build()
    }

    /** Una llamada concreta: los params en [omit] se omiten (aplica su default); un omisible
     *  presente se emite como referencia directa (el `if`/`when` ya garantizó no-null). */
    private fun singleCall(
        callee: CodeBlock,
        arguments: List<Argument>,
        omit: Set<String>,
        resolveRef: (String) -> String,
    ): CodeBlock {
        val builder = CodeBlock.builder().add("%L(\n", callee).indent()
        for (arg in arguments) {
            if (arg.paramName in omit) continue
            val value = when (val v = arg.value) {
                is ValueSource.NullFallbackToDefault -> CodeBlock.of("%L", resolveRef(v.source.name))
                else -> v.render(resolveRef)
            }
            builder.add("%L = %L,\n", arg.paramName, value)
        }
        return builder.unindent().add(")").build()
    }

    private fun Argument.omissibleRef(resolveRef: (String) -> String): String =
        resolveRef((value as ValueSource.NullFallbackToDefault).source.name)

    /**
     * Las referencias a funciones (converters, mappers) se emiten como [MemberName] —
     * nombre corto + import correcto, cero strings libres (refactor-safe por contrato).
     */
    private fun ValueSource.render(resolveRef: (String) -> String): CodeBlock = when (this) {
        is ValueSource.Direct -> CodeBlock.of("%L", resolveRef(source.name))
        is ValueSource.UnwrapValueClass ->
            if (safeCall) CodeBlock.of("%L?.value", resolveRef(source.name))
            else CodeBlock.of("%L.value", resolveRef(source.name))
        is ValueSource.WrapValueClass ->
            if (safeCall) CodeBlock.of("%L?.let { %T(it) }", resolveRef(source.name), into.toClassName())
            else CodeBlock.of("%T(%L)", into.toClassName(), resolveRef(source.name))
        is ValueSource.NullStrategyOver -> {
            // La estrategia envuelve el RESULTADO de la conversión estructural.
            val innerCode = inner.render(resolveRef)
            when (val o = outcome) {
                is dev.kmapx.core.plan.StrategyOutcome.Fallback -> CodeBlock.of("%L ?: %L", innerCode, o.default)
                is dev.kmapx.core.plan.StrategyOutcome.Throw ->
                    CodeBlock.of("%L ?: throw IllegalArgumentException(%S)", innerCode, o.message)
                dev.kmapx.core.plan.StrategyOutcome.Unsafe -> CodeBlock.of("%L!!", innerCode)
            }
        }
        is ValueSource.ViaConverter -> {
            val member = converter.qualifiedFunction.toMemberName()
            if (safeCall) CodeBlock.of("%L?.let { %M(it) }", resolveRef(source.name), member)
            else CodeBlock.of("%M(%L)", member, resolveRef(source.name))
        }
        is ValueSource.ViaQualifiedConverter -> if (injected) {
            // Bean inyectado — llamada de INSTANCIA sobre el parámetro del constructor.
            val p = injectedParamName(converterObject)
            if (safeCall) CodeBlock.of("%L?.let(%L::convert)", resolveRef(source.name), p)
            else CodeBlock.of("%L.convert(%L)", p, resolveRef(source.name))
        } else {
            // `ShortDate.convert(x)`; fuente nullable → `x?.let(ShortDate::convert)`.
            val obj = converterObject.toConverterClassName()
            if (safeCall) CodeBlock.of("%L?.let(%T::convert)", resolveRef(source.name), obj)
            else CodeBlock.of("%T.convert(%L)", obj, resolveRef(source.name))
        }
        is ValueSource.ViaMapper -> when (val m = mapper) {
            is dev.kmapx.core.plan.MapperRef.GeneratedExtension -> {
                val member = m.qualifiedFunction.toMemberName(extension = true)
                // Fuente nullable compone con `?.` — `address?.toAddressDto()`.
                if (safeCall) CodeBlock.of("%L?.%M()", resolveRef(source.name), member)
                else CodeBlock.of("%L.%M()", resolveRef(source.name), member)
            }
            is dev.kmapx.core.plan.MapperRef.UserConverter ->
                CodeBlock.of("%M(%L)", m.ref.qualifiedFunction.toMemberName(), resolveRef(source.name))
        }
        is ValueSource.NumericWidening ->
            // Widening sin pérdida de la lista cerrada — `x.toLong()` / `x?.toLong()`.
            if (safeCall) CodeBlock.of("%L?.%L()", resolveRef(source.name), toFunction)
            else CodeBlock.of("%L.%L()", resolveRef(source.name), toFunction)
        is ValueSource.BuiltinConversion ->
            // La plantilla ya es una llamada CALIFICADA (tabla cerrada) — cero imports.
            if (safeCall) {
                CodeBlock.of("%L?.let { %L }", resolveRef(source.name), template.replace("%s", "it"))
            } else {
                CodeBlock.of("%L", template.replace("%s", resolveRef(source.name)))
            }
        is ValueSource.NullFallbackToValue -> CodeBlock.of("%L ?: %L", resolveRef(source.name), default)
        is ValueSource.NullUnsafe -> CodeBlock.of("%L!!", resolveRef(source.name))
        is ValueSource.NullOrThrow ->
            CodeBlock.of("%L ?: throw IllegalArgumentException(%S)", resolveRef(source.name), message)
        is ValueSource.NullFallbackToDefault ->
            error("NullFallbackToDefault se bifurca en la llamada (callCode); nunca se renderiza inline")
        is ValueSource.MapElements -> {
            // UNA pasada. El elemento se referencia como `it`.
            val elementCode = element.render { it }
            when (into) {
                dev.kmapx.core.model.TypeKind.COLLECTION_SET ->
                    CodeBlock.of("%L.mapTo(mutableSetOf()) { %L }", resolveRef(source.name), elementCode)
                dev.kmapx.core.model.TypeKind.COLLECTION_ARRAY ->
                    CodeBlock.of("%L.map { %L }.toTypedArray()", resolveRef(source.name), elementCode)
                dev.kmapx.core.model.TypeKind.RESULT ->
                    CodeBlock.of("%L.map { %L }", resolveRef(source.name), elementCode)
                // List (o Collection/Iterable target): `map{}` produce List; si la fuente es
                // Sequence (lazy) se cierra con `.toList()`.
                else -> if (lazySource) {
                    CodeBlock.of("%L.map { %L }.toList()", resolveRef(source.name), elementCode)
                } else {
                    CodeBlock.of("%L.map { %L }", resolveRef(source.name), elementCode)
                }
            }
        }
        is ValueSource.MapEntries -> {
            // Map por entradas — el emisor idiomático exacto según qué lado cambia.
            val src = resolveRef(source.name)
            val keyCode = key?.render { it }
            val valueCode = value?.render { it }
            when {
                keyCode == null && valueCode != null ->
                    CodeBlock.of("%L.mapValues { (_, v) -> %L }", src, valueCode)
                keyCode != null && valueCode == null ->
                    CodeBlock.of("%L.mapKeys { (k, _) -> %L }", src, keyCode)
                keyCode != null && valueCode != null ->
                    CodeBlock.of("buildMap { for ((k, v) in %L) put(%L, %L) }", src, keyCode, valueCode)
                else -> CodeBlock.of("%L", src) // ambos idénticos: el motor emite Direct, no llega aquí
            }
        }
    }

    /** El `object` de `@UseConverter` como ClassName (top-level en v1; restricción de v1). */
    private fun String.toConverterClassName(): ClassName =
        ClassName(substringBeforeLast('.', missingDelimiterValue = ""), substringAfterLast('.'))

    /** Nombre del parámetro/propiedad para un converter-class inyectado (simpleName en minúscula). */
    private fun injectedParamName(fqn: String): String =
        fqn.substringAfterLast('.').replaceFirstChar { it.lowercase() }

    private fun String.toMemberName(extension: Boolean = false): MemberName =
        MemberName(
            substringBeforeLast('.', missingDelimiterValue = ""),
            substringAfterLast('.'),
            isExtension = extension,
        )

    private fun fileNameFor(source: MType): String = "${source.simpleName}Mappings"
}

private fun MType.packageName(): String =
    packageName ?: qualifiedName.substringBeforeLast('.', missingDelimiterValue = "")

/** Respeta anidamiento — `sample.Event.Approved` con paquete `sample` → ClassName(sample, Event, Approved). */
/**
 * El TypeName COMPLETO de una firma de método: genéricos ([MType.typeArgs])
 * y nulabilidad incluidos (`List<Order>`, `Order?`). Las emisiones de clases planas siguen
 * usando [toClassName].
 */
private fun MType.toTypeName(): com.squareup.kotlinpoet.TypeName {
    val base = toClassName()
    val parameterized = if (typeArgs.isEmpty()) {
        base
    } else {
        com.squareup.kotlinpoet.ParameterizedTypeName.Companion.run {
            base.parameterizedBy(typeArgs.map { it.toTypeName() })
        }
    }
    return parameterized.copy(nullable = nullable)
}

private fun MType.toClassName(): ClassName {
    val pkg = packageName()
    val simpleNames = qualifiedName.removePrefix("$pkg.").split('.').filter { it.isNotEmpty() }
    return ClassName(pkg, simpleNames.ifEmpty { listOf(simpleName) })
}
