package ar.com.logistics.auth.domain;

import ar.com.logistics.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Maps to {@code public.roles} (V2). Seeded by V7 (six rows). */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", length = 50, nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 20, nullable = false)
    private RoleScope scope;

    @Column(name = "description")
    private String description;

    public enum RoleScope {
        COMPANY,
        PLATFORM
    }
}
