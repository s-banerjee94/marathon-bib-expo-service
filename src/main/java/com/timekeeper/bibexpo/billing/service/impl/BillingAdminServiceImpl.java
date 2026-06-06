package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.exception.BillNotFoundException;
import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.billing.model.dto.response.BillResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillingSummaryResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.BillingTrend;
import com.timekeeper.bibexpo.billing.model.dto.response.OrganizationBillingResponse;
import com.timekeeper.bibexpo.billing.model.dto.response.ReasonAggregate;
import com.timekeeper.bibexpo.billing.model.dto.response.TopOrganization;
import com.timekeeper.bibexpo.billing.model.entity.Invoice;
import com.timekeeper.bibexpo.billing.model.entity.PaymentStatus;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.model.enums.DashboardRange;
import com.timekeeper.bibexpo.model.enums.TrendInterval;
import com.timekeeper.bibexpo.billing.repository.InvoiceRepository;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.service.StorageService;
import com.timekeeper.bibexpo.billing.service.BillingAdminService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingAdminServiceImpl implements BillingAdminService {

    // Single-currency assumption (decision: INR by default). If bills ever carry mixed
    // currencies, the flat totals here must become per-currency groupings.
    private static final String DEFAULT_CURRENCY = "INR";
    private static final int MAX_TREND_BUCKETS = 90;
    private static final int MAX_TOP_ORGS = 20;

    private final InvoiceRepository invoiceRepository;
    private final OrganizationRepository organizationRepository;
    private final StorageService storageService;

    @Override
    public OrganizationBillingResponse listOrganizationBills(Long organizationId, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        organizationRepository.findById(organizationId).orElseThrow(OrganizationNotFoundException::new);

        List<Invoice> invoices = invoiceRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
        BigDecimal totalBilled = invoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return OrganizationBillingResponse.builder()
                .organizationId(organizationId)
                .currency(invoices.isEmpty() ? DEFAULT_CURRENCY : invoices.get(0).getCurrency())
                .totalBilled(totalBilled)
                .bills(toResponses(invoices))
                .build();
    }

    @Override
    public Page<BillResponse> listAllBills(Long organizationId, Long eventId, Instant from, Instant to,
                                           String reason, PaymentStatus paymentStatus, String q, Pageable pageable) {
        Specification<Invoice> spec = buildSpec(organizationId, eventId, from, to, reason, paymentStatus, q);
        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return invoiceRepository.findAll(spec, effective)
                .map(invoice -> BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey())));
    }

    @Override
    public BillingSummaryResponse getSummary(DashboardRange range, TrendInterval trendInterval,
                                             int trendBuckets, int topOrgs) {
        int buckets = clamp(trendBuckets, 1, MAX_TREND_BUCKETS);
        int orgs = clamp(topOrgs, 1, MAX_TOP_ORGS);

        List<Invoice> all = invoiceRepository.findAllByOrderByCreatedAtAsc();
        Instant rangeStart = rangeStart(range);
        List<Invoice> inRange = all.stream()
                .filter(i -> rangeStart == null || !i.getCreatedAt().isBefore(rangeStart))
                .toList();

        BigDecimal totalBilled = sum(inRange);
        long billsCount = inRange.size();
        BigDecimal averageBill = billsCount == 0
                ? BigDecimal.ZERO
                : totalBilled.divide(BigDecimal.valueOf(billsCount), 2, RoundingMode.HALF_UP);

        return BillingSummaryResponse.builder()
                .currency(currencyOf(inRange, all))
                .refreshedAt(Instant.now())
                .totalBilled(totalBilled)
                .billsCount(billsCount)
                .averageBill(averageBill)
                .byReason(byReason(inRange))
                .trend(buildTrend(all, trendInterval, buckets))
                .topOrganizations(topOrganizations(inRange, orgs))
                .build();
    }

    @Override
    @Transactional
    public BillResponse updatePaymentStatus(String billId, PaymentStatus paymentStatus) {
        Invoice invoice = invoiceRepository.findByBillId(billId)
                .orElseThrow(() -> new BillNotFoundException("The bill you requested does not exist."));
        invoice.setPaymentStatus(paymentStatus);
        Invoice saved = invoiceRepository.save(invoice);
        log.info("Bill {} marked {}", billId, paymentStatus);
        return BillResponse.fromEntity(saved, storageService.createDownloadUrl(saved.getPdfKey()));
    }

    // ---- helpers ----

    private void authorizeOrgAccess(User currentUser, Long organizationId) {
        UserRole role = currentUser.getRole();
        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }
        if (currentUser.getOrganization() == null
                || !currentUser.getOrganization().getId().equals(organizationId)) {
            throw new UnauthorizedAccessException("You do not have access to this organization's billing.");
        }
    }

    private Specification<Invoice> buildSpec(Long organizationId, Long eventId, Instant from, Instant to,
                                             String reason, PaymentStatus paymentStatus, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (organizationId != null) {
                predicates.add(cb.equal(root.get("organizationId"), organizationId));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (reason != null && !reason.isBlank()) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("organizerName")), like),
                        cb.like(cb.lower(root.get("eventName")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Instant rangeStart(DashboardRange range) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return switch (range) {
            case ALL -> null;
            case YEAR -> now.minusYears(1).toInstant();
            case MONTH -> now.minusMonths(1).toInstant();
        };
    }

    private Map<String, ReasonAggregate> byReason(List<Invoice> invoices) {
        Map<String, ReasonAggregate> result = new LinkedHashMap<>();
        result.put("AUTO", aggregate(invoices, "AUTO"));
        result.put("MANUAL", aggregate(invoices, "MANUAL"));
        return result;
    }

    private ReasonAggregate aggregate(List<Invoice> invoices, String reason) {
        List<Invoice> matching = invoices.stream()
                .filter(i -> reason.equalsIgnoreCase(i.getReason()))
                .toList();
        return ReasonAggregate.builder()
                .amount(sum(matching))
                .count(matching.size())
                .build();
    }

    private BillingTrend buildTrend(List<Invoice> all, TrendInterval interval, int buckets) {
        ZonedDateTime currentStart = bucketStart(ZonedDateTime.now(ZoneOffset.UTC), interval);

        List<String> labels = new ArrayList<>(buckets);
        List<BigDecimal> billed = new ArrayList<>(buckets);
        List<Long> counts = new ArrayList<>(buckets);
        Map<ZonedDateTime, Integer> indexByStart = new LinkedHashMap<>();
        for (int i = buckets - 1; i >= 0; i--) {
            ZonedDateTime start = minusBuckets(currentStart, interval, i);
            indexByStart.put(start, labels.size());
            labels.add(label(start, interval));
            billed.add(BigDecimal.ZERO);
            counts.add(0L);
        }

        for (Invoice invoice : all) {
            ZonedDateTime start = bucketStart(invoice.getCreatedAt().atZone(ZoneOffset.UTC), interval);
            Integer idx = indexByStart.get(start);
            if (idx != null) {
                billed.set(idx, billed.get(idx).add(invoice.getTotalAmount()));
                counts.set(idx, counts.get(idx) + 1);
            }
        }

        return BillingTrend.builder()
                .interval(interval)
                .bucketLabels(labels)
                .billed(billed)
                .count(counts)
                .build();
    }

    private List<TopOrganization> topOrganizations(List<Invoice> invoices, int limit) {
        Map<Long, TopOrganization> byOrg = new LinkedHashMap<>();
        for (Invoice invoice : invoices) {
            TopOrganization acc = byOrg.computeIfAbsent(invoice.getOrganizationId(), id -> TopOrganization.builder()
                    .organizationId(id)
                    .organizerName(invoice.getOrganizerName())
                    .totalBilled(BigDecimal.ZERO)
                    .billsCount(0)
                    .build());
            acc.setOrganizerName(invoice.getOrganizerName());
            acc.setTotalBilled(acc.getTotalBilled().add(invoice.getTotalAmount()));
            acc.setBillsCount(acc.getBillsCount() + 1);
        }
        return byOrg.values().stream()
                .sorted(Comparator.comparing(TopOrganization::getTotalBilled).reversed())
                .limit(limit)
                .toList();
    }

    private ZonedDateTime bucketStart(ZonedDateTime when, TrendInterval interval) {
        ZonedDateTime day = when.truncatedTo(ChronoUnit.DAYS);
        return switch (interval) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
    }

    private ZonedDateTime minusBuckets(ZonedDateTime start, TrendInterval interval, int n) {
        return switch (interval) {
            case DAY -> start.minusDays(n);
            case WEEK -> start.minusWeeks(n);
            case MONTH -> start.minusMonths(n);
        };
    }

    private String label(ZonedDateTime start, TrendInterval interval) {
        return switch (interval) {
            case DAY -> String.format("%04d-%02d-%02d", start.getYear(), start.getMonthValue(), start.getDayOfMonth());
            case WEEK -> String.format("%04d-W%02d",
                    start.get(IsoFields.WEEK_BASED_YEAR), start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> String.format("%04d-%02d", start.getYear(), start.getMonthValue());
        };
    }

    private BigDecimal sum(List<Invoice> invoices) {
        return invoices.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String currencyOf(List<Invoice> inRange, List<Invoice> all) {
        if (!inRange.isEmpty()) {
            return inRange.get(0).getCurrency();
        }
        return all.isEmpty() ? DEFAULT_CURRENCY : all.get(0).getCurrency();
    }

    private List<BillResponse> toResponses(List<Invoice> invoices) {
        return invoices.stream()
                .map(invoice -> BillResponse.fromEntity(invoice, storageService.createDownloadUrl(invoice.getPdfKey())))
                .toList();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
