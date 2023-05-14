package me.kcra.takenaka.generator.accessor.plugin.tasks

import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.core.DefaultWorkspaceOptions
import me.kcra.takenaka.core.WorkspaceOptions
import me.kcra.takenaka.core.workspace
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.LanguageFlavor
import me.kcra.takenaka.generator.accessor.model.ClassAccessor
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateAccessorsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val mappings: MutableMappingsMapProperty

    @get:Input
    abstract val accessors: ListProperty<ClassAccessor>

    @get:Input
    abstract val basePackage: Property<String>

    @get:Input
    abstract val languageFlavor: Property<LanguageFlavor>

    @get:Input
    abstract val accessedNamespaces: ListProperty<String>

    @get:Input
    abstract val options: Property<WorkspaceOptions>

    @get:Internal
    val outputWorkspace by lazy {
        workspace {
            rootDirectory(outputDir.asFile.get())
            options(this@GenerateAccessorsTask.options.get())
        }
    }

    init {
        outputDir.convention(project.layout.buildDirectory.dir("takenaka/output"))
        languageFlavor.convention(LanguageFlavor.JAVA)
        options.convention(DefaultWorkspaceOptions.RELAXED_CACHE)
    }

    @TaskAction
    fun run() {
        val generator = AccessorGenerator(
            outputWorkspace,
            AccessorConfiguration(
                accessors = accessors.get(),
                basePackage = basePackage.get(),
                languageFlavor = languageFlavor.get(),
                namespaceFriendlinessIndex = listOf("mojang", "spigot", "yarn", "searge", "intermediary", "source"),
                accessedNamespaces = accessedNamespaces.get(),
                craftBukkitVersionReplaceCandidates = listOf("spigot")
            )
        )

        runBlocking {
            generator.generate(mappings.get())
        }
    }
}
