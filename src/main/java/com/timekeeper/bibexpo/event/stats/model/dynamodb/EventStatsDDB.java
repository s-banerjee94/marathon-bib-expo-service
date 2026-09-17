package com.timekeeper.bibexpo.event.stats.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class EventStatsDDB {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String eventId;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private String statKey;

    private Long count;

    private String updatedAt;

    // The statKey vocabulary. It sits on the record carrying the field because the writer and the
    // readers live in different modules: whoever holds the constants would otherwise be imported
    // by the others purely to spell a key.
    public static final String KEY_TOTAL = "TOTAL";
    public static final String KEY_BIB_COLLECTED = "BIB_COLLECTED";
    // Participants whose bib is collected but who are still owed at least one goody of their own.
    public static final String KEY_GOODIES_PENDING = "GOODIES_PENDING";
    public static final String PREFIX_RACE = "RACE#";
    public static final String PREFIX_CATEGORY = "CATEGORY#";
    public static final String PREFIX_GENDER = "GENDER#";
    public static final String PREFIX_GOODIE = "GOODIE#";
    public static final String PREFIX_HOUR = "HOUR#";
    public static final String PREFIX_DIST = "DIST#";
    public static final String SUFFIX_COLLECTED = "#COLLECTED";
    public static final String SUFFIX_DISTRIBUTED = "#DISTRIBUTED";
    public static final String GENDER_M = PREFIX_GENDER + "M";
    public static final String GENDER_F = PREFIX_GENDER + "F";
    public static final String GENDER_O = PREFIX_GENDER + "O";

    // What an imported roster asked for, as opposed to PREFIX_GOODIE which counts what was handed
    // over: ENTITLED#<goody>#<value> holds how many participants are owed that exact cell value.
    // Both segments are percent-encoded, because a goody name and a cell value are free text and
    // either may contain the separator.
    public static final String PREFIX_ENTITLED = "ENTITLED#";

    // Written in place of the value rows when a column holds more distinct values than a goody
    // plausibly has, which means the wrong column was marked as goodies. Its count is how many
    // distinct values were seen.
    public static final String PREFIX_ENTITLED_OVERFLOW = "ENTITLEDMANY#";

    // HANDED#<goody>#<value> holds how many of the participants behind the matching ENTITLED# row have
    // already been handed that goody, so demand still to serve is one less the other.
    public static final String PREFIX_HANDED = "HANDED#";

    /**
     * The counter key for one goody spelled one particular way.
     */
    public static String entitledKey(String goodieName, String value) {
        return PREFIX_ENTITLED + encodeSegment(goodieName) + "#" + encodeSegment(value);
    }

    /**
     * The handed-out counter key for one goody spelled one particular way.
     */
    public static String handedKey(String goodieName, String value) {
        return PREFIX_HANDED + encodeSegment(goodieName) + "#" + encodeSegment(value);
    }

    /**
     * The entitlement key a handed-out key counts against, or {@code null} if the key is not one.
     */
    public static String entitledKeyOfHanded(String statKey) {
        return statKey != null && statKey.startsWith(PREFIX_HANDED)
                ? PREFIX_ENTITLED + statKey.substring(PREFIX_HANDED.length()) : null;
    }

    /**
     * The goody name and value behind an entitlement key, or {@code null} if the key is not one.
     */
    public static String[] entitledParts(String statKey) {
        String encodedGoodie = entitledGoodieSegment(statKey);
        if (encodedGoodie == null) return null;
        String rest = statKey.substring(PREFIX_ENTITLED.length() + encodedGoodie.length() + 1);
        return new String[]{decodeSegment(encodedGoodie), decodeSegment(rest)};
    }

    /**
     * The still-encoded goody-name segment of an entitlement key, for grouping every value of one
     * goody without decoding either half. Null when the key is not an entitlement key.
     */
    public static String entitledGoodieSegment(String statKey) {
        if (statKey == null || !statKey.startsWith(PREFIX_ENTITLED)) return null;
        int sep = statKey.indexOf('#', PREFIX_ENTITLED.length());
        return sep < 0 ? null : statKey.substring(PREFIX_ENTITLED.length(), sep);
    }

    public static String encodeSegment(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    public static String decodeSegment(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
