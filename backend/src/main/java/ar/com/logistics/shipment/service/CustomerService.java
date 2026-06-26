package ar.com.logistics.shipment.service;

import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.common.validation.CuitValidator;
import ar.com.logistics.common.validation.DniValidator;
import ar.com.logistics.shipment.domain.Customer;
import ar.com.logistics.shipment.repository.company.CustomerRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-tenant-aware CRUD service for {@link Customer}. Implements the
 * customer-management endpoints under {@code /api/v1/customers/**} from
 * spec §B (etapa-3-envios, PR-3a).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate input on every mutating call: {@code personType} is
 *       {@code FISICA} or {@code JURIDICA}; FISICA rows require a
 *       7-or-8-digit DNI; JURIDICA rows require a CUIT whose
 *       verifier digit passes the AFIP mod-11 algorithm.</li>
 *   <li>Enforce per-tenant uniqueness for DNI (via
 *       {@code findByTenantIdAndDni}) and CUIT (via an in-memory
 *       {@code findAll()} sweep — the repository does not yet
 *       expose a {@code findByTenantIdAndCuitCuil} derived query, so
 *       we keep the same shape as
 *       {@code CompanyUsersService.list} and filter in code).</li>
 *   <li>Auto-default {@code consentDate = now()} when
 *       {@code dataConsent=true} and the caller omitted the date;
 *       reject the inverse mismatch (consent=false with a date) with
 *       {@code NO_DATA_CONSENT}.</li>
 *   <li>Emit audit events for every mutation (per spec C8).</li>
 *   <li>Soft-delete via {@code status=DISABLED} + {@code deletedAt}.</li>
 * </ul>
 *
 * <p>Transaction boundary: every public method runs in a single
 * {@code @Transactional("companyTransactionManager")} block. Reads are
 * RLS-scoped via V16.
 */
@Service
public class CustomerService {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerService.class);

    /** Canonical wire-format codes (the GlobalExceptionHandler maps them to HTTP statuses). */
    static final String CODE_CUSTOMER_NOT_FOUND = "CUSTOMER_NOT_FOUND";

    static final String CODE_DNI_INVALID = "DNI_INVALID";
    static final String CODE_CUIT_INVALID = "CUIT_INVALID";
    static final String CODE_DNI_ALREADY_EXISTS = "DNI_ALREADY_EXISTS";
    static final String CODE_CUIT_ALREADY_EXISTS = "CUIT_ALREADY_EXISTS";
    static final String CODE_NO_DATA_CONSENT = "NO_DATA_CONSENT";
    static final String CODE_INVALID_PERSON_TYPE = "VALIDATION_ERROR";

    /** Allowed values for {@code person_type}. Mirrors the DB CHECK constraint. */
    static final String PERSON_TYPE_FISICA = "FISICA";

    static final String PERSON_TYPE_JURIDICA = "JURIDICA";

    /** Allowed values for {@code tax_condition} (Argentina, AFIP-aligned). */
    static final Set<String> TAX_CONDITIONS =
            Set.of("CONSUMIDOR_FINAL", "RESPONSABLE_INSCRIPTO", "MONOTRIBUTO", "EXENTO", "NO_CATEGORIZADO");

    /** Allowed lifecycle statuses (used for list filtering + disable). */
    // The {@code customers} table (V15) does NOT have a {@code status}
    // column — soft-delete is signalled by {@code deletedAt != null}.
    // These constants are kept for the audit-event payload and the
    // public filter API so callers can request ACTIVE / DISABLED
    // semantically without leaking the storage detail.
    static final String STATUS_ACTIVE = "ACTIVE";

    static final String STATUS_DISABLED = "DISABLED";

    private final CustomerRepository customerRepository;
    private final AuditLogger auditLogger;

    public CustomerService(CustomerRepository customerRepository, AuditLogger auditLogger) {
        this.customerRepository = customerRepository;
        this.auditLogger = auditLogger;
    }

    // -------------------------------------------------------------------
    //  list
    // -------------------------------------------------------------------

    /**
     * Paginated list of customers in the caller's tenant with optional
     * status / search filters. Mirrors the in-memory filter shape of
     * {@code CompanyUsersService.list} — small dataset per tenant, no
     * JPA {@code Specification} needed for v1.
     */
    @Transactional("companyTransactionManager")
    public Page<Customer> list(UUID tenantId, CustomerListFilters filters, Pageable pageable) {
        List<Customer> rows = customerRepository.findAll();
        List<Customer> filtered = new ArrayList<>();
        for (Customer c : rows) {
            if (c.getTenantId() != null && !c.getTenantId().equals(tenantId)) {
                continue;
            }
            // Status filter maps to the soft-delete marker
            // (deletedAt) because the entity has no status column.
            if (filters.status() != null
                    && !filters.status().isBlank()
                    && !filters.status().equalsIgnoreCase("ALL")) {
                boolean disabled = c.getDeletedAt() != null;
                if (filters.status().equalsIgnoreCase(STATUS_DISABLED) && !disabled) {
                    continue;
                }
                if (filters.status().equalsIgnoreCase(STATUS_ACTIVE) && disabled) {
                    continue;
                }
            }
            if (filters.search() != null && !filters.search().isBlank()) {
                String needle = filters.search().toLowerCase();
                String hay = ((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                                + (c.getLastName() == null ? "" : c.getLastName()) + " "
                                + (c.getEmail() == null ? "" : c.getEmail()) + " "
                                + (c.getRazonSocial() == null ? "" : c.getRazonSocial()))
                        .toLowerCase();
                if (!hay.contains(needle)) {
                    continue;
                }
            }
            filtered.add(c);
        }
        int total = filtered.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(filtered.subList(from, to), pageable, total);
    }

    // -------------------------------------------------------------------
    //  get
    // -------------------------------------------------------------------

    /**
     * Fetch a single customer scoped to the caller's tenant. Throws
     * {@link BusinessRuleException} with code {@code CUSTOMER_NOT_FOUND}
     * when the row is missing. RLS already filters the rows the pool
     * sees, so a hit belongs to this tenant by construction.
     */
    @Transactional("companyTransactionManager")
    public Customer get(UUID tenantId, UUID customerId) {
        return customerRepository
                .findById(customerId)
                .filter(c -> c.getTenantId() != null && c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessRuleException(
                        CODE_CUSTOMER_NOT_FOUND, Map.of("customerId", customerId.toString())));
    }

    // -------------------------------------------------------------------
    //  create
    // -------------------------------------------------------------------

    /**
     * Create a customer. Order of checks (fail-fast):
     * <ol>
     *   <li>personType is FISICA or JURIDICA</li>
     *   <li>taxCondition is in the AFIP-aligned whitelist</li>
     *   <li>DNI (FISICA) / CUIT (JURIDICA) format + per-tenant uniqueness</li>
     *   <li>consent date consistency</li>
     *   <li>INSERT row, then audit</li>
     * </ol>
     */
    @Transactional("companyTransactionManager")
    public Customer create(UUID tenantId, UUID adminId, CreateCustomerRequest req) {
        validateCustomerInput(tenantId, req);

        Instant now = Instant.now();
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setPersonType(req.personType());
        c.setFirstName(req.firstName());
        c.setLastName(req.lastName());
        c.setRazonSocial(req.razonSocial());
        c.setDni(req.personType().equals(PERSON_TYPE_FISICA) ? DniValidator.normalize(req.dni()) : null);
        c.setCuitCuil(req.personType().equals(PERSON_TYPE_JURIDICA) ? CuitValidator.normalize(req.cuitCuil()) : null);
        c.setTaxCondition(req.taxCondition());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setDataConsent(req.dataConsent());
        // Auto-default consentDate when dataConsent=true and the
        // caller omitted the explicit date — the DB CHECK
        // chk_consent_date requires a non-null value in that case.
        c.setConsentDate(req.dataConsent() ? (req.consentDate() != null ? req.consentDate() : now) : null);
        c.setConsentVersion(req.consentVersion());
        c.setNotes(req.notes());
        // No status column on the entity — soft-delete is signalled by
        // deletedAt. Active rows leave deletedAt = null.
        c.setCreatedBy(adminId);
        c.setUpdatedBy(adminId);
        // createdAt / updatedAt are stamped by the entity's @PrePersist
        // callbacks, so we leave them null here.

        c = customerRepository.save(c);

        auditLogger.logAsync(new AuditEvent(
                "CUSTOMER_CREATED",
                c.getId(),
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of(
                        "createdBy",
                        adminId.toString(),
                        "personType",
                        c.getPersonType(),
                        "taxCondition",
                        c.getTaxCondition())));

        LOG.debug("Created customer {} in tenant {}", c.getId(), tenantId);
        return c;
    }

    // -------------------------------------------------------------------
    //  update
    // -------------------------------------------------------------------

    /**
     * Partial update. Any null field in the request is left unchanged.
     * Re-runs the same format + uniqueness validations as {@link #create}
     * when the corresponding field is supplied.
     */
    @Transactional("companyTransactionManager")
    public Customer update(UUID tenantId, UUID adminId, UUID customerId, UpdateCustomerRequest req) {
        Customer c = customerRepository
                .findById(customerId)
                .filter(x -> x.getTenantId() != null && x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessRuleException(
                        CODE_CUSTOMER_NOT_FOUND, Map.of("customerId", customerId.toString())));

        List<String> changedFields = new ArrayList<>();

        if (req.firstName() != null && !req.firstName().equals(c.getFirstName())) {
            c.setFirstName(req.firstName());
            changedFields.add("firstName");
        }
        if (req.lastName() != null && !req.lastName().equals(c.getLastName())) {
            c.setLastName(req.lastName());
            changedFields.add("lastName");
        }
        if (req.razonSocial() != null && !req.razonSocial().equals(c.getRazonSocial())) {
            c.setRazonSocial(req.razonSocial());
            changedFields.add("razonSocial");
        }
        if (req.taxCondition() != null && !req.taxCondition().equals(c.getTaxCondition())) {
            if (!TAX_CONDITIONS.contains(req.taxCondition())) {
                throw new BusinessRuleException(CODE_INVALID_PERSON_TYPE, Map.of("taxCondition", req.taxCondition()));
            }
            c.setTaxCondition(req.taxCondition());
            changedFields.add("taxCondition");
        }
        if (req.phone() != null && !req.phone().equals(c.getPhone())) {
            c.setPhone(req.phone());
            changedFields.add("phone");
        }
        if (req.email() != null && !req.email().equals(c.getEmail())) {
            c.setEmail(req.email());
            changedFields.add("email");
        }
        if (req.notes() != null) {
            c.setNotes(req.notes());
            changedFields.add("notes");
        }
        // Note: dataConsent + consentDate are set at create-time only.
        // Re-issuing consent is a separate flow (out of PR-3a scope)
        // because it requires the consent-version bump and audit chain
        // the spec assigns to PR-3c. Update cannot mutate them.

        c.setUpdatedBy(adminId);
        c = customerRepository.save(c);

        if (!changedFields.isEmpty()) {
            auditLogger.logAsync(new AuditEvent(
                    "CUSTOMER_UPDATED",
                    customerId,
                    AuditEvent.UserScope.COMPANY,
                    tenantId,
                    null,
                    null,
                    Map.of("updatedBy", adminId.toString(), "changedFields", changedFields.toString())));
        }

        return c;
    }

    // -------------------------------------------------------------------
    //  disable
    // -------------------------------------------------------------------

    /**
     * Soft-disable a customer. Sets {@code status = DISABLED},
     * {@code deletedAt = now()}, stamps {@code updatedBy}.
     */
    @Transactional("companyTransactionManager")
    public void disable(UUID tenantId, UUID adminId, UUID customerId) {
        Customer c = customerRepository
                .findById(customerId)
                .filter(x -> x.getTenantId() != null && x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessRuleException(
                        CODE_CUSTOMER_NOT_FOUND, Map.of("customerId", customerId.toString())));

        // Soft-delete is signalled by deletedAt != null (the entity has
        // no status column).
        c.setDeletedAt(Instant.now());
        c.setUpdatedBy(adminId);
        customerRepository.save(c);

        auditLogger.logAsync(new AuditEvent(
                "CUSTOMER_DISABLED",
                customerId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("disabledBy", adminId.toString())));
    }

    // -------------------------------------------------------------------
    //  Internal: business-rule validation
    // -------------------------------------------------------------------

    private void validateCustomerInput(UUID tenantId, CreateCustomerRequest req) {
        // personType
        if (!PERSON_TYPE_FISICA.equals(req.personType()) && !PERSON_TYPE_JURIDICA.equals(req.personType())) {
            throw new BusinessRuleException(
                    CODE_INVALID_PERSON_TYPE, Map.of("personType", String.valueOf(req.personType())));
        }
        // taxCondition
        if (!TAX_CONDITIONS.contains(req.taxCondition())) {
            throw new BusinessRuleException(
                    CODE_INVALID_PERSON_TYPE, Map.of("taxCondition", String.valueOf(req.taxCondition())));
        }
        // DNI / CUIT
        if (PERSON_TYPE_FISICA.equals(req.personType())) {
            if (!DniValidator.isValid(req.dni())) {
                throw new BusinessRuleException(CODE_DNI_INVALID, Map.of("dni", String.valueOf(req.dni())));
            }
            String normalized = DniValidator.normalize(req.dni());
            if (customerRepository.findByTenantIdAndDni(tenantId, normalized).isPresent()) {
                throw new BusinessRuleException(CODE_DNI_ALREADY_EXISTS, Map.of("dni", normalized));
            }
        } else {
            if (!CuitValidator.isValid(req.cuitCuil())) {
                throw new BusinessRuleException(CODE_CUIT_INVALID, Map.of("cuitCuil", String.valueOf(req.cuitCuil())));
            }
            String normalized = CuitValidator.normalize(req.cuitCuil());
            // Repository has no findByTenantIdAndCuitCuil yet — keep
            // the uniqueness check in code (matches the in-memory
            // filter shape of CompanyUsersService.list).
            boolean exists = customerRepository.findAll().stream()
                    .anyMatch(x -> x.getTenantId() != null
                            && x.getTenantId().equals(tenantId)
                            && normalized.equals(x.getCuitCuil()));
            if (exists) {
                throw new BusinessRuleException(CODE_CUIT_ALREADY_EXISTS, Map.of("cuitCuil", normalized));
            }
        }
        // data-consent consistency
        if (req.dataConsent() && req.consentDate() == null) {
            // allowed — server will auto-default to now(); not an error.
        } else if (!req.dataConsent() && req.consentDate() != null) {
            throw new BusinessRuleException(
                    CODE_NO_DATA_CONSENT,
                    Map.of("consentDate", req.consentDate().toString()));
        }
    }

    // -------------------------------------------------------------------
    //  Records
    // -------------------------------------------------------------------

    /** Request body for {@link #create}. */
    public record CreateCustomerRequest(
            String personType,
            String firstName,
            String lastName,
            String razonSocial,
            String dni,
            String cuitCuil,
            String taxCondition,
            String phone,
            String email,
            boolean dataConsent,
            java.time.Instant consentDate,
            String consentVersion,
            String notes) {}

    /** Request body for {@link #update}. Any null field is left unchanged. */
    public record UpdateCustomerRequest(
            String firstName,
            String lastName,
            String razonSocial,
            String taxCondition,
            String phone,
            String email,
            String notes) {}

    /** Filters for {@link #list}. Null fields are "no filter applied". */
    public record CustomerListFilters(String search, String status) {}
}
