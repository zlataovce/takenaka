package me.kcra.takenaka.generator.accessor.plugin

import me.kcra.takenaka.core.Version
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.accessor.model.ConstructorAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * A Gradle-specific builder for [AccessorConfiguration] with Minecraft presets.
 *
 * @property project the project
 * @author Matouš Kučera
 */
abstract class AccessorGeneratorExtension(val project: Project) {
    /**
     * Versions to be mapped.
     */
    abstract val versions: ListProperty<String>

    /**
     * The output directory, defaults to `build/takenaka/output`.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * The cache directory, defaults to `build/takenaka/cache`.
     */
    abstract val cacheDirectory: DirectoryProperty

    /**
     * Whether cache should be validated strictly.
     */
    abstract val strictCache: Property<Boolean>

    /**
     * Class accessor models.
     */
    abstract val accessors: ListProperty<ClassAccessor>

    /**
     * Base package of the generated accessors.
     */
    abstract val basePackage: Property<String>

    /**
     * The language of the generated code.
     */
    abstract val languageFlavor: Property<LanguageFlavor>

    /**
     * Namespaces that should be used in accessors, empty if all namespaces should be used.
     */
    abstract val accessedNamespaces: ListProperty<String>

    init {
        outputDirectory.convention(project.layout.buildDirectory.dir("takenaka/output"))
        cacheDirectory.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        languageFlavor.convention(LanguageFlavor.JAVA)
        strictCache.convention(false)
    }

    fun requireClass(name: String, block: ClassAccessorBuilder.() -> Unit = {}) {
        accessors.add(ClassAccessorBuilder(name).apply(block).toClassAccessor())
    }
}

/**
 * A builder for [ClassAccessor].
 *
 * @property name mapped name of the accessed class
 * @author Matouš Kučera
 */
class ClassAccessorBuilder(val name: String) {
    /**
     * Field accessor models.
     */
    var fields = mutableListOf<FieldAccessor>()

    /**
     * Constructor accessor models.
     */
    var constructors = mutableListOf<ConstructorAccessor>()

    /**
     * Method accessor models.
     */
    var methods = mutableListOf<MethodAccessor>()

    fun field(name: String, type: String) {
        fields += FieldAccessor(name, type)
    }

    fun field(name: String, version: Version? = null) {
        fields += FieldAccessor(name, null, version)
    }

    fun constructor(type: String) {
        constructors += ConstructorAccessor(type)
    }

    fun constructor(vararg parameters: Any) {
    }

    fun method(name: String, type: String, version: Version? = null) {
        methods += MethodAccessor(name, type, version)
    }

    fun method(name: String, version: Version? = null, vararg parameters: Any) {
    }

    fun toClassAccessor() = ClassAccessor(name, fields, constructors, methods)
}
