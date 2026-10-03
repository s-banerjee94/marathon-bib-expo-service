package com.timekeeper.bibexpo.importer.batch;

import jakarta.validation.Path;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CsvRowValidator {

    private final Validator validator;

    public List<ValidationError> validate(CsvRow row) {
        return validator.validate(row).stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    String field = switch (path) {
                        case "phone" -> "phoneNumber";
                        case "ageUnreadable" -> "age";
                        default -> mapKey(violation.getPropertyPath()).orElse(path);
                    };
                    return new ValidationError(field, violation.getMessage());
                })
                .toList();
    }

    // A goody or additional-field value is reported under its column name.
    private static Optional<String> mapKey(Path path) {
        for (Path.Node node : path) {
            if (node.getKey() != null) {
                return Optional.of(node.getKey().toString());
            }
        }
        return Optional.empty();
    }
}
