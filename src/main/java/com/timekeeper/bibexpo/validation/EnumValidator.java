package com.timekeeper.bibexpo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class EnumValidator implements ConstraintValidator<ValidEnum, String> {

    private List<String> allowedValues;

    @Override
    public void initialize(ValidEnum annotation) {
        Set<String> excluded = Set.of(annotation.excludes());
        allowedValues = Arrays.stream(annotation.enumClass().getEnumConstants())
                .map(Enum::name)
                .filter(name -> !excluded.contains(name))
                .toList();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (allowedValues.contains(value)) return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                "Invalid value '" + value + "'. Allowed values: " + String.join(", ", allowedValues) + "."
        ).addConstraintViolation();
        return false;
    }
}
