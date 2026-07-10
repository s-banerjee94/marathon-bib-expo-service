package com.timekeeper.bibexpo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates conditional requirements for CreateParticipantRequest:
 * - Either phoneNumber or email must be provided
 * - Either dateOfBirth or age must be provided
 */
@Documented
@Constraint(validatedBy = CreateParticipantValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCreateParticipant {
    String message() default "Invalid participant data";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
