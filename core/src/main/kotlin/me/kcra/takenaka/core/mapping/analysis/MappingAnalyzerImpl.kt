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

package me.kcra.takenaka.core.mapping.analysis

import me.kcra.takenaka.core.mapping.resolve.VanillaMappingContributor
import net.fabricmc.mappingio.tree.MappingTree
import org.objectweb.asm.Opcodes

/**
 * A base implementation of [MappingAnalyzer] that corrects problems defined in [StandardProblemKinds].
 *
 * @author Matouš Kučera
 */
open class MappingAnalyzerImpl : MappingAnalyzer {
    override val problems = mutableMapOf<ProblemKind, MutableList<Problem<*>>>()
    
    override fun acceptClass(klass: MappingTree.ClassMapping) {
        val mod = klass.getName(VanillaMappingContributor.NS_MODIFIERS)?.toIntOrNull()

        if (mod == null) {
            addProblem(klass, null, StandardProblemKinds.NON_EXISTENT_MAPPING) { element.tree.classes.remove(element) }
        }
        if (mod != null && (mod and Opcodes.ACC_SYNTHETIC) != 0) {
            addProblem(klass, null, StandardProblemKinds.SYNTHETIC) { element.tree.classes.remove(element) }
        }
    }

    override fun acceptField(field: MappingTree.FieldMapping) {
        val mod = field.getName(VanillaMappingContributor.NS_MODIFIERS)?.toIntOrNull()

        if (mod == null) {
            addProblem(field, null, StandardProblemKinds.NON_EXISTENT_MAPPING) { element.owner.fields.remove(element) }
        }
        if (mod != null && (mod and Opcodes.ACC_SYNTHETIC) != 0) {
            addProblem(field, null, StandardProblemKinds.SYNTHETIC) { element.owner.fields.remove(element) }
        }
    }

    override fun acceptMethod(method: MappingTree.MethodMapping) {
        val mod = method.getName(VanillaMappingContributor.NS_MODIFIERS)?.toIntOrNull()

        if (mod == null) {
            addProblem(method, null, StandardProblemKinds.NON_EXISTENT_MAPPING) { element.owner.methods.remove(element) }
        }
        if (mod != null && (mod and Opcodes.ACC_SYNTHETIC) != 0) {
            addProblem(method, null, StandardProblemKinds.SYNTHETIC) { element.owner.methods.remove(element) }
        }
    }
}
