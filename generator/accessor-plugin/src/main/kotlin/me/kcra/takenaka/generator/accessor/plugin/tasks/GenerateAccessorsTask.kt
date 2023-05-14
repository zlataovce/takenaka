package me.kcra.takenaka.generator.accessor.plugin.tasks

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.*
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import me.kcra.takenaka.generator.common.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.buildMappingConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateAccessorsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val cacheDir: DirectoryProperty

    @get:Input
    abstract val versions: ListProperty<String>

    @get:Input
    abstract val accessors: ListProperty<ClassAccessor>

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val languageFlavor: Property<LanguageFlavor>

    @get:Input
    abstract val options: Property<WorkspaceOptions>

    @Internal
    val outputWorkspace: Provider<Workspace> = outputDir.map { dir ->
        workspace {
            rootDirectory(dir.asFile)
            options(this@GenerateAccessorsTask.options.get())
        }
    }

    @Internal
    val cacheWorkspace: Provider<CompositeWorkspace> = cacheDir.map { dir ->
        compositeWorkspace {
            rootDirectory(dir.asFile)
            options(this@GenerateAccessorsTask.options.get())
        }
    }

    @Internal
    val sharedCacheWorkspace: Provider<Workspace> = cacheWorkspace.map { workspace ->
        workspace.createWorkspace {
            name = "shared"
        }
    }

    @Internal
    val mappingCacheWorkspace: Provider<CompositeWorkspace> = cacheWorkspace.map { workspace ->
        workspace.createCompositeWorkspace {
            name = "mappings"
        }
    }

    init {
        cacheDir.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        outputDir.convention(project.layout.buildDirectory.dir("takenaka/output"))
        options.convention(DefaultWorkspaceOptions.RELAXED_CACHE)
    }

    @TaskAction
    fun run() {
        val objectMapper = objectMapper()
        val xmlMapper = XmlMapper()

        val yarnProvider = YarnMetadataProvider(sharedCacheWorkspace.get(), xmlMapper)
        val mappingConfig = buildMappingConfig {
            version(this@GenerateAccessorsTask.versions.get())
            workspace(mappingCacheWorkspace.get())

            // remove Searge's ID namespace, it's not necessary
            intercept { v ->
                NamespaceFilter(v, "searge_id")
            }
            // remove static initializers, not needed in the documentation
            intercept(::StaticInitializerFilter)
            // remove overrides of java/lang/Object, they are implicit
            intercept(::ObjectOverrideFilter)
            // remove obfuscated method parameter names, they are a filler from Searge
            intercept(::MethodArgSourceFilter)

            contributors { versionWorkspace ->
                val mojangProvider = MojangManifestAttributeProvider(versionWorkspace, objectMapper)
                val spigotProvider = SpigotManifestProvider(versionWorkspace, objectMapper)

                val prependedClasses = mutableListOf<String>()

                listOf(
                    VanillaMappingContributor(versionWorkspace, mojangProvider),
                    MojangServerMappingResolver(versionWorkspace, mojangProvider),
                    IntermediaryMappingResolver(versionWorkspace, sharedCacheWorkspace.get()),
                    YarnMappingResolver(versionWorkspace, yarnProvider),
                    SeargeMappingResolver(versionWorkspace, sharedCacheWorkspace.get()),
                    WrappingContributor(SpigotClassMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                        // 1.16.5 mappings have been republished with proper packages, even though the reobfuscated JAR does not have those
                        // See: https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/commits/80d35549ec67b87a0cdf0d897abbe826ba34ac27
                        LegacySpigotMappingPrepender(it, prependedClasses = prependedClasses, prependEverything = versionWorkspace.version.id == "1.16.5")
                    },
                    WrappingContributor(SpigotMemberMappingResolver(versionWorkspace, xmlMapper, spigotProvider)) {
                        LegacySpigotMappingPrepender(it, prependedClasses = prependedClasses)
                    }
                )
            }
        }

        val generator = AccessorGenerator(
            outputWorkspace.get(),
            AccessorConfiguration(
                accessors = accessors.get(),
                basePackage = basePackage.get(),
                languageFlavor = languageFlavor.get(),
                namespaceFriendlinessIndex = listOf("mojang", "spigot", "yarn", "searge", "intermediary", "source"),
                accessedNamespaces = listOf("spigot", "source"),
                craftBukkitVersionReplaceCandidates = listOf("spigot")
            )
        )

        val mappingProvider = ResolvingMappingProvider(mappingConfig, objectMapper, xmlMapper)
        val analyzer = MappingAnalyzerImpl(
            MappingAnalyzerImpl.AnalysisOptions(
                innerClassNameCompletionCandidates = setOf("spigot")
            )
        )

        runBlocking {
            val mappings = mappingProvider.get(analyzer)
            analyzer.acceptResolutions()

            generator.generate(mappings)
        }
    }
}
