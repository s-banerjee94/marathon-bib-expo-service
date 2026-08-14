package com.timekeeper.bibexpo.participant.api;

import com.timekeeper.bibexpo.participant.model.dynamodb.ParticipantDDB;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

/**
 * The participant roster as the rest of the application may use it.
 *
 * <p>Five modules legitimately touch participant records — distribution stamps collection state on
 * them, the importer replaces the roster wholesale, messaging marks who a campaign reached,
 * participant self-access writes the short code, and event stats rescans them to rebuild counters.
 * That traffic is fine by direction; what it must not do is reach the repository, so this interface
 * is the whole of the surface they get. Anything not declared here is participant's own business.
 *
 * <p>Records are AWS-mapped beans and the paging types are the enhanced client's, deliberately: the
 * participant table is a DynamoDB table and pretending otherwise would cost a translation layer
 * that buys nothing.
 */
public interface ParticipantStore {

    /**
     * Loads one participant by its event and bib number.
     *
     * @param eventId   the owning event
     * @param bibNumber the bib number, unique within the event
     * @return the participant
     * @throws com.timekeeper.bibexpo.participant.exception.ParticipantNotFoundException if no such bib exists
     */
    ParticipantDDB findByEventAndBibOrThrow(Long eventId, String bibNumber);

    /**
     * Lazily pages every participant of an event, for callers that walk the whole roster.
     *
     * @param eventId  the owning event
     * @param pageSize participants fetched per round trip
     * @return the pages, fetched as they are consumed
     */
    SdkIterable<Page<ParticipantDDB>> findPagesByEventId(Long eventId, int pageSize);

    /**
     * Reads a single page of an event's participants, for callers exposing their own cursor.
     *
     * <p>The filter is applied after the limit, so a filtered page may hold fewer rows than asked
     * for and still have more behind it — read on until the returned key is null.
     *
     * @param eventId  the owning event
     * @param limit    maximum rows to read before filtering
     * @param startKey exclusive start key from the previous page, or null/empty to start at the first
     * @param filter   optional DynamoDB filter expression, or null for none
     * @return the page, or null when the event has no participants at all
     */
    Page<ParticipantDDB> findPage(Long eventId, int limit, Map<String, AttributeValue> startKey, Expression filter);

    /**
     * Writes one participant, overwriting any record with the same event and bib number.
     *
     * @param participant the record to write
     */
    void save(ParticipantDDB participant);

    /**
     * Writes many participants in batches, retrying individually whatever DynamoDB leaves unprocessed.
     *
     * @param participants the records to write; null or empty is a no-op
     */
    void batchSave(List<ParticipantDDB> participants);

    /**
     * Deletes every participant of an event.
     *
     * @param eventId the owning event
     * @return how many records were deleted
     */
    int deleteAllByEventId(String eventId);

    /**
     * Sets only the verify short code, leaving every other attribute untouched so a concurrent edit
     * is not overwritten.
     *
     * @param eventId   the owning event
     * @param bibNumber the participant's bib number
     * @param code      the short code to store
     */
    void updateVerifyShortCode(Long eventId, String bibNumber, String code);
}
