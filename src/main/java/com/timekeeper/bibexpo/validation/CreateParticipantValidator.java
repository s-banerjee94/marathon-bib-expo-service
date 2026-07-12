package com.timekeeper.bibexpo.validation;

import com.timekeeper.bibexpo.model.dto.request.CreateParticipantRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CreateParticipantValidator implements ConstraintValidator<ValidCreateParticipant, CreateParticipantRequest> {

    @Override
    public boolean isValid(CreateParticipantRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        boolean hasPhoneNumber = request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty();
        boolean hasEmail = request.getEmail() != null && !request.getEmail().trim().isEmpty();

        if (!hasPhoneNumber && !hasEmail) {
            context.buildConstraintViolationWithTemplate("Either phone number or email is required")
                    .addPropertyNode("phoneNumber")
                    .addConstraintViolation();
            valid = false;
        }

        boolean hasDateOfBirth = request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty();
        boolean hasAge = request.getAge() != null;

        if (!hasDateOfBirth && !hasAge) {
            context.buildConstraintViolationWithTemplate("Either date of birth or age is required")
                    .addPropertyNode("dateOfBirth")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}
