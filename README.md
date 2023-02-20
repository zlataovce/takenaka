# takenaka

A Kotlin library for reconciling multiple obfuscation mapping files from multiple versions of Minecraft: JE.

The goal of this project is to improve the maintainability and performance of the [NMSMapper](https://github.com/ScreamingSandals/NMSMapper) library.

## Features

* fetching and deserialization of various mappings
* parsing of the server JAR (modifiers, superclasses, superinterfaces and more)
* cross-version mapping ancestry comparison
* web documentation generation (including generics!)

### Planned features

- [ ] cross-version history comparison for class members (fields and methods)
- [ ] history page for the web documentation
- [ ] reflective accessor generation

### Mappings

- [x] Mojang mappings
- [x] Intermediary (FabricMC) mappings
- [x] Searge (Forge) mappings
- [x] Spigot mappings
- [ ] Yarn (FabricMC) mappings
- [ ] Hashed (QuiltMC) mappings
- [ ] QuiltMC mappings

## Acknowledgements

- the [NMSMapper](https://github.com/ScreamingSandals/NMSMapper) library and the ScreamingSandals members

## Licensing

This library is licensed under the [Apache License, Version 2.0](https://github.com/zlataovce/takenaka/blob/master/LICENSE).
