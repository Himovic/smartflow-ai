package com.dnd.smartflow.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.Set;

/** Restricts a string attribute to a closed list of values (null is accepted; combine with @NotBlank). */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OneOf.Validator.class)
public @interface OneOf {
    String[] value();

    String message() default "must be one of {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<OneOf, String> {
        private Set<String> allowed;

        @Override
        public void initialize(OneOf annotation) {
            allowed = Set.of(annotation.value());
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || allowed.contains(value);
        }
    }
}
