# takenaka

A Kotlin library for reconciling multiple obfuscation mapping files from multiple versions of Minecraft: JE.

The goal of this project is to improve the maintainability and performance of the [NMSMapper](https://github.com/ScreamingSandals/NMSMapper) library.

## Features

* fetching and deserialization of various mappings
* parsing of the server JAR (modifiers, superclasses, superinterfaces and more)
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
- [ ] Hashed (QuiltMC) mappings (PRs welcome!)
- [ ] QuiltMC mappings (PRs welcome!)

## Acknowledgements

- the [NMSMapper](https://github.com/ScreamingSandals/NMSMapper) library and the ScreamingSandals members

## Licensing

This library is licensed under the [Apache License, Version 2.0](https://github.com/zlataovce/takenaka/blob/master/LICENSE).
