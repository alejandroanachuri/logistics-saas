package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.TrackingEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code tracking_events} table
 * (V15). APPEND-ONLY — no UPDATE / soft-delete methods will ever
 * land here. Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V16.
 *
 * <p>The {@link #existsByEventHash(String)} derived query is the
 * idempotency gate in PR-3b: the event-registration service calls
 * it BEFORE inserting to short-circuit when the same hash has
 * already landed (the UNIQUE constraint would also catch the
 * duplicate, but catching it earlier turns a stack-trace into a
 * clean 409 {@code DUPLICATE_EVENT} response).
 *
 * <p>The {@link #findByPackageIdOrderByEventTimestampAsc(UUID)} derived
 * query backs the {@code TrackingEventService.list} timeline lookup.
 */
@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {

    boolean existsByEventHash(String eventHash);

    List<TrackingEvent> findByPackageIdOrderByEventTimestampAsc(UUID packageId);
}
