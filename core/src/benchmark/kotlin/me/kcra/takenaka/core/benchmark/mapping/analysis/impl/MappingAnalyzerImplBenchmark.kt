/*
 * This file is part of takenaka, licensed under the Apache License, Version 2.0 (the "License").
 *
 * Copyright (c) 2023 Matous Kucera
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

package me.kcra.takenaka.core.benchmark.mapping.analysis.impl

import kotlinx.benchmark.*
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.analysis.impl.StandardProblemKinds
import me.kcra.takenaka.core.mapping.resolve.impl.VanillaMappingContributor
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import org.openjdk.jmh.annotations.Level
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class MappingAnalyzerImplBenchmark {
    private lateinit var tree: MappingTree

    @Setup(Level.Invocation)
    fun setup() {
        tree = createMockTree()
    }

    @Benchmark
    fun acceptInheritanceCorrections() {
        val analyzer = MappingAnalyzerImpl()

        analyzer.accept(tree)
        analyzer.acceptResolutions(StandardProblemKinds.INHERITANCE_ERROR)
    }

    private fun createMockTree(): MappingTree {
        val tree = MemoryMappingTree()

        while (true) {
            if (tree.visitHeader()) {
                tree.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, listOf("missing_namespace", "wrong_namespace", VanillaMappingContributor.NS_SUPER, VanillaMappingContributor.NS_INTERFACES))
            }

            if (tree.visitContent()) {
                // class ConcreteClass extends SuperClass {
                //     @Override
                //     void overriddenMethod(test.Class arg0) {
                //     }
                // }
                if (tree.visitClass("ConcreteClass")) {
                    tree.visitDstName(MappedElementKind.CLASS, 2, "SuperClass")
                    if (tree.visitElementContent(MappedElementKind.CLASS)) {
                        if (tree.visitMethod("overriddenMethod", "(Ltest/Class;)V")) {
                            tree.visitDstName(MappedElementKind.METHOD, 1, "wrongMappedName")
                            tree.visitElementContent(MappedElementKind.METHOD)
                        }
                    }
                }

                // class SuperClass {
                //     void overriddenMethod(test.Class arg0) {
                //     }
                // }
                if (tree.visitClass("SuperClass")) {
                    tree.visitDstName(MappedElementKind.CLASS, 2, "java/lang/Object")
                    if (tree.visitElementContent(MappedElementKind.CLASS)) {
                        if (tree.visitMethod("overriddenMethod", "(Ltest/Class;)V")) {
                            tree.visitDstName(MappedElementKind.METHOD, 0, "correctMappedName")
                            tree.visitDstName(MappedElementKind.METHOD, 1, "correctMappedName")
                            tree.visitElementContent(MappedElementKind.METHOD)
                        }
                    }
                }
            }

            if (tree.visitEnd()) {
                break
            }
        }

        return tree
    }
}
