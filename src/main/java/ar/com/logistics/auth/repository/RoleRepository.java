package ar.com.logistics.auth.repository;

import ar.com.logistics.auth.domain.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Role-catalog repository. The catalog is read-only at runtime
 * (seeded by V7); the registration service looks up the
 * {@code COMPANY_ADMIN} role to assign it to the first admin
 * user.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndScope(String name, Role.RoleScope scope);
}
