package com.timekeeper.bibexpo.importer.batch;

import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvRow {

    private int rowNumber;

    @Size(max = 50, message = "The chip number cannot be longer than 50 characters.")
    private String chipNumber;

    @NotBlank(message = "The bib number is missing.")
    @Size(max = 50, message = "The bib number cannot be longer than 50 characters.")
    private String bibNumber;

    @NotBlank(message = "The name is missing.")
    @Size(min = 2, message = "The name must be at least 2 characters.")
    @Size(max = 200, message = "The name cannot be longer than 200 characters.")
    private String fullName;

    @Size(max = 50, message = "The date of birth cannot be longer than 50 characters.")
    private String dateOfBirth;

    @Min(value = 4, message = "The age must be greater than 3.")
    @Max(value = 150, message = "The age must be 150 or less.")
    private Integer age;

    @AssertFalse(message = "The age must be a whole number.")
    private boolean ageUnreadable;

    @Pattern(regexp = "^[MFO]$", message = "The gender must be M, F, or O.")
    private String gender;

    private String raceName;
    private String categoryName;

    @Pattern(regexp = "^\\d{10}$", message = "The phone number must be exactly 10 digits.")
    private String phone;

    @Email(message = "The email address is not valid.")
    @Size(max = 100, message = "The email address cannot be longer than 100 characters.")
    private String email;

    @Size(max = 100, message = "The country cannot be longer than 100 characters.")
    private String country;

    @Size(max = 100, message = "The city cannot be longer than 100 characters.")
    private String city;

    @Builder.Default
    private Map<String, @Size(max = 100, message = "A goody value cannot be longer than 100 characters.") String>
            goodies = new HashMap<>();

    @Builder.Default
    private Map<String, @Size(max = ParticipantDDB.MAX_ADDITIONAL_FIELD_VALUE,
            message = "An additional field value cannot be longer than {max} characters.") String>
            additionalFields = new HashMap<>();
}
