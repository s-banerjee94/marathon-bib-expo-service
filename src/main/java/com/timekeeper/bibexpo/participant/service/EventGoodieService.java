package com.timekeeper.bibexpo.participant.service;

import com.timekeeper.bibexpo.event.exception.EventOperationNotAllowedException;
import com.timekeeper.bibexpo.event.limit.exception.EventLimitExceededException;
import com.timekeeper.bibexpo.event.model.dto.response.EventGoodieResponse;
import com.timekeeper.bibexpo.participant.exception.EventGoodieAlreadyExistsException;
import com.timekeeper.bibexpo.participant.exception.EventGoodieInUseException;
import com.timekeeper.bibexpo.participant.exception.EventGoodieNotFoundException;
import com.timekeeper.bibexpo.participant.model.dto.request.AddEventGoodieRequest;
import com.timekeeper.bibexpo.user.model.entity.User;

import java.util.List;

/**
 * The goodies an event hands out. The list is kept on the event, while participant records carry
 * the imported goodies under their own names, so every change here keeps the two in step.
 *
 * <p>A goody gets onto the list in one of two ways. An import adds its file's goodies columns the
 * way it creates races and categories: a name already on the list, ignoring case and surrounding
 * spaces, is left as it is, a new one is added, and nothing is ever removed. An organizer can also
 * add one by hand, for a goody every participant is owed that no file mentioned — a sponsor's kit
 * that arrives after the roster is in.
 *
 * <p>Changes follow the event's own rules through its operation guard. Adding, or removing a
 * hand-added goody, is allowed while the event is a draft or published. Removing an imported goody
 * rewrites every participant record that carries it, so it is allowed only in draft. No change is
 * allowed once the event is completed or cancelled, or its bill is final, and a goody that has been
 * handed out to anyone cannot be removed at all.
 */
public interface EventGoodieService {

    /**
     * The event's goodies, in the order they were added.
     */
    List<EventGoodieResponse> listGoodies(Long eventId, User currentUser);

    /**
     * Adds a goody by hand. Every participant is owed it, though no record carries it.
     *
     * @throws EventGoodieAlreadyExistsException if the list already has it, ignoring case and spaces
     * @throws EventLimitExceededException if the event already holds as many goodies as its plan allows
     * @throws EventOperationNotAllowedException if the event's status or bill does not allow the change
     */
    EventGoodieResponse addGoodie(Long eventId, AddEventGoodieRequest request, User currentUser);

    /**
     * Removes a goody from the list and, when an import brought it, from every participant record
     * that carries it, then rebuilds the event's counters.
     *
     * @throws EventGoodieNotFoundException if the list has no goody by that name
     * @throws EventGoodieInUseException if it has been handed out, or another feature still points at it
     * @throws EventOperationNotAllowedException if the event's status or bill does not allow the change
     */
    void removeGoodie(Long eventId, String name, User currentUser);

    /**
     * Adds the goodies columns of an imported file. A name already on the list stays as it is, but is
     * marked as imported, since participant records now carry it. New names are added only when all
     * of them fit within the plan's limit; the import itself has already passed the event's rules.
     */
    void addImportedGoodies(Long eventId, List<String> names);
}
