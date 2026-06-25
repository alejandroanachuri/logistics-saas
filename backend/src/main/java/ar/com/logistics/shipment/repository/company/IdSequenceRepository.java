package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.IdSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code id_sequences} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. RLS-scoped via
 * V16.
 *
 * <p>The composite PK {@code (tenant_id, sequence_key, year)} is
 * carried by the {@link IdSequence.IdSequenceId} class. PR-3a
 * adds a native {@code UPSERT ... RETURNING current_value} for the
 * increment-and-read flow — LGST does NOT go through this
 * sequence (it is random), but internal branch codes and order
 * numbers will.
 */
@Repository
public interface IdSequenceRepository extends JpaRepository<IdSequence, IdSequence.IdSequenceId> {}
