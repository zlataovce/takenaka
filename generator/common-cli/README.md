# generator-common-cli

This serves as an abstraction over various CLI generators.

Generators are configured further via system properties.

## Options

```bash
[mk@mk-pc libs]$ java -jar generator-web-cli-0.0.1-SNAPSHOT-all.jar --help
Usage: takenaka options_list
Options: 
    --output, -o [output] -> Output directory { String }
    --versions, -v -> Target Minecraft versions, separated by commas (always required) { String }
    --mappingCache, -c [mapping-cache] -> Caching directory for mappings { String }
    --strictCache [false] -> Restricts cache invalidation conditions 
    --clean [false] -> Removes previous build output before launching 
    --help, -h -> Usage info
```