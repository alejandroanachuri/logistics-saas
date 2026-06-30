package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.ShipmentCustody;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code shipment_custody} table
 * (V15). Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. RLS resolves
 * the tenant via a subquery against {@code packages} (V16) — this
 * table does not have its own {@code tenant_id} column.
 *
 * <p>PK type is the {@link ShipmentCustody.ShipmentCustodyId}
 * composite key carried by the {@code @IdClass} annotation on the
 * entity. The {@code @IdClass} approach (vs. {@code @EmbeddedId})
 * keeps the JPQL in PR-3b's handoff service readable — the
 * composite key fields surface directly in the WHERE clause.
 */
@Repository
public interface ShipmentCustodyRepository extends JpaRepository<ShipmentCustody, ShipmentCustody.ShipmentCustodyId> {}
