/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023-2024 Matous Kucera
 *
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kcra.takenaka.generator.accessor.plugin.tasks

import kotlinx.coroutines.runBlocking
import me.kcra.takenaka.generator.accessor.AccessorConfiguration
import me.kcra.takenaka.generator.accessor.TracingAccessorGenerator
import me.kcra.takenaka.generator.common.provider.impl.SimpleAncestryProvider
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.OutputStream
import java.io.PrintStream

/**
 * A Gradle task that prints accessor debugging information.
 *
 * @author Matouš Kučera
 */
abstract class TraceAccessorsTask : GenerationTask() {
    /**
     * The generation trace output file, defaults to `build/takenaka/accessor-trace.txt`, nothing is saved if null.
     */
    @get:OutputFile
    abstract val tracingFile: RegularFileProperty

    init {
        tracingFile.convention(project.layout.buildDirectory.file("takenaka/accessor-trace.txt"))
    }

    /**
     * Runs the task.
     */
    @TaskAction
    fun run() {
        var tracingStream: OutputStream = NoOpCloser(System.out)

        val tracingFile0 = tracingFile.orNull?.asFile
        if (tracingFile0 != null) {
            tracingStream = Splitter(tracingStream, tracingFile0.outputStream())
        }

        val generator = TracingAccessorGenerator(
            PrintStream(tracingStream),
            outputWorkspace,
            AccessorConfiguration(
                accessors = accessors.get(),
                basePackage = basePackage.get(),
                codeLanguage = codeLanguage.get(),
                accessorType = accessorType.get(),
                namespaceFriendlinessIndex = namespaceFriendlinessIndex.get(),
                accessedNamespaces = accessedNamespaces.get(),
                craftBukkitVersionReplaceCandidates = craftBukkitVersionReplaceCandidates.get(),
                namingStrategy = namingStrategy.get(),
                accessorRuntimePackage = accessorRuntimePackage.get()
            )
        )

        runBlocking {
            generator.generate(
                mappingProvider.get(),
                SimpleAncestryProvider(historyIndexNamespace.get(), historyNamespaces.get())
            )
        }

        tracingStream.close()
        tracingFile0?.let { println("Report saved to ${it.absolutePath}.") }
    }

    /**
     * An [OutputStream] wrapper that no-ops [close] calls.
     */
    private class NoOpCloser(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: ByteArray) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun write(b: Int) = delegate.write(b)
        override fun flush() = delegate.flush()
        override fun close() {
        }
    }

    /**
     * A T-piece [OutputStream] splitter.
     */
    private class Splitter(private val left: OutputStream, private val right: OutputStream) : OutputStream() {
        override fun write(b: ByteArray) {
            left.write(b)
            right.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            left.write(b, off, len)
            right.write(b, off, len)
        }

        override fun write(b: Int) {
            left.write(b)
            right.write(b)
        }

        override fun flush() {
            left.flush()
            right.flush()
        }

        override fun close() {
            try {
                left.close()
            } finally {
                right.close()
            }
        }
    }
}
