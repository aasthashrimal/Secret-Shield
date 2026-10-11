package com.secretshield.validation;

import java.lang.annotation.*;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordBytesValidator.class)
public @interface PasswordBytes {
    String message() default "Password exceeds supported UTF-8 length";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
