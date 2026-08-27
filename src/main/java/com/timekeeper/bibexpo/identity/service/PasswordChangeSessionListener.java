package com.timekeeper.bibexpo.identity.service;

import com.timekeeper.bibexpo.user.model.event.PasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Signs every device out when the account's password changes, so a session opened with the old
 * password cannot outlive it. This matters most where a password is being changed
 * <em>because</em> it may be known to someone else.
 *
 * <p>Runs before commit rather than after, so the sessions and the new password land in the same
 * transaction: there is no window where the password has changed but the old devices are still
 * signed in. The user module publishes the event rather than calling here directly, which keeps
 * the dependency pointing down the module layers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeSessionListener {

    private final SessionService sessionService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPasswordChanged(PasswordChangedEvent event) {
        log.info("Password changed for {} — signing out every device", event.getUsername());
        sessionService.endAllSessions(event.getUsername());
    }
}
