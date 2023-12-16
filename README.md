# takenaka

[![Maven releases](https://repo.screamingsandals.org/api/badge/latest/releases/me/kcra/takenaka/core?color=000000&name=sandals-releases)](https://repo.screamingsandals.org/#/releases/me/kcra/takenaka)
[![Maven snapshots](https://repo.screamingsandals.org/api/badge/latest/snapshots/me/kcra/takenaka/core?color=000000&name=sandals-snapshots)](https://repo.screamingsandals.org/#/snapshots/me/kcra/takenaka)
[![Discord](https://img.shields.io/discord/1151104250487783555?logo=discord)](https://discord.gg/HZhPBbxpFP)

A Kotlin library for reconciling multiple obfuscation mapping files from multiple versions of Minecraft: JE.

The goal of this project is to improve the maintainability and performance of the [NMSMapper](https://github.com/ScreamingSandals/NMSMapper) library.

## Features

* fetching and deserialization of various mappings
* parsing of the server and client JAR (modifiers, superclasses, superinterfaces and more)
* mapping semantic analysis and error correction
* cross-version mapping history comparison
* web documentation generation (including generics!)
* reflective/MethodHandle accessor generation

### Mappings

- [x] Mojang mappings
- [x] Intermediary (FabricMC) mappings
- [x] Searge (Forge) mappings
- [x] Spigot mappings
- [x] Yarn (FabricMC) mappings
- [x] Hashed (QuiltMC) mappings
- [x] QuiltMC mappings

## Usage

### generator-accessor-plugin

A Gradle plugin for generating cross-version reflective accessors for plugins, mods, ...

This is an alternative to the traditional [paperweight-userdev](https://github.com/PaperMC/paperweight-test-plugin) + modularized abstraction approach,
but be warned, this requires a very good understanding of Java's [Reflection](https://www.oracle.com/technical-resources/articles/java/javareflection.html)
and is by no means something a beginner developer should attempt.

Example code listed here is in Gradle's Kotlin DSL flavor, but is also applicable to the Groovy DSL with minor adjustments.

To get started, include the ScreamingSandals repository for plugin management (`settings.gradle.kts`):
```kt
pluginManagement {
    repositories {
        gradlePluginPortal()
        
        // add the ScreamingSandals repository for the Gradle plugin
        maven("https://repo.screamingsandals.org/public")
    }
}

// ...
```

Apply and configure the plugin in your buildscript (`build.gradle.kts`):
```kt
// the accessorRuntime() function is just sugar for "me.kcra.takenaka:generator-accessor-runtime:${me.kcra.takenaka.gradle.BuildConfig.BUILD_VERSION}"
import me.kcra.takenaka.generator.accessor.plugin.accessorRuntime

plugins {
    id("me.kcra.takenaka.accessor") version "<latest version here, check the releases badge on the top of the page>" // apply the plugin
}

repositories {
    mavenCentral()

    // add the ScreamingSandals repository for the mapping bundle
    maven("https://repo.screamingsandals.org/public")
}

dependencies {
    mappingBundle("me.kcra.takenaka:mappings:1.8.8+1.20.2") // the mapping bundle, published by the project at github.com/zlataovce/mappings
    implementation(accessorRuntime()) // the small library needed for the accessors to function
}

accessors {
    // you can select a version subset with versions or versionRange, i.e.:
    
    // versions("1.20", "1.20.1", "1.20.2")
    // versionRange("1.20", "1.20.2")
    
    // if you don't, you will get accessors mapped for 1.8.8 to 1.20.2 - everything that the bundle offers
    
    basePackage("org.example.myplugin.accessors") // this is the base package of the generated output, probably somewhere in your plugin/library's namespace
    accessedNamespaces("spigot") // these are the "namespaces" that can be queried on runtime, i.e. "spigot" (for Bukkit), "searge" (for Forge), "mojang" (not useful on runtime), "yarn" (not useful on runtime), "intermediary" (for Fabric), "quilt" or "hashed" (for Quilt)
    accessorType("reflection") // this is the generated accessor type, can be "none" (no accessor breakout classes are generated, only a mapping class that can be queried), "reflection" or "method_handles" (self-explanatory, java.lang.reflect or java.lang.invoke accessors)
    
    // there are many more options, like mapping for clients, IntelliJ's source JAR view and auto-complete are your friends (Ctrl+Click)
    
    // now let's define what we want to access
    mapClass("net.minecraft.network.Connection") { // basically any name on mappings.cephx.dev apart from the obfuscated one, applies to other definitions as well
        // NOTE: in one member declaration, the field type, method or constructor argument types and the method return type MUST all be from the same namespace (you CAN'T mix e.g. Mojang and Searge names in one declaration)
        // the generation WILL fail if you do this!
        
        // NOTE: all mapped members are made accessible automatically (field.setAccessible(true), ...), so you don't need to worry about visibility modifiers
    
        // maps the "Channel channel" field
        field("io.netty.channel.Channel" /* the type of the field, can be a stringified type, a Java class or a KClass */, "channel" /* the name of the field */)
        
        // maps the "Connection(PacketFlow arg0)" constructor
        constructor("net.minecraft.network.protocol.PacketFlow" /* argument types of the constructor, same thing as the field type */)
        
        // maps the "SocketAddress getRemoteAddress()" method
        method(java.net.SocketAddress::class /* the return type of the method, same thing as the field type */, "getRemoteAddress" /* the method name */)
        
        // maps the "void disconnect(Component arg0)" method
        method(java.lang.Void.TYPE /* same thing, just a Java class instance */, "disconnect" /* same thing */, "net.minecraft.network.chat.Component" /* the method argument types, same thing as the field type */)
    }
    
    // ... - more mapClass declarations
}
```

### generator-web-cli

A CLI to generate your own mapping documentation site ([mappings.cephx.dev](https://mappings.cephx.dev)).

The CLI artifact can be acquired either from [the ScreamingSandals Maven repository](https://repo.screamingsandals.org/#/releases/me/kcra/takenaka/generator-web-cli)
(be sure to select the artifact with the `-all` classifier, else you're going to run into errors) or built and executed from source using the `runShadow` task:
`./gradlew :generator-web-cli:runShadow --args='<application args here>'`

Available options can be shown using the `--help` argument, example:
```
Usage: web-cli options_list
Options: 
    --output, -o [output] -> Output directory { String }
    --version, -v -> Target Minecraft version, can be specified multiple times (always required) { String }
    --namespace, -n -> Target namespace, can be specified multiple times, order matters { String }
    --ancestryNamespace, -a -> Target ancestry namespace, can be specified multiple times, has to be a subset of --namespace { String }
    --cache, -c [cache] -> Caching directory for mappings and other resources { String }
    --server [false] -> Include server mappings in the documentation 
    --client [false] -> Include client mappings in the documentation 
    --strictCache [false] -> Enforces strict cache validation 
    --clean [false] -> Removes previous build output and cache before launching 
    --noJoined [false] -> Don't cache joined mapping files 
    --minifier, -m [NORMAL] -> The minifier implementation used for minifying the documentation { Value should be one of [deterministic, normal, none] }
    --javadoc, -j -> Javadoc site that should be referenced in the documentation, can be specified multiple times { String }
    --synthetic, -s [false] -> Include synthetic classes and class members in the documentation 
    --noMeta [false] -> Don't emit HTML metadata tags in OpenGraph format 
    --noPseudoElems [false] -> Don't emit pseudo-elements (increases file size) 
    --help, -h -> Usage info
```

The command-line to build a [mappings.cephx.dev](https://mappings.cephx.dev) clone would look something like this:
`java -jar generator-web-cli-<latest version here>.jar --server -v 1.20.2 -v 1.20.1 ... (more versions follow)`

#### `--namespace` option

This option allows you to specify a custom namespace subset and preference ordering.
Useful if you want to build an instance for only modding mappings for example.

For the Fabric toolchain, you would specify something along the lines of `-n yarn -n intermediary`,
that resolves only the Yarn and Intermediary mappings, with Yarn names used for the links/overview pages/...
and Intermediary being a fallback that is used if a Yarn name is not present.
Both namespaces will still be shown as usual on class detail pages.

*The `source` (obfuscated) namespace is always implicitly appended last.*

By default, all available namespaces are used (`mojang, spigot, yarn, searge, intermediary, hashed`).

#### `--ancestryNamespace` option

This option allows you to select a subset of defined namespaces (`--namespace` choices or its default), which will be
used for ancestry computation. This option is more advanced and most users won't need to use it, the default suffices for most cases.

By default, the `mojang, spigot, searge, intermediary, hashed` namespaces are used, minus ones that haven't been defined (`--namespace` choices or its default).

#### `--javadoc` option

The expected value can be:
- a plus-sign delimited pair of a supported package and a link to the Javadoc root (Javadoc sites _with no modules_): `org.slf4j+https://www.slf4j.org/api`
- a link to the Javadoc root (Javadoc sites _with modules_): `https://docs.oracle.com/en/java/javase/17/docs/api`

**Java 17 API is included automatically for indexing.**

## Acknowledgements

- the [NMSMapper](https://github.com/ScreamingSandals/NMSMapper) library and the ScreamingSandals members

## Licensing

This library is licensed under the [Apache License, Version 2.0](https://github.com/zlataovce/takenaka/blob/master/LICENSE).
