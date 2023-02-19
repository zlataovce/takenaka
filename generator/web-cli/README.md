# generator-web-cli

This is the CLI interface for the web documentation generator.

Refer to the [generator-common-cli README](../common-cli/README.md) for command-line options.

## Properties

* `me.kcra.takenaka.generator.web.env` (`development` (default/fallback) or `production`) - The generator environment, `production` minifies all files to decrease file size.
* `me.kcra.takenaka.generator.web.concurrencyLimit` (integer, defaults to `-1` [no limit]) - The parallelism limit for the coroutine dispatcher, an appropriate value is chosen by default.
* `me.kcra.takenaka.generator.web.index.foreign` (a complex expression) - Javadoc sites to be linked to in the generated output.
  * the value is a comma-separated (no space after the comma) list of strings, and the string can be:
    * a plus-sign delimited pair of a supported package and a link to the Javadoc root (Javadoc sites _with no modules_): `org.slf4j+https://www.slf4j.org/api`
    * a link to the Javadoc root (Javadoc sites _with modules_): `https://docs.oracle.com/en/java/javase/17/docs/api`
* `me.kcra.takenaka.generator.web.index.jdk` (boolean, defaults to `true`) - Whether the JDK 17 site should be indexed for linking.
* `me.kcra.takenaka.generator.web.skipSynthetics` (boolean, defaults to `true`) - Whether synthetic classes and their members should be skipped.
