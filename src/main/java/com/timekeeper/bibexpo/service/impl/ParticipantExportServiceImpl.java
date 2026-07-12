package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.CsvImportException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.ExportField;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.ParticipantExportService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.service.validator.ParticipantAccessGuard;
import com.timekeeper.bibexpo.util.TextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantExportServiceImpl implements ParticipantExportService {

    private final ParticipantDDBRepository participantRepository;
    private final ParticipantAccessGuard accessGuard;
    private final RaceCategoryNameResolver nameResolver;

    @Override
    public byte[] exportParticipantsToCsv(Long eventId, List<ExportField> fields, User currentUser) {
        log.info("Exporting participants to CSV for event ID: {} by user: {}", eventId, currentUser.getUsername());

        accessGuard.forRead(eventId, currentUser);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(eventId.toString()).build()
        );

        List<ParticipantDDB> allParticipants = participantRepository.getTable().query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();

        log.info("Found {} participants to export for event ID: {}", allParticipants.size(), eventId);

        List<ExportField> exportFields = (fields == null || fields.isEmpty())
                ? Arrays.asList(ExportField.values())
                : fields;

        Set<String> goodiesKeys = collectGoodiesKeys(allParticipants);
        EventNames names = nameResolver.forEvent(eventId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            List<String> headers = buildCsvHeaders(exportFields, goodiesKeys);
            csvPrinter.printRecord(headers);

            for (ParticipantDDB participant : allParticipants) {
                List<String> row = buildCsvRow(participant, exportFields, goodiesKeys, names);
                csvPrinter.printRecord(row);
            }

            csvPrinter.flush();
            log.info("Successfully exported {} participants to CSV for event ID: {}", allParticipants.size(), eventId);
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Failed to generate CSV for event ID: {}", eventId, e);
            throw new CsvImportException("Failed to generate CSV export", e);
        }
    }

    private Set<String> collectGoodiesKeys(List<ParticipantDDB> participants) {
        Set<String> keys = new LinkedHashSet<>();
        for (ParticipantDDB participant : participants) {
            if (participant.getGoodies() != null) {
                keys.addAll(participant.getGoodies().keySet());
            }
        }
        return keys;
    }

    private List<String> buildCsvHeaders(List<ExportField> fields, Set<String> goodiesKeys) {
        List<String> headers = new ArrayList<>();

        for (ExportField field : fields) {
            if (field == ExportField.GOODIES) {
                headers.addAll(goodiesKeys);
            } else {
                headers.add(getHeaderName(field));
            }
        }

        return headers;
    }

    private String getHeaderName(ExportField field) {
        return switch (field) {
            case CHIP_NUMBER -> "CHIP No";
            case BIB_NUMBER -> "BIB No";
            case FULL_NAME -> "NAME";
            case DATE_OF_BIRTH -> "DOB(dd-mm-yyy)";
            case AGE -> "Age";
            case GENDER -> "Gender";
            case RACE_NAME -> "Race";
            case CATEGORY_NAME -> "Category";
            case PHONE_NUMBER -> "Phone";
            case EMAIL -> "Email-Id";
            case COUNTRY -> "Country";
            case CITY -> "City";
            case BIB_COLLECTED_AT -> "Bib Collected At";
            case EMERGENCY_CONTACT_NAME -> "Emergency Contact Name";
            case EMERGENCY_CONTACT_PHONE -> "Emergency Contact Phone";
            case NOTES -> "Notes";
            case GOODIES -> "Goodies";
        };
    }

    private List<String> buildCsvRow(ParticipantDDB participant, List<ExportField> fields,
                                     Set<String> goodiesKeys, EventNames names) {
        List<String> row = new ArrayList<>();

        for (ExportField field : fields) {
            if (field == ExportField.GOODIES) {
                for (String goodieKey : goodiesKeys) {
                    String value = participant.getGoodies() != null
                            ? participant.getGoodies().getOrDefault(goodieKey, "")
                            : "";
                    row.add(value);
                }
            } else {
                row.add(getFieldValue(participant, field, names));
            }
        }

        return row;
    }

    private String getFieldValue(ParticipantDDB participant, ExportField field, EventNames names) {
        return switch (field) {
            case BIB_NUMBER -> TextUtils.nullSafe(participant.getBibNumber());
            case CHIP_NUMBER -> TextUtils.nullSafe(participant.getChipNumber());
            case FULL_NAME -> TextUtils.nullSafe(participant.getFullName());
            case EMAIL -> TextUtils.nullSafe(participant.getEmail());
            case PHONE_NUMBER -> TextUtils.nullSafe(participant.getPhoneNumber());
            case DATE_OF_BIRTH -> TextUtils.nullSafe(participant.getDateOfBirth());
            case AGE -> participant.getAge() != null ? participant.getAge().toString() : "";
            case GENDER -> TextUtils.nullSafe(participant.getGender());
            case COUNTRY -> TextUtils.nullSafe(participant.getCountry());
            case CITY -> TextUtils.nullSafe(participant.getCity());
            case RACE_NAME -> TextUtils.nullSafe(names.raceName(participant.getRaceId()));
            case CATEGORY_NAME -> TextUtils.nullSafe(names.categoryName(participant.getCategoryId()));
            case BIB_COLLECTED_AT -> TextUtils.nullSafe(participant.getBibCollectedAt());
            case EMERGENCY_CONTACT_NAME -> TextUtils.nullSafe(participant.getEmergencyContactName());
            case EMERGENCY_CONTACT_PHONE -> TextUtils.nullSafe(participant.getEmergencyContactPhone());
            case NOTES -> TextUtils.nullSafe(participant.getNotes());
            case GOODIES -> "";
        };
    }
}
