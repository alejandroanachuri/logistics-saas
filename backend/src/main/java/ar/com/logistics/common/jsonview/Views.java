package ar.com.logistics.common.jsonview;

/**
 * Jackson JSON view markers for field-level security, per PRD §10.1.
 *
 * <p>The {@link ar.com.logistics.shipment.domain.Customer} entity carries
 * {@code @JsonView(Views.Default.class)} on its {@code dni} and
 * {@code cuitCuil} fields. With this annotation in place, Jackson's view
 * filter behaves as follows:
 * <ul>
 *   <li>When the response is serialized with
 *       {@link org.springframework.http.converter.json.MappingJacksonValue#setSerializationView(Class)
 *       setSerializationView(Views.Default.class)}, the {@code dni} /
 *       {@code cuitCuil} fields ARE included (used for ADMIN / OPERATOR
 *       callers).</li>
 *   <li>When serialized with
 *       {@code setSerializationView(Views.Masked.class)}, those fields are
 *       EXCLUDED (used for DRIVER / VIEWER / public-portal callers).
 *       Jackson treats the two view classes as different views, so fields
 *       tagged with {@code Default} are dropped when the active view is
 *       {@code Masked}.</li>
 * </ul>
 *
 * <p>Per the design, masking is implemented as "field absent" rather than
 * "field present with {@code ****}" — the simpler implementation that meets
 * the PRD requirement of no field leakage. Future enhancement: server-side
 * masked string if a UI needs to render placeholder asterisks.
 */
public final class Views {

    private Views() {
        // static-only
    }

    /**
     * Fields tagged with this view are included when the active view is
     * {@code Default}, and excluded otherwise. Used for ADMIN and OPERATOR
     * callers who are entitled to see the full DNI / CUIT.
     */
    public static final class Default {}

    /**
     * Fields tagged with {@code Default} are excluded when the active view
     * is {@code Masked}. Used for DRIVER / VIEWER callers and the
     * unauthenticated public portal (defense in depth: even if a Customer
     * object somehow reaches a public endpoint, its sensitive fields are
     * stripped).
     */
    public static final class Masked {}
}
