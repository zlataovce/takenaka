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
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.core.mapping.util.dstNamespaceIds
import me.kcra.takenaka.generator.accessor.context.GenerationContext
import me.kcra.takenaka.generator.accessor.model.*
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTreeView
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * A field accessor and ancestry node pair.
 */
typealias ResolvedFieldPair = Pair<FieldAccessor, FieldAncestryNode>

/**
 * A constructor accessor and ancestry node pair.
 */
typealias ResolvedConstructorPair = Pair<ConstructorAccessor, MethodAncestryNode>

/**
 * A method accessor and ancestry node pair.
 */
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
    protected fun resolveField(
        tree: AncestryTree<MappingTreeView.FieldMappingView>,
        model: FieldAccessor
    ): FieldAncestryNode {
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
    protected fun resolveFieldChain(
        tree: AncestryTree<MappingTreeView.FieldMappingView>,
        model: FieldAccessor
    ): List<ResolvedFieldPair> = buildList {
        var nextNode: FieldAccessor? = model
        while (nextNode != null) {
            add(ResolvedFieldPair(nextNode, resolveField(tree, nextNode)))
            nextNode = nextNode.chain
        }

        reverse() // last chain member comes first
    }

    /**
     * Resolves field ancestry nodes that match supplied required types.
     *
     * @param tree the ancestry tree
     * @param types the required types
     * @return the nodes
     */
    protected fun resolveRequiredFields(
        tree: AncestryTree<MappingTreeView.FieldMappingView>,
        types: RequiredMemberTypes
    ): List<FieldAncestryNode> = tree.filter { node ->
        if ((types and DefaultRequiredMemberTypes.CONSTANT) != 0) {
            val mod = node.last.value.modifiers

            if ((mod and Opcodes.ACC_STATIC) != 0 && (mod and Opcodes.ACC_FINAL) != 0) {
                return@filter true
            }
        }

        return@filter false
    }

    /**
     * Resolves a constructor ancestry node from a model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the node
     */
    protected fun resolveConstructor(
        tree: AncestryTree<MappingTreeView.MethodMappingView>,
        model: ConstructorAccessor
    ): MethodAncestryNode {
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
    protected fun resolveMethod(
        tree: AncestryTree<MappingTreeView.MethodMappingView>,
        model: MethodAccessor
    ): MethodAncestryNode {
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
    protected fun resolveMethodChain(
        tree: AncestryTree<MappingTreeView.MethodMappingView>,
        model: MethodAccessor
    ): List<ResolvedMethodPair> = buildList {
        var nextNode: MethodAccessor? = model
        while (nextNode != null) {
            add(ResolvedMethodPair(nextNode, resolveMethod(tree, nextNode)))
            nextNode = nextNode.chain
        }

        reverse() // last chain member comes first
    }

    /**
     * Returns a mapped name of a member based on the friendliness index.
     *
     * @param member the member
     * @return the mapped name
     */
    protected fun getFriendlyName(member: MappingTreeView.MemberMappingView): String {
        generator.config.namespaceFriendlinessIndex.forEach { ns ->
            member.getName(ns)?.let { return it }
        }
        return member.tree.dstNamespaceIds.firstNotNullOfOrNull(member::getDstName) ?: member.srcName
    }

    /**
     * Returns a mapped descriptor of a member based on the friendliness index.
     *
     * @param member the member
     * @return the mapped descriptor
     */
    protected fun getFriendlyDesc(member: MappingTreeView.MemberMappingView): String {
        generator.config.namespaceFriendlinessIndex.forEach { ns ->
            member.getDesc(ns)?.let { return it }
        }
        return member.tree.dstNamespaceIds.firstNotNullOfOrNull(member::getDstDesc) ?: member.srcDesc
    }

    /**
     * Returns a parsed [Type] of a member descriptor picked based on the friendliness index.
     *
     * @param member the member
     * @return the [Type]
     */
    protected fun getFriendlyType(member: MappingTreeView.MemberMappingView): Type = Type.getType(getFriendlyName(member))
}
