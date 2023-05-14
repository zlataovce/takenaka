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

package me.kcra.takenaka.generator.accessor.context.impl

import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.mapping.ancestry.AncestryTree
import me.kcra.takenaka.core.mapping.ancestry.NameDescriptorPair
import me.kcra.takenaka.core.mapping.ancestry.impl.FieldAncestryNode
import me.kcra.takenaka.core.mapping.ancestry.impl.MethodAncestryNode
import me.kcra.takenaka.core.mapping.ancestry.impl.find
import me.kcra.takenaka.core.mapping.util.dstNamespaceIds
import me.kcra.takenaka.generator.accessor.context.GenerationContext
import me.kcra.takenaka.generator.accessor.model.ConstructorAccessor
import me.kcra.takenaka.generator.accessor.model.FieldAccessor
import me.kcra.takenaka.generator.accessor.model.MethodAccessor
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.Type
import java.util.*

private val logger = KotlinLogging.logger {}

typealias ResolvedFieldPair = Pair<FieldAccessor, FieldAncestryNode>
typealias ResolvedConstructorPair = Pair<ConstructorAccessor, MethodAncestryNode>
typealias ResolvedMethodPair = Pair<MethodAccessor, MethodAncestryNode>

/**
 * An implementation base for [GenerationContext].
 *
 * @author Matouš Kučera
 */
abstract class AbstractGenerationContext(contextScope: CoroutineScope) : GenerationContext, CoroutineScope by contextScope {
    /**
     * The generation timestamp of this context's output.
     */
    val generationTime by lazy(::Date)

    /**
     * Resolves a field ancestry node from a model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the node
     */
    protected fun resolveField(tree: AncestryTree<MappingTreeView.FieldMappingView>, model: FieldAccessor): FieldAncestryNode {
        val fieldNode = if (model.type == null) {
            tree.find(model.name, version = model.version)?.apply {
                logger.debug { "inferred type '${getFriendlyType(last.value).className}' for field ${model.name}" }
            }
        } else {
            tree[NameDescriptorPair(model.name, model.internalType!!)]
        }

        return checkNotNull(fieldNode) {
            "Field ancestry node with name ${model.name} and type ${model.internalType} not found"
        }
    }

    /**
     * Resolves field ancestry nodes from a chained model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the nodes
     */
    protected fun resolveFieldChain(tree: AncestryTree<MappingTreeView.FieldMappingView>, model: FieldAccessor): List<ResolvedFieldPair> {
        val nodes = mutableListOf<ResolvedFieldPair>()

        var nextNode: FieldAccessor? = model
        while (nextNode != null) {
            nodes += ResolvedFieldPair(nextNode, resolveField(tree, nextNode))
            nextNode = nextNode.chain
        }

        nodes.reverse() // last chain member comes first
        return nodes
    }

    /**
     * Resolves a constructor ancestry node from a model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the node
     */
    protected fun resolveConstructor(tree: AncestryTree<MappingTreeView.MethodMappingView>, model: ConstructorAccessor): MethodAncestryNode {
        val ctorNode = tree[NameDescriptorPair("<init>", model.type)]

        return checkNotNull(ctorNode) {
            "Constructor ancestry node with type ${model.type} not found"
        }
    }

    /**
     * Resolves a method ancestry node from a model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the node
     */
    protected fun resolveMethod(tree: AncestryTree<MappingTreeView.MethodMappingView>, model: MethodAccessor): MethodAncestryNode {
        val methodNode = if (model.isIncomplete || model.version != null) {
            tree.find(model.name, model.type, version = model.version)?.apply {
                if (model.isIncomplete) {
                    logger.debug { "inferred return type '${getFriendlyType(last.value).returnType.className}' for method ${model.name}" }
                }
            }
        } else {
            tree[NameDescriptorPair(model.name, model.type)]
        }

        return checkNotNull(methodNode) {
            "Method ancestry node with name ${model.name} and type ${model.type} not found"
        }
    }

    /**
     * Resolves method ancestry nodes from a chained model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the nodes
     */
    protected fun resolveMethodChain(tree: AncestryTree<MappingTreeView.MethodMappingView>, model: MethodAccessor): List<ResolvedMethodPair> {
        val nodes = mutableListOf<ResolvedMethodPair>()

        var nextNode: MethodAccessor? = model
        while (nextNode != null) {
            nodes += ResolvedMethodPair(nextNode, resolveMethod(tree, nextNode))
            nextNode = nextNode.chain
        }

        nodes.reverse() // last chain member comes first
        return nodes
    }

    /**
     * Returns a parsed [Type] of a member descriptor picked based on the friendliness index.
     *
     * @param member the member
     * @return the [Type]
     */
    protected fun getFriendlyType(member: MappingTreeView.MemberMappingView): Type {
        generator.config.namespaceFriendlinessIndex.forEach { ns ->
            member.getDesc(ns)?.let { return Type.getType(it) }
        }
        return Type.getType(member.tree.dstNamespaceIds.firstNotNullOfOrNull(member::getDstDesc) ?: member.srcDesc)
    }
}
