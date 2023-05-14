package me.kcra.takenaka.generator.accessor.plugin.tasks

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.DefaultWorkspaceOptions
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.WorkspaceOptions
import me.kcra.takenaka.core.compositeWorkspace
import me.kcra.takenaka.core.mapping.WrappingContributor
import me.kcra.takenaka.core.mapping.adapter.*
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.resolve.impl.*
import me.kcra.takenaka.core.util.objectMapper
import me.kcra.takenaka.generator.common.ResolvingMappingProvider
import me.kcra.takenaka.generator.common.buildMappingConfig
import net.fabricmc.mappingio.tree.MappingTree
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

typealias MutableMappingsMapProperty = MapProperty<Version, MappingTree>

abstract class ResolveMappingsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val cacheDir: DirectoryProperty

    @get:Input
    abstract val versions: ListProperty<String>

    @get:Input
    abstract val options: Property<WorkspaceOptions>

    @get:Internal
    abstract val mappings: MutableMappingsMapProperty

    @get:Internal
    val cacheWorkspace by lazy {
        compositeWorkspace {
            rootDirectory(cacheDir.asFile.get())
            options(this@ResolveMappingsTask.options.get())
        }
    }

    @get:Internal
    val sharedCacheWorkspace by lazy {
        cacheWorkspace.createWorkspace {
            name = "shared"
        }
    }

    @get:Internal
    val mappingCacheWorkspace by lazy {
        cacheWorkspace.createCompositeWorkspace {
            name = "mappings"
        }
    }

    init {
        cacheDir.convention(project.layout.buildDirectory.dir("takenaka/cache"))
        options.convention(DefaultWorkspaceOptions.RELAXED_CACHE)
    }

    @TaskAction
    fun run() {
        val objectMapper = objectMapper()
        val xmlMapper = XmlMapper()

        val yarnProvider = YarnMetadataProvider(sharedCacheWorkspace, xmlMapper)
        val mappingConfig = buildMappingConfig {
            version(this@ResolveMappingsTask.versions.get())
            workspace(mappingCacheWorkspace)

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
                    IntermediaryMappingResolver(versionWorkspace, sharedCacheWorkspace),
                    YarnMappingResolver(versionWorkspace, yarnProvider),
                    SeargeMappingResolver(versionWorkspace, sharedCacheWorkspace),
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

        val mappingProvider = ResolvingMappingProvider(mappingConfig, objectMapper, xmlMapper)
        val analyzer = MappingAnalyzerImpl(
            MappingAnalyzerImpl.AnalysisOptions(
                innerClassNameCompletionCandidates = setOf("spigot")
            )
        )

        runBlocking {
            val mappingMap = mappingProvider.get(analyzer)
            analyzer.acceptResolutions()

            mappings.set(mappingMap)
        }
    }
}
