package com.timekeeper.bibexpo.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class SmsTemplateParser {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{(\\w*)}");

    private SmsTemplateParser() {}

    /**
     * Renders a template by replacing #{fieldName} placeholders with values
     * read directly from the participant object via reflection.
     * Placeholders with no matching field, or whose field value is null, are replaced with an empty string.
     *
     * @param template    raw template text containing zero or more #{fieldName} placeholders
     * @param participant source object whose field names must match the placeholder keys
     * @return fully rendered message
     */
    public static String parse(String template, Object participant) {
        if (template == null || template.isBlank()) {
            return "";
        }
        if (participant == null) {
            return template;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);

        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String value = resolveField(participant, fieldName);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Validates that all #{fieldName} placeholders in the template correspond to a valid
     * getter on the given class. Returns the list of invalid placeholder names.
     * An empty list means all placeholders are valid.
     *
     * @param template raw template text
     * @param clazz    class whose getters are checked against the placeholder names
     * @return list of invalid placeholder names found in the template
     */
    public static List<String> validatePlaceholders(String template, Class<?> clazz) {
        Set<String> placeholders = extractPlaceholders(template);
        if (placeholders.isEmpty()) {
            return Collections.emptyList();
        }

        return placeholders.stream()
                .filter(fieldName -> !hasGetter(clazz, fieldName))
                .toList();
    }

    private static Set<String> extractPlaceholders(String template) {
        if (template == null || template.isBlank()) {
            return Collections.emptySet();
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        return matcher.results()
                .map(m -> m.group(1))
                .collect(Collectors.toSet());
    }

    private static boolean hasGetter(Class<?> clazz, String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        }
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            clazz.getMethod(getterName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static String resolveField(Object obj, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = obj.getClass().getMethod(getterName);
            Object value = getter.invoke(obj);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.warn("SMS template placeholder '{}' could not be resolved: {}", fieldName, e.getMessage());
            return "";
        }
    }
}
