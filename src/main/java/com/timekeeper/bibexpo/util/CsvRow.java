package com.timekeeper.bibexpo.util;

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

    private String chipNumber;

    @NotBlank(message = "BIB No. is required")
    private String bibNumber;

    @NotBlank(message = "NAME is required")
    @Size(min = 2, message = "NAME must be at least 2 characters")
    private String fullName;

    private String dateOfBirth;

    @Positive(message = "Age must be a positive number")
    private Integer age;

    @Pattern(regexp = "^[MFO]$", message = "Gender must be M, F, or O")
    private String gender;

    private String raceName;
    private String categoryName;

    @Pattern(regexp = "^\\d{10}$", message = "Phone must be exactly 10 digits")
    private String phone;

    @Email(message = "Invalid email format")
    private String email;

    private String country;
    private String city;

    @Builder.Default
    private Map<String, String> goodies = new HashMap<>();
}
