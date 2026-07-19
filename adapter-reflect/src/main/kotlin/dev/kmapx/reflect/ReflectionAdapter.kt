package dev.kmapx.reflect

import dev.kmapx.core.model.MClass
import dev.kmapx.core.model.MConstructor
import dev.kmapx.core.model.MConstructorParam
import dev.kmapx.core.model.MEnumEntry
import dev.kmapx.core.model.MProperty
import dev.kmapx.core.model.MType
import dev.kmapx.core.model.TypeKind
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * Reflection adapter, FOR TESTS ONLY.
 * Lets core tests build MClass from real Kotlin classes without invoking the compiler.
 * Production translation is frontend-ksp's job; this adapter must mirror its output shape.
 */
public inline fun <reified T : Any> mclassOf(): MClass = mclassOf(T::class)

public fun mclassOf(klass: KClass<*>): MClass {
    val ctorParamNames = klass.primaryConstructor?.parameters?.mapNotNull { it.name }?.toSet() ?: emptySet()

    val properties = klass.memberProperties.map { prop ->
        // Espeja al frontend: `var` con setter private/protected no es asignable.
        val assignable = prop is KMutableProperty<*> &&
            (prop.setter.visibility == KVisibility.PUBLIC || prop.setter.visibility == KVisibility.INTERNAL)
        MProperty(
            name = prop.name,
            type = prop.returnType.toMType(),
            mutable = assignable,
            inConstructor = prop.name in ctorParamNames,
            computed = false, // reflection can't distinguish computed getters reliably; KSP can (frontend concern)
        )
    }

    val constructors = klass.constructors.map { ctor ->
        MConstructor(
            params = ctor.parameters.map { p ->
                MConstructorParam(
                    name = p.name ?: "<unnamed>",
                    type = p.type.toMType(),
                    hasDefault = p.isOptional,
                )
            },
            isPrimary = ctor == klass.primaryConstructor,
            // internal es utilizable: el código generado vive en el mismo módulo (mismo criterio
            // que el frontend). @MapConstructor/@MapFactory tienen retención SOURCE: reflection
            // no puede verlas — los tests de constructores construyen MClass a mano.
            visible = ctor.visibility == KVisibility.PUBLIC || ctor.visibility == KVisibility.INTERNAL,
        )
    }

    val subtypes = if (klass.isSealed) klass.sealedSubclasses.map { mclassOf(it) } else emptyList()

    // Entries por reflection (@MapEntry es SOURCE: overrides solo en tests a mano / KSP).
    val enumEntries = klass.java.enumConstants
        ?.map { MEnumEntry((it as Enum<*>).name) }
        ?: emptyList()

    return MClass(
        type = klass.toMType(nullable = false),
        properties = properties,
        constructors = constructors,
        sealedSubtypes = subtypes,
        enumEntries = enumEntries,
    )
}

private fun KType.toMType(): MType {
    val erasure = jvmErasure
    return erasure.toMType(nullable = isMarkedNullable).copy(
        typeArgs = arguments.mapNotNull { it.type?.toMType() },
    )
}

private fun KClass<*>.toMType(nullable: Boolean): MType {
    val kind = when {
        // ANTES del check de value class — kotlin.Result ES value class (espeja al frontend).
        qualifiedName == "kotlin.Result" -> TypeKind.RESULT
        qualifiedName == "kotlin.Array" -> TypeKind.COLLECTION_ARRAY
        isValue -> TypeKind.VALUE_CLASS
        isSealed && java.isInterface -> TypeKind.SEALED_INTERFACE
        isSealed -> TypeKind.SEALED_CLASS
        // OBJECT antes que DATA: un `data object` es OBJECT (espeja al frontend).
        objectInstance != null -> TypeKind.OBJECT
        isData -> TypeKind.DATA_CLASS
        java.isEnum -> TypeKind.ENUM
        this == List::class -> TypeKind.COLLECTION_LIST
        this == Set::class -> TypeKind.COLLECTION_SET
        this == Map::class -> TypeKind.COLLECTION_MAP
        this == Iterable::class -> TypeKind.COLLECTION_ITERABLE
        this == Collection::class -> TypeKind.COLLECTION_ITERABLE
        this == Sequence::class -> TypeKind.COLLECTION_SEQUENCE
        else -> TypeKind.REGULAR_CLASS
    }
    val underlying = if (kind == TypeKind.VALUE_CLASS) {
        primaryConstructor?.parameters?.singleOrNull()?.type?.toMType()
    } else null

    return MType(
        qualifiedName = qualifiedName ?: java.name,
        nullable = nullable,
        kind = kind,
        underlying = underlying,
        // Paquete real — imprescindible para clases anidadas (espeja al frontend).
        packageName = java.`package`?.name ?: "",
    )
}
