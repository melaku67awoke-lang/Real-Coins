# CI notes

The supplied project originally contained `gradle/wrapper/gradle-wrapper.properties` but not the wrapper JAR/scripts.
The GitHub Actions workflow therefore provisions Gradle 9.3.1 and generates the standard wrapper at build time before running the wrapper tasks.
