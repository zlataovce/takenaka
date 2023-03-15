# generator-web-cli

This is the CLI interface for the web documentation generator.

## Options

```bash
$ java -jar generator-web-cli-0.0.1-SNAPSHOT-all.jar --help
Usage: web-cli options_list
Options:
    --output, -o [output] -> Output directory { String }
    --version, -v -> Target Minecraft version, can be specified multiple times (always required) { String }
    --cache, -c [mapping-cache] -> Caching directory for mappings { String }
    --strictCache [false] -> Enforces strict cache validation
    --clean [false] -> Removes previous build output and cache before launching
    --minifier, -m [DETERMINISTIC] -> The minifier implementation used for minifying the documentation { Value should be one of [deterministic, normal, none] }
    --parallelismLimit [-1] -> Parallelism limit of the coroutine context { Int }
    --javadoc, -j -> Javadoc site that should be referenced in the documentation, can be specified multiple times { String }
    --skipSynthetic [true] -> Excludes synthetic classes and class members from the documentation
    --help, -h -> Usage info
```

### javadoc

The value can be:
* a plus-sign delimited pair of a supported package and a link to the Javadoc root (Javadoc sites _with no modules_): `org.slf4j+https://www.slf4j.org/api`
* a link to the Javadoc root (Javadoc sites _with modules_): `https://docs.oracle.com/en/java/javase/17/docs/api`

**Java 17 API is included automatically for indexing.**
