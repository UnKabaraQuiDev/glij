package lu.kbra.glij.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Alias for @CacheManager when the engine's CacheManager class is imported. */
@Retention(RetentionPolicy.SOURCE)
@Target({ ElementType.METHOD, ElementType.PARAMETER })
public @interface ShaderCache {
	String value() default "";
}
