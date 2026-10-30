# Example `.glij` sources

These files demonstrate compute and rendering methods. They are **not** a separate
Maven module because they require the game engine (`lu.kbra:gameengine:0.2-SNAPSHOT`),
which is not distributed as part of this project.

Copy `src/main/java/` into a consuming Maven project with the engine dependency
and the GLiJ Maven plugin. Maven will generate the Java and GLSL resources before
normal compilation. The integration tests in `glij-tests` compile and run
equivalent generated Java against a test-only facade of the engine.
