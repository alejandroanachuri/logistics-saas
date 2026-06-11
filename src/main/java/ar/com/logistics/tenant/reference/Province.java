package ar.com.logistics.tenant.reference;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The 24 Argentine provinces (plus CABA) that the registration
 * wizard and other forms can choose from. v1 source of truth lives
 * in this enum (the spec file
 * {@code openspec/changes/etapa-1-registro/specs/reference-data.md}
 * requires the controller to read from the
 * {@code public.provinces} table seeded by V10; the enum exists as
 * a compile-time companion that Bean-Validation can enforce before
 * the request reaches the database, and is what the request DTO
 * uses for type safety).
 *
 * <p>The spec-patch decision (recorded in the change proposal) is
 * ASCII-only display names in v1; the diacritic versions (e.g.
 * {@code Córdoba} instead of {@code Cordoba}) are explicitly out of
 * scope for v1 and deferred to a follow-up that pairs the change
 * with an i18n pass on the frontend.
 */
public enum Province {
    BUENOS_AIRES("Buenos Aires"),
    CABA("Ciudad Autonoma de Buenos Aires"),
    CATAMARCA("Catamarca"),
    CHACO("Chaco"),
    CHUBUT("Chubut"),
    CORDOBA("Cordoba"),
    CORRIENTES("Corrientes"),
    ENTRE_RIOS("Entre Rios"),
    FORMOSA("Formosa"),
    JUJUY("Jujuy"),
    LA_PAMPA("La Pampa"),
    LA_RIOJA("La Rioja"),
    MENDOZA("Mendoza"),
    MISIONES("Misiones"),
    NEUQUEN("Neuquen"),
    RIO_NEGRO("Rio Negro"),
    SALTA("Salta"),
    SAN_JUAN("San Juan"),
    SAN_LUIS("San Luis"),
    SANTA_CRUZ("Santa Cruz"),
    SANTA_FE("Santa Fe"),
    SANTIAGO_DEL_ESTERO("Santiago del Estero"),
    TIERRA_DEL_FUEGO("Tierra del Fuego"),
    TUCUMAN("Tucuman");

    private final String displayName;

    Province(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Human-readable Spanish (ASCII-only in v1) label rendered in
     * the UI. Stable across releases — clients can safely cache this
     * alongside the code.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Canonical enum name used as the {@code code} value in the
     * JSON DTO. Stable across releases.
     */
    public String code() {
        return name();
    }

    /**
     * Reverse lookup from a wire-format code. Returns
     * {@link Optional#empty()} when the code is unknown, so the
     * controller can map it to a Bean-Validation failure rather
     * than crashing.
     */
    public static Optional<Province> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (Province p : values()) {
            if (p.name().equals(code)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * The 24 entries in alphabetical order by code. The
     * reference-data controller uses this to build the response
     * (or it can hit the DB; both paths agree on the order).
     */
    public static List<Province> all() {
        return Arrays.stream(values())
                .sorted(Comparator.comparing(Province::name))
                .toList();
    }
}
