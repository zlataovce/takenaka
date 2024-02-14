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

package me.kcra.takenaka.core.test.mapping.analysis.impl

import me.kcra.takenaka.core.mapping.analysis.impl.AnalysisOptions
import me.kcra.takenaka.core.mapping.analysis.impl.MappingAnalyzerImpl
import me.kcra.takenaka.core.mapping.analysis.impl.StandardProblemKinds
import me.kcra.takenaka.core.mapping.resolve.impl.AbstractVanillaMappingContributor
import net.fabricmc.mappingio.MappedElementKind
import net.fabricmc.mappingio.MappingUtil
import net.fabricmc.mappingio.tree.MappingTree
import net.fabricmc.mappingio.tree.MemoryMappingTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MappingAnalyzerImplTest {
    @Test
    fun `missing method names should be added for a single namespace`() {
        val tree = createMockTree()
        val klass = assertNotNull(tree.getClass("ConcreteClass"))
        val method = assertNotNull(klass.getMethod("overriddenMethod", "(Ltest/Class;)V"))

        val analyzer = MappingAnalyzerImpl(
            AnalysisOptions(
                inheritanceErrorExemptions = setOf("wrong_namespace")
            )
        )
        analyzer.accept(tree)

        assertNull(method.getName("missing_namespace"))

        analyzer.acceptResolutions(StandardProblemKinds.INHERITANCE_ERROR)

        assertEquals("correctMappedName", method.getName("missing_namespace"))
    }

    @Test
    fun `incorrect method names should be corrected for a single namespace`() {
        val tree = createMockTree()
        val klass = assertNotNull(tree.getClass("ConcreteClass"))
        val method = assertNotNull(klass.getMethod("overriddenMethod", "(Ltest/Class;)V"))

        val analyzer = MappingAnalyzerImpl(
            AnalysisOptions(
                inheritanceErrorExemptions = setOf("missing_namespace")
            )
        )
        analyzer.accept(tree)

        assertEquals("wrongMappedName", method.getName("wrong_namespace"))

        analyzer.acceptResolutions(StandardProblemKinds.INHERITANCE_ERROR)

        assertEquals("correctMappedName", method.getName("wrong_namespace"))
    }

    @Test
    fun `missing and incorrect method names should be added and corrected for multiple namespaces`() {
        val tree = createMockTree()
        val klass = assertNotNull(tree.getClass("ConcreteClass"))
        val method = assertNotNull(klass.getMethod("overriddenMethod", "(Ltest/Class;)V"))

        val analyzer = MappingAnalyzerImpl()
        analyzer.accept(tree)

        assertNull(method.getName("missing_namespace"))
        assertEquals("wrongMappedName", method.getName("wrong_namespace"))

        analyzer.acceptResolutions(StandardProblemKinds.INHERITANCE_ERROR)

        assertEquals("correctMappedName", method.getName("missing_namespace"))
        assertEquals("correctMappedName", method.getName("wrong_namespace"))
    }

    @Test
    fun `missing method names with a differing return type should be added for a single namespace`() {
        val tree = createMockTree()
        val klass = assertNotNull(tree.getClass("ConcreteClass"))
        val method = assertNotNull(klass.getMethod("overriddenMethod1", "(Ltest/Class;)LConcreteClass;"))

        val analyzer = MappingAnalyzerImpl(
            AnalysisOptions(
                inheritanceErrorExemptions = setOf("wrong_namespace")
            )
        )
        analyzer.accept(tree)

        assertNull(method.getName("missing_namespace"))

        analyzer.acceptResolutions(StandardProblemKinds.INHERITANCE_ERROR)

        assertEquals("correctMappedName", method.getName("missing_namespace"))
    }

    private fun createMockTree(): MappingTree {
        val tree = MemoryMappingTree()

        while (true) {
            if (tree.visitHeader()) {
                tree.visitNamespaces(MappingUtil.NS_SOURCE_FALLBACK, listOf("missing_namespace", "wrong_namespace", AbstractVanillaMappingContributor.NS_SUPER, AbstractVanillaMappingContributor.NS_INTERFACES))
            }

            if (tree.visitContent()) {
                // class ConcreteClass extends SuperClass {
                //     @Override
                //     void overriddenMethod(test.Class arg0) {
                //     }
                //     @Override
                //     ConcreteClass overriddenMethod1(test.Class arg0) {
                //     }
                // }
                if (tree.visitClass("ConcreteClass")) {
                    tree.visitDstName(MappedElementKind.CLASS, 2, "SuperClass")
                    if (tree.visitElementContent(MappedElementKind.CLASS)) {
                        if (tree.visitMethod("overriddenMethod", "(Ltest/Class;)V")) {
                            tree.visitDstName(MappedElementKind.METHOD, 1, "wrongMappedName")
                            tree.visitElementContent(MappedElementKind.METHOD)
                        }
                        if (tree.visitMethod("overriddenMethod1", "(Ltest/Class;)LConcreteClass;")) {
                            tree.visitElementContent(MappedElementKind.METHOD)
                        }
                    }
                }

                // class SuperClass {
                //     void overriddenMethod(test.Class arg0) {
                //     }
                //     SuperClass overriddenMethod1(test.Class arg0) {
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
                        if (tree.visitMethod("overriddenMethod1", "(Ltest/Class;)LSuperClass;")) {
                            tree.visitDstName(MappedElementKind.METHOD, 0, "correctMappedName")
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
