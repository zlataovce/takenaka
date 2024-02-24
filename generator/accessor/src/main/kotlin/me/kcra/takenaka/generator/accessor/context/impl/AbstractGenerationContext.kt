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

package me.kcra.takenaka.generator.accessor.context.impl

import kotlinx.coroutines.CoroutineScope
import me.kcra.takenaka.core.Version
import me.kcra.takenaka.core.mapping.adapter.replaceCraftBukkitNMSVersion
import me.kcra.takenaka.core.mapping.ancestry.ConstructorComputationMode
import me.kcra.takenaka.core.mapping.ancestry.NameDescriptorPair
import me.kcra.takenaka.core.mapping.ancestry.impl.*
import me.kcra.takenaka.core.mapping.fromInternalName
import me.kcra.takenaka.core.mapping.resolve.impl.craftBukkitNmsVersion
import me.kcra.takenaka.core.mapping.resolve.impl.modifiers
import me.kcra.takenaka.core.mapping.util.dstNamespaceIds
import me.kcra.takenaka.generator.accessor.AccessorGenerator
import me.kcra.takenaka.generator.accessor.context.GenerationContext
import me.kcra.takenaka.generator.accessor.model.*
import me.kcra.takenaka.generator.accessor.naming.GeneratedClassType
import me.kcra.takenaka.generator.accessor.util.globAsRegex
import me.kcra.takenaka.generator.accessor.util.isGlob
import me.kcra.takenaka.generator.common.provider.AncestryProvider
import mu.KotlinLogging
import net.fabricmc.mappingio.tree.MappingTreeView.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.text.SimpleDateFormat
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
abstract class AbstractGenerationContext(
    override val generator: AccessorGenerator,
    val ancestryProvider: AncestryProvider,
    contextScope: CoroutineScope
) : GenerationContext, CoroutineScope by contextScope {
    /**
     * Names of accessor models that had accessor classes generated.
     */
    private val generatedClasses = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Resolved class accessors with generated class type and their final partially qualified name
     */
    private val generatedClassNames = mutableMapOf<Pair<ResolvedClassAccessor, GeneratedClassType>, String>() // {(fq name, class type): pq name of the generated class}

    /**
     * The generation timestamp of this context's output.
     */
    val generationTime = Date()

    /**
     * Generates an accessor class from a model.
     *
     * @param model the accessor model
     * @param tree the class ancestry tree
     */
    override fun generateClass(model: ClassAccessor, tree: ClassAncestryTree) {
        if (model.internalName.isGlob) {
            val results = tree.find(model.internalName.globAsRegex())

            logger.info { "matched ${results.size} nodes from glob pattern '${model.internalName}'" }
            if (results.isEmpty()) {
                logger.warn { "glob pattern '${model.internalName}' did not match any nodes" }
            }

            results.forEach { (name, node) ->
                generateClass(
                    ClassAccessor(
                        name,
                        model.fields,
                        model.constructors,
                        model.methods,
                        model.requiredTypes
                    ),
                    node
                )
            }
        } else {
            generateClass(
                model,
                checkNotNull(tree[model.internalName]) {
                    "Class ancestry node with name ${model.internalName} not found"
                }
            )
        }
    }

    /**
     * Generates an accessor class from a class ancestry node.
     *
     * @param model the accessor model
     * @param node the ancestry node of the class defined by the model
     */
    protected open fun generateClass(model: ClassAccessor, node: ClassAncestryNode) {
        if (!generatedClasses.add(model.internalName)) {
            logger.warn { "class '${model.internalName}' has already had accessors generated, duplicate model?" }
            return
        }

        logger.info { "generating accessors for class '${model.internalName}'" }

        val fieldTree = ancestryProvider.field<_, _, FieldMappingView>(node)
        val fieldAccessors = model.fields.flatMap { resolveFieldChain(fieldTree, it) } +
                resolveRequiredFields(fieldTree, model.requiredTypes).map { fieldNode ->
                    FieldAccessor(getFriendlyName(fieldNode.last.value), getFriendlyDesc(fieldNode.last.value)) to fieldNode
                }

        // fields can't be overloaded, but capitalization matters, which is a problem when making uppercase names from everything
        val fieldOverloadCount = mutableMapOf<String, Int>()
        val fieldOverloads = fieldAccessors.associate { (fieldAccessor, _) ->
            fieldAccessor to fieldOverloadCount.compute(fieldAccessor.upperName) { _, i -> i?.inc() ?: 0 }!!
        }

        val ctorTree = ancestryProvider.method<_, _, MethodMappingView>(node, constructorMode = ConstructorComputationMode.ONLY)
        val ctorAccessors = model.constructors.map { ResolvedConstructorPair(it, resolveConstructor(ctorTree, it)) } +
                resolveRequiredConstructors(ctorTree, model.requiredTypes).map { ctorNode ->
                    ConstructorAccessor(getFriendlyDesc(ctorNode.last.value)) to ctorNode
                }

        val methodTree = ancestryProvider.method<_, _, MethodMappingView>(node)
        val methodAccessors = model.methods.flatMap { resolveMethodChain(methodTree, it) }  +
                resolveRequiredMethods(methodTree, model.requiredTypes).map { methodNode ->
                    MethodAccessor(getFriendlyName(methodNode.last.value), getFriendlyDesc(methodNode.last.value)) to methodNode
                }

        val methodOverloadCount = mutableMapOf<String, Int>()
        val methodOverloads = methodAccessors.associate { (methodAccessor, _) ->
            methodAccessor to methodOverloadCount.compute(methodAccessor.upperName) { _, i -> i?.inc() ?: 0 }!!
        }

        generateClass(ResolvedClassAccessor(model, node, fieldAccessors, ctorAccessors, methodAccessors, fieldOverloads, methodOverloads))
    }

    /**
     * Generates an accessor class from a resolved model.
     *
     * @param resolvedAccessor the resolved accessor model
     */
    protected open fun generateClass(resolvedAccessor: ResolvedClassAccessor) {
    }

    /**
     * Generates a mapping lookup class with accessors
     * that have been generated in this context.
     */
    override fun generateLookupClass() {
        generateLookupClass(generatedClassNames.filter { it.key.second === GeneratedClassType.MAPPING }.map { it.value }.sorted())
    }

    /**
     * Generates a mapping lookup class from class names.
     *
     * @param names internal names of classes declared in accessor models
     */
    protected open fun generateLookupClass(names: List<String>) {
    }

    /**
     * Resolves a field ancestry node from a model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the node
     */
    protected open fun resolveField(tree: FieldAncestryTree, model: FieldAccessor): FieldAncestryNode {
        val fieldNode = if (model.type == null) {
            val version = checkNotNull(tree.versions.find { it.id == model.version }) {
                "Could not resolve version ${model.version} in ancestry tree"
            }

            tree.find(model.name, version = version)?.apply {
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
    protected open fun resolveFieldChain(tree: FieldAncestryTree, model: FieldAccessor): List<ResolvedFieldPair> = buildList {
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
    protected open fun resolveRequiredFields(tree: FieldAncestryTree, types: RequiredMemberTypes): List<FieldAncestryNode> = tree.filter { node ->
        val requiresEnumConstant = (types and DefaultRequiredMemberTypes.ENUM_CONSTANT) != 0
        if (requiresEnumConstant || (types and DefaultRequiredMemberTypes.CONSTANT) != 0) {
            val mod = node.last.value.modifiers

            if ((mod and Opcodes.ACC_STATIC) != 0 && (mod and Opcodes.ACC_FINAL) != 0) {
                return@filter !(requiresEnumConstant && (mod and Opcodes.ACC_ENUM) == 0)
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
    protected open fun resolveConstructor(tree: MethodAncestryTree, model: ConstructorAccessor): MethodAncestryNode {
        val ctorNode = tree[NameDescriptorPair("<init>", model.type)]

        return checkNotNull(ctorNode) {
            "Constructor ancestry node with type ${model.type} not found"
        }
    }

    /**
     * Resolves constructor ancestry nodes that match supplied required types.
     *
     * @param tree the ancestry tree
     * @param types the required types
     * @return the nodes
     */
    protected open fun resolveRequiredConstructors(tree: MethodAncestryTree, types: RequiredMemberTypes): List<MethodAncestryNode> {
        return emptyList()
    }

    /**
     * Resolves a method ancestry node from a model.
     *
     * @param tree the ancestry tree
     * @param model the model
     * @return the node
     */
    protected open fun resolveMethod(tree: MethodAncestryTree, model: MethodAccessor): MethodAncestryNode {
        val methodNode = if (model.isIncomplete || model.version != null) {
            val version = checkNotNull(tree.versions.find { it.id == model.version }) {
                "Could not resolve version ${model.version} in ancestry tree"
            }

            tree.find(model.name, model.type, version = version)?.apply {
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
    protected open fun resolveMethodChain(tree: MethodAncestryTree, model: MethodAccessor): List<ResolvedMethodPair> = buildList {
        var nextNode: MethodAccessor? = model
        while (nextNode != null) {
            add(ResolvedMethodPair(nextNode, resolveMethod(tree, nextNode)))
            nextNode = nextNode.chain
        }

        reverse() // last chain member comes first
    }

    /**
     * Resolves method ancestry nodes that match supplied required types.
     *
     * @param tree the ancestry tree
     * @param types the required types
     * @return the nodes
     */
    protected open fun resolveRequiredMethods(tree: MethodAncestryTree, types: RequiredMemberTypes): List<MethodAncestryNode> {
        return emptyList()
    }

    /**
     * Returns a mapped name of an element based on the friendliness index.
     *
     * @param elem the element
     * @return the mapped name
     */
    protected open fun getFriendlyName(elem: ElementMappingView): String {
        generator.config.namespaceFriendlinessIndex.forEach { ns ->
            elem.getName(ns)?.let { return it }
        }
        return elem.tree.dstNamespaceIds.firstNotNullOfOrNull(elem::getDstName) ?: elem.srcName
    }

    /**
     * Returns a mapped descriptor of a member based on the friendliness index.
     *
     * @param member the member
     * @return the mapped descriptor
     */
    protected open fun getFriendlyDesc(member: MemberMappingView): String {
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
    protected open fun getFriendlyType(member: MemberMappingView): Type = Type.getType(getFriendlyDesc(member))

    /**
     * Groups the generator's mappings by version.
     *
     * @param node the ancestry node
     * @return the grouped class mappings
     */
    protected open fun groupClassNames(node: ClassAncestryNode): Map<ClassKey, List<Version>> = buildMap<ClassKey, MutableList<Version>> {
        node.forEach { (version, klass) ->
            val nmsVersion = klass.tree.craftBukkitNmsVersion

            generator.config.accessedNamespaces.forEach { ns ->
                val nsId = klass.tree.getNamespaceId(ns)
                if (nsId != NULL_NAMESPACE_ID) {
                    val name = klass.getName(nsId) ?: klass.srcName

                    // de-internalize the name beforehand to meet the ClassMapping contract
                    getOrPut(ClassKey(ns, name.fromInternalName().replaceCraftBukkitNMSVersion(nmsVersion, separator = '.')), ::mutableListOf) += version
                }
            }
        }
    }

    /**
     * Groups the generator's mappings by version.
     *
     * @param node the ancestry node
     * @return the grouped field mappings
     */
    protected open fun groupFieldNames(node: FieldAncestryNode): Map<FieldKey, List<Version>> = buildMap<FieldKey, MutableList<Version>> {
        node.forEach { (version, field) ->
            generator.config.accessedNamespaces.forEach { ns ->
                val nsId = field.tree.getNamespaceId(ns)
                if (nsId != NULL_NAMESPACE_ID) {
                    val name = field.getName(nsId) ?: field.srcName

                    getOrPut(FieldKey(ns, name), ::mutableListOf) += version
                }
            }
        }
    }

    /**
     * Groups the generator's mappings by version.
     *
     * @param node the ancestry node
     * @return the grouped constructor mappings
     */
    protected open fun groupConstructorNames(node: MethodAncestryNode): Map<ConstructorKey, List<Version>> = buildMap<ConstructorKey, MutableList<Version>> {
        node.forEach { (version, ctor) ->
            val nmsVersion = ctor.tree.craftBukkitNmsVersion

            generator.config.accessedNamespaces.forEach { ns ->
                ctor.getDesc(ns)?.let { desc ->
                    getOrPut(ConstructorKey(ns, desc.replaceCraftBukkitNMSVersion(nmsVersion)), ::mutableListOf) += version
                }
            }
        }
    }

    /**
     * Groups the generator's mappings by version.
     *
     * @param node the ancestry node
     * @return the grouped method mappings
     */
    protected open fun groupMethodNames(node: MethodAncestryNode): Map<MethodKey, List<Version>> = buildMap<MethodKey, MutableList<Version>> {
        node.forEach { (version, method) ->
            val nmsVersion = method.tree.craftBukkitNmsVersion

            generator.config.accessedNamespaces.forEach { ns ->
                method.getDesc(ns)?.let { desc ->
                    val name = method.getName(ns) ?: method.srcName

                    getOrPut(MethodKey(ns, name, desc.replaceCraftBukkitNMSVersion(nmsVersion)), ::mutableListOf) += version
                }
            }
        }
    }

    /**
     * Sanitizes an accessor model name for generating an accessor class in this context.
     *
     * Is it safe to call this function multiple times for the same inputs.
     *
     * @param resolvedAccessor an accessor
     * @param classType a class type
     * @return a non-conflicting partially qualified name returned by current naming strategy
     */
    protected open fun generateNonConflictingName(resolvedAccessor: ResolvedClassAccessor, classType: GeneratedClassType): String {
        val namingStrategy = generator.config.namingStrategy
        val pair = Pair(resolvedAccessor, classType)

        synchronized(generatedClassNames) {
            generatedClassNames[pair]?.let {
                return it
            }

            val name = resolvedAccessor.model.name.fromInternalName()

            var index = 0
            var modifiedName = name
            var returnedName = ""

            while (true) {
                val indexedName = namingStrategy.klass(modifiedName, classType)
                if (!generatedClassNames.containsValue(indexedName)) {
                    returnedName = indexedName
                    break
                }
                modifiedName = name + (++index)
                if (index != 0 && indexedName == returnedName) {
                    // Our naming strategy is stupid and somehow changing the input does not change the output
                    index = 0
                    do {
                        returnedName = indexedName + (++index)
                    } while (generatedClassNames.containsValue(returnedName))
                    break
                }
                returnedName = indexedName
            }

            generatedClassNames[pair] = returnedName

            return returnedName
        }
    }

    companion object {
        /**
         * The file comment's generation timestamp date format.
         */
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    }
}

