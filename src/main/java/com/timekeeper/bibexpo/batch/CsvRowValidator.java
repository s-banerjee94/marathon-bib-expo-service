package com.timekeeper.bibexpo.batch;

import com.timekeeper.bibexpo.util.CsvRow;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class CsvRowValidator {

    private final Validator validator;

    public List<ValidationError> validate(CsvRow row) {
        Set<ConstraintViolation<CsvRow>> violations = validator.validate(row);

        return violations.stream()
                .map(violation -> ValidationError.builder()
                        .field(getFieldName(violation.getPropertyPath().toString()))
                        .message(violation.getMessage())
                        .build())
                .toList();
    }

    private String getFieldName(String propertyPath) {
		return switch (propertyPath) {
			case "bibNumber" -> "bibNumber";
			case "fullName" -> "fullName";
			case "email" -> "email";
			case "phone" -> "phoneNumber";
			case "age" -> "age";
			case "gender" -> "gender";
			default -> propertyPath;
		};
    }
}
