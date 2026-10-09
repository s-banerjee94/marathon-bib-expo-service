package com.timekeeper.bibexpo.participant.service.impl;

import com.timekeeper.bibexpo.participant.exception.ParticipantExportFailedException;
import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.participant.model.enums.ExportField;
import com.timekeeper.bibexpo.participant.repository.ParticipantDDBRepository;
import com.timekeeper.bibexpo.participant.service.ParticipantExportService;
import com.timekeeper.bibexpo.participant.service.validator.ParticipantAccessGuard;
import com.timekeeper.bibexpo.event.api.EventNames;
import com.timekeeper.bibexpo.event.api.RaceCategoryNameQuery;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantExportServiceImpl implements ParticipantExportService {

    private final ParticipantDDBRepository participantRepository;
    private final ParticipantAccessGuard accessGuard;
    private final RaceCategoryNameQuery nameResolver;

    @Override
    public byte[] exportParticipantsToCsv(Long eventId, List<ExportField> fields, User currentUser) {
        accessGuard.forRead(eventId, currentUser);

        List<ParticipantDDB> allParticipants = participantRepository.findAllByEventId(eventId.toString());

        List<ExportField> exportFields = (fields == null || fields.isEmpty())
                ? List.of(ExportField.values())
                : List.copyOf(new LinkedHashSet<>(fields));

        // Fixed columns claim their headers first, so a clashing goody or additional key is the one
        // that gets the suffix, wherever its group sits in the column order.
        Set<String> takenHeaders = new HashSet<>();
        exportFields.stream()
                .filter(field -> field != ExportField.GOODIES && field != ExportField.ADDITIONAL_FIELDS)
                .forEach(field -> takenHeaders.add(headerOf(field).toLowerCase(Locale.ROOT)));
        Map<String, String> goodieColumns = exportFields.contains(ExportField.GOODIES)
                ? dynamicColumns(allParticipants, ParticipantDDB::getGoodies, "Goody", takenHeaders)
                : Map.of();
        Map<String, String> additionalColumns = exportFields.contains(ExportField.ADDITIONAL_FIELDS)
                ? dynamicColumns(allParticipants, ParticipantDDB::getAdditionalFields, "Additional", takenHeaders)
                : Map.of();
        EventNames names = nameResolver.forEvent(eventId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

            List<String> headers = new ArrayList<>();
            for (ExportField field : exportFields) {
                switch (field) {
                    case GOODIES -> headers.addAll(goodieColumns.values());
                    case ADDITIONAL_FIELDS -> headers.addAll(additionalColumns.values());
                    default -> headers.add(headerOf(field));
                }
            }
            csvPrinter.printRecord(formulaSafe(headers));

            for (ParticipantDDB participant : allParticipants) {
                List<String> row = new ArrayList<>(headers.size());
                for (ExportField field : exportFields) {
                    switch (field) {
                        case GOODIES -> goodieColumns.keySet()
                                .forEach(key -> row.add(valueOf(participant.getGoodies(), key)));
                        case ADDITIONAL_FIELDS -> additionalColumns.keySet()
                                .forEach(key -> row.add(valueOf(participant.getAdditionalFields(), key)));
                        default -> row.add(getFieldValue(participant, field, names));
                    }
                }
                csvPrinter.printRecord(formulaSafe(row));
            }

            csvPrinter.flush();
            log.atInfo()
                    .addKeyValue("event", "participant.exported")
                    .addKeyValue("eventId", eventId)
                    .addKeyValue("rows", allParticipants.size())
                    .addKeyValue("fields", exportFields.size())
                    .log("Participants exported");
            return baos.toByteArray();

        } catch (IOException e) {
            log.atError()
                    .addKeyValue("event", "participant.export_failed")
                    .addKeyValue("eventId", eventId)
                    .setCause(e)
                    .log("Participant export failed");
            throw new ParticipantExportFailedException(e);
        }
    }

    // Cells come from imported and hand-typed data; a leading = + - @ tab or CR would make a
    // spreadsheet run the cell as a formula, so it is prefixed with ' to be read as plain text.
    private static List<String> formulaSafe(List<String> cells) {
        return cells.stream()
                .map(cell -> cell != null && !cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0 ? "'" + cell : cell)
                .toList();
    }

    /**
     * One column per distinct key any participant holds, sorted by name, mapped to its header. A
     * header another column already uses gets the group label, e.g. "City (Additional)".
     */
    private static Map<String, String> dynamicColumns(List<ParticipantDDB> participants,
                                                      Function<ParticipantDDB, Map<String, String>> values,
                                                      String label, Set<String> takenHeaders) {
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        participants.stream().map(values).filter(Objects::nonNull).forEach(map -> keys.addAll(map.keySet()));

        Map<String, String> columns = new LinkedHashMap<>();
        for (String key : keys) {
            String header = toHeader(key);
            if (!takenHeaders.add(header.toLowerCase(Locale.ROOT))) {
                header = header + " (" + label + ")";
                takenHeaders.add(header.toLowerCase(Locale.ROOT));
            }
            columns.put(key, header);
        }
        return columns;
    }

    private static String headerOf(ExportField field) {
        return toHeader(field.name().toLowerCase(Locale.ROOT));
    }

    // "tShirtSize", "t_shirt_size" and "t shirt size" all read "T Shirt Size".
    private static String toHeader(String key) {
        String spaced = key.replace('_', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").trim();
        return Arrays.stream(spaced.split("\\s+"))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
    }

    private static String valueOf(Map<String, String> values, String key) {
        return values == null ? "" : TextUtils.nullSafe(values.get(key));
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
            case RACE_NAME -> TextUtils.nullSafe(names.raceLabel(participant.getRaceId()));
            case CATEGORY_NAME -> TextUtils.nullSafe(names.categoryLabel(participant.getCategoryId()));
            case BIB_COLLECTED_AT -> TextUtils.nullSafe(participant.getBibCollectedAt());
            case EMERGENCY_CONTACT_NAME -> TextUtils.nullSafe(participant.getEmergencyContactName());
            case EMERGENCY_CONTACT_PHONE -> TextUtils.nullSafe(participant.getEmergencyContactPhone());
            case NOTES -> TextUtils.nullSafe(participant.getNotes());
            case GOODIES, ADDITIONAL_FIELDS -> "";
        };
    }
}
