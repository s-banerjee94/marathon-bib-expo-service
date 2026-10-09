package com.timekeeper.bibexpo.inventory.service.util;

import com.timekeeper.bibexpo.inventory.api.GoodieIssue;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryStockRepository;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The stock alerts an organization's admins and users receive. Each is called inside the stock change
 * and acts only once that change commits, so one that rolls back never raises an alert. The work runs
 * on the notification executor, never on the request's thread: with open-in-view a request keeps its
 * connection to the end, and one that waited there for a second could starve the pool when many
 * counters post at once. A failure is logged, never thrown, since the stock has already moved.
 *
 * <p>A low-stock alert goes out once per low spell. The drop that takes an item's stock across all
 * locations to its threshold flags the item, and whatever lifts it back above clears the flag. Both are
 * one conditional statement over the committed balances, so counters posting at the same instant
 * raise a single alert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockAlerts {

    private final InventoryItemRepository itemRepository;
    private final InventoryStockRepository stockRepository;
    private final InventoryLocationRepository locationRepository;
    private final NotificationService notificationService;
    private final Executor notificationTaskExecutor;

    /** The item's stock went down: alerts the staff if that took it to its low-stock threshold. */
    public void stockDropped(Long itemId) {
        afterCommit(() -> {
            if (itemRepository.markLowStockAlerted(itemId) == 1) {
                itemRepository.findById(itemId).ifPresent(this::alertLowStock);
            }
        });
    }

    /** The item's stock went up or its threshold changed: once above the threshold, a drop to it alerts again. */
    public void rearm(Long itemId) {
        afterCommit(() -> itemRepository.clearLowStockAlert(itemId));
    }

    /** A hand-over took a shelf below zero. */
    public void shelfOverdrawn(InventoryItem item, GoodieIssue issue, String bibNumber) {
        String location = locationRepository.findById(issue.locationId())
                .map(InventoryLocation::getName).orElse("its location");
        String goody = issue.variantLabel() == null || issue.variantLabel().isBlank()
                ? item.getName() : item.getName() + " (" + issue.variantLabel() + ")";
        NotifyRequest request = staffAlert(item, NotificationType.OUT_OF_STOCK, "Out of Stock",
                String.format("%s has run out at %s: bib %s got one the shelf did not have. "
                        + "Receive or move stock there to put the count right.", goody, location, bibNumber));
        afterCommit(() -> {
            log.atInfo()
                    .addKeyValue("event", "inventory.stock_overdrawn")
                    .addKeyValue("targetOrgId", item.getOrganizationId())
                    .addKeyValue("variantId", issue.variantId())
                    .addKeyValue("locationId", issue.locationId())
                    .log("A hand-over took a shelf below zero");
            notificationService.notify(request);
        });
    }

    private void alertLowStock(InventoryItem item) {
        long onHand = stockRepository.sumOnHandByItemId(item.getId());
        log.atInfo()
                .addKeyValue("event", "inventory.low_stock_alerted")
                .addKeyValue("targetOrgId", item.getOrganizationId())
                .addKeyValue("itemId", item.getId())
                .addKeyValue("onHand", onHand)
                .log("An item ran low on stock");
        String message = onHand <= 0
                ? String.format("%s has run out across all locations. Receive more to keep handing it out.",
                        item.getName())
                : String.format("%s is running low: %d left across all locations. Receive more before it runs out.",
                        item.getName(), onHand);
        notificationService.notify(staffAlert(item, NotificationType.LOW_STOCK, "Low Stock", message));
    }

    private static NotifyRequest staffAlert(InventoryItem item, NotificationType type, String title, String message) {
        return NotifyRequest.builder()
                .audience(NotificationAudience.ORGANIZATION_STAFF)
                .organizationId(item.getOrganizationId())
                .type(type)
                .title(title)
                .message(message)
                .entityType("INVENTORY_ITEM")
                .entityId(String.valueOf(item.getId()))
                .build();
    }

    private void afterCommit(Runnable action) {
        Runnable guarded = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                alertFailed(e);
            }
        };
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationTaskExecutor.execute(guarded);
                } catch (RejectedExecutionException e) {
                    alertFailed(e);
                }
            }
        });
    }

    private static void alertFailed(RuntimeException e) {
        log.atWarn()
                .addKeyValue("event", "inventory.stock_alert_failed")
                .setCause(e)
                .log("A stock alert could not be checked or sent");
    }
}
