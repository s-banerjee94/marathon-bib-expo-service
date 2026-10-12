package com.timekeeper.bibexpo.inventory.service.impl;

import com.timekeeper.bibexpo.audit.api.AuditAction;
import com.timekeeper.bibexpo.audit.api.AuditContextHolder;
import com.timekeeper.bibexpo.audit.api.AuditEntityType;
import com.timekeeper.bibexpo.audit.api.Auditable;
import com.timekeeper.bibexpo.inventory.api.GoodieIssue;
import com.timekeeper.bibexpo.inventory.api.GoodieIssueRecorder;
import com.timekeeper.bibexpo.inventory.exception.IdempotencyKeyReusedException;
import com.timekeeper.bibexpo.inventory.exception.InventoryItemNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryLocationNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.InventoryVariantNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.StockPostingConflictException;
import com.timekeeper.bibexpo.inventory.exception.StockPostingNotFoundException;
import com.timekeeper.bibexpo.inventory.exception.StockPostingNotReversibleException;
import com.timekeeper.bibexpo.inventory.model.dto.request.CreateStockPostingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.ReverseStockPostingRequest;
import com.timekeeper.bibexpo.inventory.model.dto.request.StockPostingLineRequest;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemActivityResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.ItemStockResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockBalanceResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockItemResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockMovementResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockPostingConflictResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockPostingResponse;
import com.timekeeper.bibexpo.inventory.model.dto.response.StockRowResponse;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryAttributeOption;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryLocation;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryMovement;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryPosting;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryStock;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryTerm;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariantAttributeValue;
import com.timekeeper.bibexpo.inventory.model.enums.MovementReason;
import com.timekeeper.bibexpo.inventory.model.enums.MovementType;
import com.timekeeper.bibexpo.inventory.model.enums.StockAction;
import com.timekeeper.bibexpo.inventory.repository.InventoryAttributeOptionRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryItemRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryLocationRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryMovementRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryPostingRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryStockRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryTermRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantAttributeValueRepository;
import com.timekeeper.bibexpo.inventory.repository.InventoryVariantRepository;
import com.timekeeper.bibexpo.inventory.service.StockService;
import com.timekeeper.bibexpo.inventory.service.util.ItemImages;
import com.timekeeper.bibexpo.inventory.service.util.StockAlerts;
import com.timekeeper.bibexpo.inventory.service.validator.InventoryAccessGuard;
import com.timekeeper.bibexpo.shared.error.AccessForbiddenException;
import com.timekeeper.bibexpo.shared.error.InvalidUserDataException;
import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.shared.util.TextUtils;
import com.timekeeper.bibexpo.storage.service.StorageService;
import com.timekeeper.bibexpo.user.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockServiceImpl implements StockService, GoodieIssueRecorder {

    // An adjustment explains a recount. The other reasons belong to movements no adjustment posts.
    private static final Set<MovementReason> ADJUSTMENT_REASONS =
            EnumSet.of(MovementReason.DAMAGED, MovementReason.LOST, MovementReason.CORRECTION);

    private final InventoryStockRepository stockRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryVariantRepository variantRepository;
    private final InventoryVariantAttributeValueRepository variantAttributeValueRepository;
    private final InventoryAttributeOptionRepository optionRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryAccessGuard accessGuard;
    private final StockAlerts stockAlerts;
    private final InventoryPostingRepository postingRepository;
    private final InventoryTermRepository termRepository;
    private final ItemImages itemImages;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public Page<StockItemResponse> listStock(Long organizationId, String search, Long locationId, Boolean lowOnly,
                                             Pageable pageable, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        String needle = TextUtils.trimToNull(search);
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));

        // The database decides which items the page holds, so the page counts describe the filtered
        // set and no item is ever split across two pages.
        Page<InventoryItem> items = itemRepository.searchWithStock(organizationId, needle, locationId,
                Boolean.TRUE.equals(lowOnly) ? Boolean.TRUE : null, effective);
        Map<Long, List<StockRowResponse>> rows = rowsFor(items.getContent(), needle, locationId);
        Map<Long, String> imageUrls = itemImages.urlsFor(items.getContent());
        return items.map(item -> toStockItem(item, rows.getOrDefault(item.getId(), List.of()),
                imageUrls.get(item.getId())));
    }

    /** The balance rows behind one page of items, keyed by item, in five queries however long the page. */
    private Map<Long, List<StockRowResponse>> rowsFor(List<InventoryItem> items, String needle, Long locationId) {
        List<Long> itemIds = items.stream().map(InventoryItem::getId).toList();
        List<InventoryVariant> variants = itemIds.isEmpty() ? List.of() : variantRepository.findByItemIdIn(itemIds);
        List<Long> variantIds = variants.stream().map(InventoryVariant::getId).toList();
        List<InventoryStock> balances = variantIds.isEmpty() ? List.of()
                : stockRepository.findByVariantIdIn(variantIds).stream()
                        .filter(b -> locationId == null || locationId.equals(b.getLocationId()))
                        .toList();
        if (balances.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> itemIdByVariant = variants.stream()
                .collect(Collectors.toMap(InventoryVariant::getId, InventoryVariant::getItemId));
        Map<Long, String> locationNames = locationRepository.findAllById(balances.stream()
                        .map(InventoryStock::getLocationId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, InventoryLocation::getName));
        Map<Long, String> variantLabels = variantLabels(variantIds);

        Map<Long, List<StockRowResponse>> byItem = new HashMap<>();
        for (InventoryStock balance : balances) {
            byItem.computeIfAbsent(itemIdByVariant.get(balance.getVariantId()), k -> new ArrayList<>())
                    .add(StockRowResponse.builder()
                            .id(balance.getId())
                            .variantId(balance.getVariantId())
                            .variantLabel(variantLabels.get(balance.getVariantId()))
                            .locationId(balance.getLocationId())
                            .locationName(locationNames.get(balance.getLocationId()))
                            .onHand(balance.getOnHand())
                            .updatedAt(balance.getUpdatedAt())
                            .updatedBy(balance.getLastModifiedBy())
                            .build());
        }
        byItem.values().forEach(list -> list.sort(
                Comparator.comparing(StockRowResponse::getVariantLabel, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(StockRowResponse::getLocationName, Comparator.nullsLast(Comparator.naturalOrder()))));
        narrowToSearch(items, byItem, needle);
        return byItem;
    }

    // A search that matched a variant value or a location name should leave only the rows it matched,
    // so the total describes what is on screen. An item matched by its own name keeps all of its rows,
    // and so does one the database matched on something this pass cannot see — the collation ignores
    // accents where Java does not, and an item with no rows at all would read as a bug.
    private static void narrowToSearch(List<InventoryItem> items, Map<Long, List<StockRowResponse>> byItem,
                                       String needle) {
        if (needle == null) {
            return;
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        for (InventoryItem item : items) {
            List<StockRowResponse> rows = byItem.get(item.getId());
            if (rows == null || item.getName().toLowerCase(Locale.ROOT).contains(lower)) {
                continue;
            }
            List<StockRowResponse> kept = rows.stream().filter(r -> contains(r.getVariantLabel(), lower)
                    || contains(r.getLocationName(), lower)).toList();
            if (!kept.isEmpty()) {
                byItem.put(item.getId(), kept);
            }
        }
    }

    private static boolean contains(String value, String lower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lower);
    }

    // "S / Yellow" — the variant's attribute values in attribute order. Absent for the single
    // attribute-less variant an item without real variants carries.
    private Map<Long, String> variantLabels(List<Long> variantIds) {
        List<InventoryVariantAttributeValue> values = variantIds.isEmpty() ? List.of()
                : variantAttributeValueRepository.findByVariantIdIn(variantIds);
        if (values.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> options = optionRepository.findAllById(values.stream()
                        .map(InventoryVariantAttributeValue::getOptionId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(InventoryAttributeOption::getId, InventoryAttributeOption::getValue));
        return values.stream()
                .sorted(Comparator.comparing(InventoryVariantAttributeValue::getAttributeId))
                .collect(Collectors.groupingBy(InventoryVariantAttributeValue::getVariantId,
                        Collectors.mapping(v -> v.getOptionId() != null
                                        ? options.getOrDefault(v.getOptionId(), "")
                                        : Objects.toString(v.getRawValue(), ""),
                                Collectors.joining(" / "))));
    }

    private static StockItemResponse toStockItem(InventoryItem item, List<StockRowResponse> rows, String imageUrl) {
        int onHand = rows.stream().mapToInt(StockRowResponse::getOnHand).sum();
        return StockItemResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .imageUrl(imageUrl)
                .onHand(onHand)
                .locationCount((int) rows.stream().map(StockRowResponse::getLocationId).distinct().count())
                .lowStockThreshold(item.getLowStockThreshold())
                // An item with no threshold of its own is low only once the shelf is empty.
                .low(onHand <= (item.getLowStockThreshold() == null ? 0 : item.getLowStockThreshold()))
                .updatedAt(rows.stream().map(StockRowResponse::getUpdatedAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null))
                .rows(rows)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovementResponse> listMovements(Long organizationId, Long itemId, Long variantId,
                                                     MovementType type, MovementReason reason, String performedBy,
                                                     Instant occurredFrom, Instant occurredTo,
                                                     Pageable pageable, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new InvalidUserDataException("The start of the date range must come before its end.");
        }
        // Lines posted by one operation share a clock reading, so occurredAt alone leaves ties the
        // database may break differently each time — a row could land on two pages or on none.
        Pageable effective = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        Page<InventoryMovement> page = movementRepository.search(organizationId, itemId, variantId, type, reason,
                TextUtils.trimToNull(performedBy), occurredFrom, occurredTo, effective);

        // Names are resolved for the whole page at once, so the screen never fetches items one by one.
        List<InventoryMovement> rows = page.getContent();
        Map<Long, String> itemNames = itemRepository.findAllById(
                        rows.stream().map(InventoryMovement::getItemId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryItem::getId, InventoryItem::getName));
        Map<Long, String> variantLabels = variantLabels(rows.stream().map(InventoryMovement::getVariantId).distinct().toList());
        Map<Long, String> locationNames = locationRepository.findAllById(rows.stream()
                        .flatMap(m -> Stream.of(m.getFromLocationId(), m.getToLocationId()))
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, InventoryLocation::getName));
        Map<Long, Long> reversedBy = postingRepository.findAllById(rows.stream().map(InventoryMovement::getPostingId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .filter(p -> p.getReversedByPostingId() != null)
                .collect(Collectors.toMap(InventoryPosting::getId, InventoryPosting::getReversedByPostingId));

        return page.map(m -> {
            StockMovementResponse row = StockMovementResponse.fromEntity(m);
            row.setItemName(itemNames.get(m.getItemId()));
            row.setVariantLabel(variantLabels.get(m.getVariantId()));
            row.setFromLocationName(locationNames.get(m.getFromLocationId()));
            row.setToLocationName(locationNames.get(m.getToLocationId()));
            row.setReversedByPostingId(reversedBy.get(m.getPostingId()));
            return row;
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ItemStockResponse getItemStock(Long organizationId, Long itemId, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        InventoryItem item = itemRepository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(InventoryItemNotFoundException::new);
        List<InventoryVariant> variants = new ArrayList<>(variantRepository.findByItemId(itemId));
        List<Long> variantIds = variants.stream().map(InventoryVariant::getId).toList();
        Map<Long, String> labels = variantLabels(variantIds);
        variants.sort(definedOrder(variantIds));
        List<StockBalanceResponse> balances = variantIds.isEmpty() ? List.of()
                : stockRepository.findByVariantIdIn(variantIds).stream().map(StockBalanceResponse::fromEntity).toList();

        return ItemStockResponse.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .imageUrl(itemImages.urlFor(item, variants))
                .unitName(termRepository.findById(item.getUnitId()).map(InventoryTerm::getName).orElse(null))
                .lowStockThreshold(item.getLowStockThreshold())
                .onHand(balances.stream().mapToInt(StockBalanceResponse::getOnHand).sum())
                .variants(variants.stream().map(v -> new ItemStockResponse.Variant(v.getId(), labels.get(v.getId()),
                        storageService.createDownloadUrl(v.getImageKey()))).toList())
                .locations(locationRepository.search(organizationId, null, null).stream()
                        .map(l -> new ItemStockResponse.Location(l.getId(), l.getName())).toList())
                .balances(balances)
                .allowedActions(allowedActions(currentUser))
                .build();
    }

    // Option ids follow the order an attribute's values were defined in (S, M, L, XL), where the labels would
    // sort alphabetically. A value typed in rather than picked compares as text.
    private Comparator<InventoryVariant> definedOrder(List<Long> variantIds) {
        Map<Long, String> keys = variantIds.isEmpty() ? Map.of()
                : variantAttributeValueRepository.findByVariantIdIn(variantIds).stream()
                        .sorted(Comparator.comparing(InventoryVariantAttributeValue::getAttributeId))
                        .collect(Collectors.groupingBy(InventoryVariantAttributeValue::getVariantId,
                                Collectors.mapping(v -> v.getOptionId() != null
                                                ? "%019d".formatted(v.getOptionId())
                                                : Objects.toString(v.getRawValue(), ""),
                                        Collectors.joining("|"))));
        return Comparator.comparing((InventoryVariant v) -> keys.getOrDefault(v.getId(), ""))
                .thenComparing(InventoryVariant::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ItemActivityResponse> listItemActivity(Long organizationId, Long itemId, Pageable pageable,
                                                       User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        itemRepository.findByIdAndOrganizationId(itemId, organizationId)
                .orElseThrow(InventoryItemNotFoundException::new);
        Page<InventoryMovementRepository.ActivityEntry> entries = movementRepository.findItemActivity(organizationId,
                itemId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));

        List<Long> postingIds = entries.stream().map(InventoryMovementRepository.ActivityEntry::getPostingId)
                .filter(Objects::nonNull).toList();
        Map<Long, InventoryPosting> postings = postingRepository.findAllById(postingIds).stream()
                .collect(Collectors.toMap(InventoryPosting::getId, Function.identity()));
        // Other items' lines too: an undo takes back the whole posting, so all of it decides canReverse.
        Map<Long, List<InventoryMovement>> postingLines = postingIds.isEmpty() ? Map.of()
                : movementRepository.findByPostingIdInOrderById(postingIds).stream()
                        .collect(Collectors.groupingBy(InventoryMovement::getPostingId));
        Map<Long, InventoryMovement> loneLines = movementRepository.findAllById(entries.stream()
                        .filter(e -> e.getPostingId() == null)
                        .map(InventoryMovementRepository.ActivityEntry::getFirstLineId).toList()).stream()
                .collect(Collectors.toMap(InventoryMovement::getId, Function.identity()));

        List<InventoryMovement> allLines = Stream.concat(postingLines.values().stream().flatMap(List::stream),
                loneLines.values().stream()).toList();
        List<Long> variantIds = allLines.stream().map(InventoryMovement::getVariantId).distinct().toList();
        Map<Long, String> labels = variantLabels(variantIds);
        Map<Long, String> locationNames = locationRepository.findAllById(allLines.stream()
                        .flatMap(m -> Stream.of(m.getFromLocationId(), m.getToLocationId()))
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryLocation::getId, InventoryLocation::getName));
        Set<Long> liveVariants = variantRepository.findAllById(variantIds).stream().map(InventoryVariant::getId)
                .collect(Collectors.toSet());
        Map<String, Integer> onHand = variantIds.isEmpty() ? Map.of()
                : stockRepository.findByVariantIdIn(variantIds).stream()
                        .collect(Collectors.toMap(b -> b.getVariantId() + ":" + b.getLocationId(), InventoryStock::getOnHand));

        return entries.map(entry -> {
            if (entry.getPostingId() == null) {
                InventoryMovement m = loneLines.get(entry.getFirstLineId());
                return ItemActivityResponse.builder()
                        .type(m.getType())
                        .reason(m.getReason())
                        .reference(m.getReference())
                        .performedBy(m.getCreatedBy())
                        .occurredAt(m.getOccurredAt())
                        .lines(List.of(activityLine(m, labels, locationNames)))
                        .build();
            }
            InventoryPosting posting = postings.get(entry.getPostingId());
            List<InventoryMovement> lines = postingLines.get(posting.getId());
            List<InventoryMovement> mine = lines.stream().filter(m -> itemId.equals(m.getItemId())).toList();
            Set<MovementReason> reasons = mine.stream().map(InventoryMovement::getReason).collect(Collectors.toSet());
            return ItemActivityResponse.builder()
                    .postingId(posting.getId())
                    .type(posting.getType())
                    .reason(reasons.size() == 1 ? reasons.iterator().next() : null)
                    .reference(posting.getReference())
                    .performedBy(posting.getCreatedBy())
                    .occurredAt(mine.get(0).getOccurredAt())
                    .reversesPostingId(posting.getReversesPostingId())
                    .reversedByPostingId(posting.getReversedByPostingId())
                    .canReverse(canReverse(currentUser, posting, lines, liveVariants, onHand))
                    .lines(mine.stream().map(m -> activityLine(m, labels, locationNames)).toList())
                    .build();
        });
    }

    private static ItemActivityResponse.Line activityLine(InventoryMovement m, Map<Long, String> labels,
                                                          Map<Long, String> locationNames) {
        return ItemActivityResponse.Line.builder()
                .variantId(m.getVariantId())
                .variantLabel(labels.get(m.getVariantId()))
                .reason(m.getReason())
                .fromLocationId(m.getFromLocationId())
                .fromLocationName(locationNames.get(m.getFromLocationId()))
                .toLocationId(m.getToLocationId())
                .toLocationName(locationNames.get(m.getToLocationId()))
                .quantity(m.getQuantity())
                .balanceAfter(m.getBalanceAfter())
                .build();
    }

    // The undo rule read without writing anything: false for whatever reversePosting would refuse.
    private boolean canReverse(User user, InventoryPosting posting, List<InventoryMovement> lines,
                               Set<Long> liveVariants, Map<String, Integer> onHand) {
        if (posting.getType() == MovementType.REVERSAL || posting.getReversedByPostingId() != null
                || !mayReverse(user, posting)) {
            return false;
        }
        return lines.stream().allMatch(line -> {
            if (!liveVariants.contains(line.getVariantId())) {
                return false;
            }
            if (line.getToLocationId() != null) {
                return onHand.getOrDefault(line.getVariantId() + ":" + line.getToLocationId(), 0)
                        - line.getQuantity() >= undoFloor(line);
            }
            return onHand.getOrDefault(line.getVariantId() + ":" + line.getFromLocationId(), 0)
                    + line.getQuantity() <= 1_000_000;
        });
    }

    @Auditable(entityType = AuditEntityType.STOCK_POSTING, action = AuditAction.CREATE)
    @Override
    @Transactional
    public StockPostingResponse postStock(Long organizationId, String idempotencyKey, CreateStockPostingRequest request,
                                          User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        String key = requireIdempotencyKey(idempotencyKey);
        List<StockPostingLineRequest> lines = request.getLines();
        validateLines(request.getType(), lines);
        requireAllowed(currentUser, actionFor(request));
        String hash = sha256(request.toString());
        Optional<InventoryPosting> replay = findReplay(organizationId, currentUser, key, hash);
        if (replay.isPresent()) {
            return respond(replay.get());
        }

        Map<Long, InventoryVariant> variants = requireVariantsInOrg(
                lines.stream().map(StockPostingLineRequest::getVariantId).distinct().toList(), organizationId);
        requireLocationsInOrg(lines.stream()
                .flatMap(l -> Stream.of(l.getFromLocationId(), l.getToLocationId(), l.getLocationId()))
                .filter(Objects::nonNull).distinct().toList(), organizationId);

        // Saved before any stock moves: a twin request carrying the same key waits here on the unique key
        // until this one commits, then fails instead of posting again.
        Run run = new Run(postingRepository.saveAndFlush(InventoryPosting.builder()
                .organizationId(organizationId)
                .type(request.getType())
                .reference(TextUtils.trimToNull(request.getReference()))
                .note(TextUtils.trimToNull(request.getNote()))
                .lineCount(lines.size())
                .idempotencyKey(key)
                .requestHash(hash)
                .build()), currentUser.getUsername());

        for (int i = 0; i < lines.size(); i++) {
            StockPostingLineRequest line = lines.get(i);
            InventoryVariant variant = variants.get(line.getVariantId());
            switch (request.getType()) {
                case RECEIPT -> {
                    if (stockIn(run, i, variant, line.getToLocationId(), line.getQuantity(), MovementType.RECEIPT, null)) {
                        run.raised().add(variant.getItemId());
                    }
                }
                case TRANSFER -> {
                    if (stockOut(run, i, variant, line.getFromLocationId(), line.getQuantity(), MovementType.TRANSFER, null,
                            0)) {
                        stockIn(run, i, variant, line.getToLocationId(), line.getQuantity(), MovementType.TRANSFER, null);
                    }
                }
                default -> adjustLine(run, i, variant, line);
            }
        }
        return finish(run);
    }

    // A count states what the shelf holds, checked against what the counter last saw, so a hand-out made
    // while counting is never silently overwritten. A write-off only ever takes stock away.
    private void adjustLine(Run run, int index, InventoryVariant variant, StockPostingLineRequest line) {
        if (line.getReason() != MovementReason.CORRECTION) {
            if (stockOut(run, index, variant, line.getLocationId(), line.getQuantity(), MovementType.ADJUSTMENT,
                    line.getReason(), 0)) {
                run.dropped().add(variant.getItemId());
            }
            return;
        }
        InventoryStock stock = findOrCreateStock(variant.getId(), line.getLocationId(), run.actor());
        if (!stock.getOnHand().equals(line.getExpectedOnHand())) {
            run.conflicts().add(conflict(index, variant.getId(), line.getLocationId(), stock.getOnHand(),
                    line.getExpectedOnHand(), null));
            return;
        }
        int delta = line.getCountedOnHand() - stock.getOnHand();
        if (delta > 0 && stockIn(run, index, variant, line.getLocationId(), delta, MovementType.ADJUSTMENT,
                MovementReason.CORRECTION)) {
            run.raised().add(variant.getItemId());
        } else if (delta < 0 && stockOut(run, index, variant, line.getLocationId(), -delta, MovementType.ADJUSTMENT,
                MovementReason.CORRECTION, 0)) {
            run.dropped().add(variant.getItemId());
        }
    }

    @Auditable(entityType = AuditEntityType.STOCK_POSTING, action = AuditAction.CREATE)
    @Override
    @Transactional
    public StockPostingResponse reversePosting(Long organizationId, Long postingId, String idempotencyKey,
                                               ReverseStockPostingRequest request, User currentUser) {
        accessGuard.requireOrgAccess(currentUser, organizationId);
        String key = requireIdempotencyKey(idempotencyKey);
        InventoryPosting original = postingRepository.findByIdAndOrganizationId(postingId, organizationId)
                .orElseThrow(StockPostingNotFoundException::new);
        requireMayReverse(currentUser, original);
        String note = request == null ? null : TextUtils.trimToNull(request.getNote());
        String hash = sha256("reverse " + postingId + " " + note);
        Optional<InventoryPosting> replay = findReplay(organizationId, currentUser, key, hash);
        if (replay.isPresent()) {
            return respond(replay.get());
        }
        if (original.getType() == MovementType.REVERSAL) {
            throw new StockPostingNotReversibleException(StockPostingNotReversibleException.IS_REVERSAL);
        }

        Run run = new Run(postingRepository.saveAndFlush(InventoryPosting.builder()
                .organizationId(organizationId)
                .type(MovementType.REVERSAL)
                .reference(original.getReference())
                .note(note)
                .lineCount(original.getLineCount())
                .reversesPostingId(original.getId())
                .idempotencyKey(key)
                .requestHash(hash)
                .build()), currentUser.getUsername());
        if (postingRepository.markReversed(original.getId(), run.posting().getId()) == 0) {
            throw new StockPostingNotReversibleException(StockPostingNotReversibleException.ALREADY_REVERSED);
        }

        List<InventoryMovement> lines = movementRepository.findByPostingIdOrderById(original.getId());
        Map<Long, InventoryVariant> variants = variantRepository.findAllById(
                        lines.stream().map(InventoryMovement::getVariantId).distinct().toList()).stream()
                .collect(Collectors.toMap(InventoryVariant::getId, Function.identity()));
        for (int i = 0; i < lines.size(); i++) {
            InventoryMovement line = lines.get(i);
            InventoryVariant variant = variants.get(line.getVariantId());
            boolean cameIn = line.getToLocationId() != null;
            Long locationId = cameIn ? line.getToLocationId() : line.getFromLocationId();
            // A variant removed since has no shelf left to take the units back.
            if (variant == null) {
                run.conflicts().add(conflict(i, line.getVariantId(), locationId, 0, null, line.getQuantity()));
            } else if (cameIn) {
                if (stockOut(run, i, variant, locationId, line.getQuantity(), MovementType.REVERSAL, null,
                        undoFloor(line))) {
                    run.dropped().add(variant.getItemId());
                }
            } else if (stockIn(run, i, variant, locationId, line.getQuantity(), MovementType.REVERSAL, null)) {
                run.raised().add(variant.getItemId());
            }
        }
        if (!run.conflicts().isEmpty()) {
            throw new StockPostingConflictException(undoRefusal(run.conflicts(), lines, variants), run.conflicts());
        }
        return finish(run);
    }

    // An undo may take a shelf below zero only as far as it stood just before the line it takes back, so a
    // count that lifted an overdrawn shelf can be undone and a receipt whose stock was handed out cannot.
    // A line from before balances were kept may not take it below zero at all.
    private static int undoFloor(InventoryMovement line) {
        return line.getBalanceAfter() == null ? 0 : Math.min(0, line.getBalanceAfter() - line.getQuantity());
    }

    // The screen showing this has no lines to mark, so the message names the first shelf that refused.
    private String undoRefusal(List<StockPostingConflictResponse.Line> conflicts, List<InventoryMovement> lines,
                               Map<Long, InventoryVariant> variants) {
        StockPostingConflictResponse.Line first = conflicts.get(0);
        InventoryMovement line = lines.get(first.getIndex());
        if (!variants.containsKey(line.getVariantId())) {
            return "You can't undo this change because a variant it moved has since been deleted.";
        }
        String item = itemRepository.findById(line.getItemId()).map(InventoryItem::getName).orElse("This item");
        String label = variantLabels(List.of(line.getVariantId())).get(line.getVariantId());
        String location = locationRepository.findById(first.getLocationId()).map(InventoryLocation::getName)
                .orElse("its location");
        String outcome = line.getToLocationId() != null
                ? "would go to " + (first.getOnHand() - first.getRequested())
                : "would hold more than 1,000,000 units";
        int others = conflicts.size() - 1;
        return "You can't undo this change because " + item + (label == null ? "" : " " + label) + " at " + location
                + " " + outcome + (others == 0 ? "." : ", and so would " + others
                + (others == 1 ? " other shelf." : " other shelves."));
    }

    // ---- postings ----------------------------------------------------------------

    // One posting being written: what its lines share, and what they found along the way.
    private record Run(InventoryPosting posting, String actor, Instant now,
                       List<StockPostingConflictResponse.Line> conflicts, Set<Long> raised, Set<Long> dropped) {
        Run(InventoryPosting posting, String actor) {
            this(posting, actor, Instant.now(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
        }
    }

    // Puts units on a shelf, creating its balance on first use. False, with the line marked, past the cap.
    private boolean stockIn(Run run, int index, InventoryVariant variant, Long locationId, int quantity,
                            MovementType type, MovementReason reason) {
        InventoryStock stock = findOrCreateStock(variant.getId(), locationId, run.actor());
        if (stockRepository.add(stock.getId(), quantity, false, run.now(), run.actor()) == 0) {
            run.conflicts().add(conflict(index, variant.getId(), locationId, stock.getOnHand(), null, quantity));
            return false;
        }
        movementRepository.save(movement(run, variant, type, reason, quantity).toLocationId(locationId)
                .balanceAfter(stock.getOnHand() + quantity).build());
        return true;
    }

    // Takes units off a shelf, never below the floor: 0, except for an undo. False, with the line marked, when
    // the shelf holds too few. The balance is locked, so what it read is what the shelf holds.
    private boolean stockOut(Run run, int index, InventoryVariant variant, Long locationId, int quantity,
                             MovementType type, MovementReason reason, int floor) {
        InventoryStock stock = stockRepository.lockByVariantIdAndLocationId(variant.getId(), locationId);
        if (stock == null || stock.getOnHand() - quantity < floor) {
            run.conflicts().add(conflict(index, variant.getId(), locationId, stock == null ? 0 : stock.getOnHand(),
                    null, quantity));
            return false;
        }
        stockRepository.deduct(stock.getId(), quantity, true, run.now(), run.actor());
        movementRepository.save(movement(run, variant, type, reason, quantity).fromLocationId(locationId)
                .balanceAfter(stock.getOnHand() - quantity).build());
        return true;
    }

    private static InventoryMovement.InventoryMovementBuilder movement(Run run, InventoryVariant variant,
                                                                        MovementType type, MovementReason reason,
                                                                        int quantity) {
        return InventoryMovement.builder()
                .organizationId(run.posting().getOrganizationId())
                .itemId(variant.getItemId())
                .variantId(variant.getId())
                .postingId(run.posting().getId())
                .type(type)
                .reason(reason)
                .quantity(quantity)
                .reference(run.posting().getReference())
                .occurredAt(run.now());
    }

    private static StockPostingConflictResponse.Line conflict(int index, Long variantId, Long locationId, Integer onHand,
                                                              Integer expectedOnHand, Integer requested) {
        return StockPostingConflictResponse.Line.builder().index(index).variantId(variantId).locationId(locationId)
                .onHand(onHand).expectedOnHand(expectedOnHand).requested(requested).build();
    }

    // Any line the stock could not take rolls the whole posting back, its own row included, and frees the key.
    private StockPostingResponse finish(Run run) {
        if (!run.conflicts().isEmpty()) {
            throw new StockPostingConflictException(run.conflicts());
        }
        run.raised().forEach(stockAlerts::rearm);
        run.dropped().forEach(stockAlerts::stockDropped);
        InventoryPosting posting = run.posting();
        auditAgainst(posting.getOrganizationId(), posting.getId());
        log.atInfo()
                .addKeyValue("event", "inventory.stock_posted")
                .addKeyValue("targetOrgId", posting.getOrganizationId())
                .addKeyValue("postingId", posting.getId())
                .addKeyValue("postingType", posting.getType())
                .addKeyValue("reversesPostingId", posting.getReversesPostingId())
                .addKeyValue("lineCount", posting.getLineCount())
                .log("Stock posted");
        return respond(posting);
    }

    // The balance now at every shelf the posting's lines touched, so a repeated key answers with today's numbers.
    private StockPostingResponse respond(InventoryPosting posting) {
        List<InventoryMovement> lines = movementRepository.findByPostingIdOrderById(posting.getId());
        Set<String> cells = lines.stream()
                .flatMap(m -> Stream.of(m.getFromLocationId(), m.getToLocationId())
                        .filter(Objects::nonNull).map(locationId -> m.getVariantId() + ":" + locationId))
                .collect(Collectors.toSet());
        List<StockBalanceResponse> balances = lines.isEmpty() ? List.of()
                : stockRepository.findByVariantIdIn(lines.stream().map(InventoryMovement::getVariantId).distinct().toList())
                        .stream()
                        .filter(s -> cells.contains(s.getVariantId() + ":" + s.getLocationId()))
                        .map(StockBalanceResponse::fromEntity)
                        .toList();
        return StockPostingResponse.fromEntity(posting, balances);
    }

    private static String requireIdempotencyKey(String key) {
        String trimmed = TextUtils.trimToNull(key);
        if (trimmed == null || trimmed.length() > 100) {
            throw new InvalidUserDataException("Send a new Idempotency-Key header of at most 100 characters with each change.");
        }
        return trimmed;
    }

    // A repeated key answers with what it already posted; the same key on a different request is refused.
    private Optional<InventoryPosting> findReplay(Long organizationId, User currentUser, String key, String hash) {
        Optional<InventoryPosting> posted = postingRepository.findByOrganizationIdAndCreatedByAndIdempotencyKey(
                organizationId, currentUser.getUsername(), key);
        if (posted.isPresent() && !posted.get().getRequestHash().equals(hash)) {
            throw new IdempotencyKeyReusedException();
        }
        return posted;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void validateLines(MovementType type, List<StockPostingLineRequest> lines) {
        if (type != MovementType.RECEIPT && type != MovementType.TRANSFER && type != MovementType.ADJUSTMENT) {
            throw new InvalidUserDataException("Choose whether you are receiving, moving or adjusting stock.");
        }
        Set<String> cells = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            StockPostingLineRequest l = lines.get(i);
            int n = i + 1;
            String cell = switch (type) {
                case RECEIPT -> {
                    require(l.getToLocationId() != null && l.getQuantity() != null,
                            "Choose a location and a quantity for line " + n + ".");
                    yield l.getVariantId() + ":" + l.getToLocationId();
                }
                case TRANSFER -> {
                    require(l.getFromLocationId() != null && l.getToLocationId() != null && l.getQuantity() != null,
                            "Choose where line " + n + " comes from, where it goes and how many.");
                    require(!l.getFromLocationId().equals(l.getToLocationId()),
                            "You cannot move line " + n + " to the location it comes from.");
                    yield l.getVariantId() + ":" + l.getFromLocationId() + ">" + l.getToLocationId();
                }
                default -> {
                    require(l.getLocationId() != null && ADJUSTMENT_REASONS.contains(l.getReason()),
                            "Choose a location for line " + n + " and whether it was counted, damaged or lost.");
                    if (l.getReason() == MovementReason.CORRECTION) {
                        require(l.getCountedOnHand() != null && l.getExpectedOnHand() != null,
                                "Enter what you counted on line " + n + " and what the shelf showed before.");
                    } else {
                        require(l.getQuantity() != null, "Enter how many units on line " + n + " were damaged or lost.");
                    }
                    yield l.getVariantId() + ":" + l.getLocationId();
                }
            };
            require(cells.add(cell), "You listed the same variant and location twice, so merge line " + n
                    + " into the earlier one.");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidUserDataException(message);
        }
    }

    // Every id on every line is checked against the organization in the path, never trusted because a screen sent it.
    private Map<Long, InventoryVariant> requireVariantsInOrg(List<Long> variantIds, Long organizationId) {
        List<InventoryVariant> variants = variantRepository.findAllById(variantIds);
        List<Long> itemIds = variants.stream().map(InventoryVariant::getItemId).distinct().toList();
        boolean allOwned = itemRepository.findAllById(itemIds).stream()
                .filter(item -> item.getOrganizationId().equals(organizationId)).count() == itemIds.size();
        if (variants.size() != variantIds.size() || !allOwned) {
            throw new InventoryVariantNotFoundException();
        }
        return variants.stream().collect(Collectors.toMap(InventoryVariant::getId, Function.identity()));
    }

    private void requireLocationsInOrg(List<Long> locationIds, Long organizationId) {
        long owned = locationRepository.findAllById(locationIds).stream()
                .filter(location -> location.getOrganizationId().equals(organizationId)).count();
        if (owned != locationIds.size()) {
            throw new InventoryLocationNotFoundException();
        }
    }

    // ---- who may do what ----------------------------------------------------------------

    // Removing stock and undoing other people's work is an admin decision; an organizer user may receive, move
    // and count. Distributors never reach inventory at all.
    private static Set<StockAction> allowedActions(User user) {
        return switch (user.getRole()) {
            case ROOT, ADMIN, ORGANIZER_ADMIN -> EnumSet.allOf(StockAction.class);
            case ORGANIZER_USER -> EnumSet.of(StockAction.RECEIPT, StockAction.TRANSFER, StockAction.COUNT,
                    StockAction.REVERSE);
            default -> EnumSet.noneOf(StockAction.class);
        };
    }

    // A posting of counts alone is a COUNT; one that writes anything off is a full ADJUSTMENT.
    private static StockAction actionFor(CreateStockPostingRequest request) {
        return switch (request.getType()) {
            case RECEIPT -> StockAction.RECEIPT;
            case TRANSFER -> StockAction.TRANSFER;
            default -> request.getLines().stream().allMatch(l -> l.getReason() == MovementReason.CORRECTION)
                    ? StockAction.COUNT : StockAction.ADJUSTMENT;
        };
    }

    private static void requireAllowed(User user, StockAction action) {
        if (!allowedActions(user).contains(action)) {
            throw new AccessForbiddenException("You do not have permission to make this stock change.");
        }
    }

    private static void requireMayReverse(User user, InventoryPosting posting) {
        requireAllowed(user, StockAction.REVERSE);
        if (!mayReverse(user, posting)) {
            throw new AccessForbiddenException("You can only undo your own stock changes from the last 24 hours.");
        }
    }

    // An organizer user may undo only their own postings, and only within a day of posting them.
    private static boolean mayReverse(User user, InventoryPosting posting) {
        return allowedActions(user).contains(StockAction.REVERSE)
                && (user.getRole() != UserRole.ORGANIZER_USER
                || (user.getUsername().equals(posting.getCreatedBy())
                && !posting.getCreatedAt().isBefore(Instant.now().minus(Duration.ofDays(1)))));
    }

    @Override
    @Transactional
    public void issue(List<GoodieIssue> issues, String bibNumber, String actor) {
        issues.forEach(issue -> postHandover(issue, MovementType.ISSUE, bibNumber, actor));
    }

    @Override
    @Transactional
    public void reverse(List<GoodieIssue> issues, String bibNumber, String actor) {
        issues.forEach(issue -> postHandover(issue, MovementType.REVERSAL, bibNumber, actor));
    }

    // One unit per line. An issue may overdraw the shelf, since the runner is already holding the goody,
    // and a reversal lands back on the location the unit left, wherever the goody hands out from now and
    // however full that shelf is: the counter is never refused.
    private void postHandover(GoodieIssue issue, MovementType type, String bibNumber, String actor) {
        InventoryVariant variant = requireVariant(issue.variantId());
        InventoryItem item = itemRepository.findById(variant.getItemId())
                .orElseThrow(InventoryItemNotFoundException::new);
        InventoryStock stock = findOrCreateStock(variant.getId(), issue.locationId(), actor);
        Instant now = Instant.now();
        boolean out = type == MovementType.ISSUE;
        int balanceAfter = stock.getOnHand() + (out ? -1 : 1);
        if (out) {
            stockRepository.deduct(stock.getId(), 1, true, now, actor);
            // One unit at a time lands on -1 only on the hand-over that crossed below zero.
            if (balanceAfter == -1) {
                stockAlerts.shelfOverdrawn(item, issue, bibNumber);
            }
            stockAlerts.stockDropped(item.getId());
        } else {
            stockRepository.add(stock.getId(), 1, true, now, actor);
            stockAlerts.rearm(item.getId());
        }

        movementRepository.save(InventoryMovement.builder()
                .organizationId(item.getOrganizationId())
                .itemId(item.getId())
                .variantId(variant.getId())
                .type(type)
                .fromLocationId(out ? issue.locationId() : null)
                .toLocationId(out ? null : issue.locationId())
                .quantity(1)
                .balanceAfter(balanceAfter)
                .reference(bibNumber)
                .occurredAt(now)
                .build());
    }

    // ---- lookups ----------------------------------------------------------------

    private InventoryVariant requireVariant(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(InventoryVariantNotFoundException::new);
    }

    // ---- balance updates ----------------------------------------------------------------

    // Two counters can post the first unit of a variant at a location at the same instant, so the row is
    // created by a statement that cannot fail on the unique key, never by a read followed by an insert.
    private InventoryStock findOrCreateStock(Long variantId, Long locationId, String actor) {
        stockRepository.insertIfMissing(variantId, locationId, Instant.now(), actor);
        return stockRepository.lockByVariantIdAndLocationId(variantId, locationId);
    }

    // A balance response carries no organization, and the aspect would otherwise fall back to the
    // actor's own — which is none at all for a root user.
    private void auditAgainst(Long organizationId, Long entityId) {
        AuditContextHolder.setOrganizationId(organizationId);
        AuditContextHolder.setEntityId(String.valueOf(entityId));
    }
}
