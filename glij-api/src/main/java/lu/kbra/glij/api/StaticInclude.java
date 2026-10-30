package lu.kbra.glij.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Repeatable(StaticIncludes.class)
public @interface StaticInclude {
	String path() default "";

	Class<?> type() default Void.class;

	String methodName() default "";
}
