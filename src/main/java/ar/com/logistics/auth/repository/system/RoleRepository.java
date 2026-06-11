package ar.com.logistics.auth.repository.system;

import ar.com.logistics.auth.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side role repository. The roles catalog (V7) is
 * read-only at runtime and the registration service needs to
 * resolve {@code COMPANY_ADMIN} on the system pool (BYPASSRLS).
 *
 * <p>The company side also reads roles (for the JWT claims) but
 * the role lookup is on the system EMF because the
 * registration write path needs BYPASSRLS, and a single shared
 * interface here keeps the per-DataSource basePackages disjoint
 * from {@code auth.repository.company}.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndScope(String name, Role.RoleScope scope);
}
