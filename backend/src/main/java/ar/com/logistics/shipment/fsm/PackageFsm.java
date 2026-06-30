package ar.com.logistics.shipment.fsm;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable finite-state machine for {@code Package} lifecycle.
 *
 * <p>The FSM is the source of truth for valid package state transitions.
 * A transition request is allowed only if the {@code (from, to)} pair
 * is explicitly listed in {@link #VALID_TRANSITIONS}; any other pair
 * is rejected with {@link ar.com.logistics.common.exception.BusinessRuleException}
 * carrying {@code INVALID_STATE_TRANSITION}. The catalog is enforced
 * in code (not in the DB) per PRD §7.1 — the FSM is the contract
 * surface for the tracking-event engine.
 *
 * <p>States are the 19 PRD-defined package states plus the special
 * "reversible" {@code RETENIDO} state. {@link #FINAL_STATES} are
 * terminal — no transitions out of them are ever valid.
 *
 * <p><b>PRD §7.3 / §7.4 contradiction note:</b> PRD §7.3 line 550 lists
 * {@code RETENIDO -> CANCELADO} as a valid transition, but §7.4
 * describes {@code RETENIDO} as "reversible" (stores
 * {@code previous_status}). A reversible state should not have a final
 * state ({@code CANCELADO}) as an exit. We follow §7.3 (PRD is
 * authoritative for this stage) and document the contradiction here
 * so the next person to read this code knows it was a deliberate
 * decision, not an oversight. Etapa 4 will revisit if needed.
 *
 * <p>The visibility of the FSM is package-scoped (default): only the
 * tracking-event service in this package should call
 * {@link #isValidTransition}. Other layers either call the service
 * (preferred) or import the public methods explicitly.
 */
public final class PackageFsm {

    private PackageFsm() {
        // static utility
    }

    // ------------------------------------------------------------------------
    // State constants (string-typed to match the DB column).
    // ------------------------------------------------------------------------

    public static final String PRE_ALTA = "PRE_ALTA";
    public static final String ESPERANDO_APROBACION_RUTA = "ESPERANDO_APROBACION_RUTA";
    public static final String CREADO = "CREADO";
    public static final String RECIBIDO_EN_SUCURSAL_ORIGEN = "RECIBIDO_EN_SUCURSAL_ORIGEN";
    public static final String RETENIDO_DOCUMENTACION = "RETENIDO_DOCUMENTACION";
    public static final String CLASIFICADO = "CLASIFICADO";
    public static final String EN_TRANSITO_A_HUB = "EN_TRANSITO_A_HUB";
    public static final String EN_HUB = "EN_HUB";
    public static final String EN_TRANSITO_CON_ALIADO = "EN_TRANSITO_CON_ALIADO";
    public static final String EN_TRANSITO_A_DESTINO = "EN_TRANSITO_A_DESTINO";
    public static final String RECIBIDO_EN_SUCURSAL_DESTINO = "RECIBIDO_EN_SUCURSAL_DESTINO";
    public static final String EN_REPARTO = "EN_REPARTO";
    public static final String ENTREGA_FALLIDA = "ENTREGA_FALLIDA";
    public static final String INCIDENTE_ACTIVO = "INCIDENTE_ACTIVO";
    public static final String DEVOLUCION_INICIADA = "DEVOLUCION_INICIADA";
    public static final String RETENIDO = "RETENIDO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String DEVUELTO = "DEVUELTO";
    public static final String CANCELADO = "CANCELADO";

    /** Terminal states — no outgoing transitions are ever valid. */
    public static final Set<String> FINAL_STATES = Set.of(ENTREGADO, DEVUELTO, CANCELADO);

    // ------------------------------------------------------------------------
    // Valid transitions (PRD §7.3, verbatim).
    // ------------------------------------------------------------------------

    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.ofEntries(
            entry(PRE_ALTA, List.of(ESPERANDO_APROBACION_RUTA, CREADO, CANCELADO)),
            entry(ESPERANDO_APROBACION_RUTA, List.of(CREADO, CANCELADO)),
            entry(CREADO, List.of(RECIBIDO_EN_SUCURSAL_ORIGEN, CANCELADO)),
            entry(RECIBIDO_EN_SUCURSAL_ORIGEN, List.of(CLASIFICADO, RETENIDO_DOCUMENTACION, CANCELADO, RETENIDO)),
            entry(RETENIDO_DOCUMENTACION, List.of(CLASIFICADO, CANCELADO)),
            entry(CLASIFICADO, List.of(EN_TRANSITO_A_HUB, EN_TRANSITO_A_DESTINO, EN_TRANSITO_CON_ALIADO, RETENIDO)),
            entry(EN_TRANSITO_A_HUB, List.of(EN_HUB, RETENIDO)),
            entry(EN_HUB, List.of(EN_TRANSITO_A_DESTINO, EN_TRANSITO_CON_ALIADO, RETENIDO)),
            entry(EN_TRANSITO_CON_ALIADO, List.of(EN_TRANSITO_A_DESTINO, RECIBIDO_EN_SUCURSAL_DESTINO, RETENIDO)),
            entry(EN_TRANSITO_A_DESTINO, List.of(RECIBIDO_EN_SUCURSAL_DESTINO, RETENIDO)),
            entry(RECIBIDO_EN_SUCURSAL_DESTINO, List.of(EN_REPARTO, RETENIDO, DEVOLUCION_INICIADA)),
            entry(EN_REPARTO, List.of(ENTREGADO, ENTREGA_FALLIDA, INCIDENTE_ACTIVO)),
            entry(ENTREGA_FALLIDA, List.of(EN_REPARTO, DEVOLUCION_INICIADA, RETENIDO)),
            entry(INCIDENTE_ACTIVO, List.of(DEVOLUCION_INICIADA, RETENIDO, EN_REPARTO)),
            entry(DEVOLUCION_INICIADA, List.of(DEVUELTO)),
            entry(
                    RETENIDO,
                    List.of(
                            RECIBIDO_EN_SUCURSAL_ORIGEN,
                            CLASIFICADO,
                            EN_TRANSITO_A_HUB,
                            EN_HUB,
                            EN_TRANSITO_CON_ALIADO,
                            EN_TRANSITO_A_DESTINO,
                            RECIBIDO_EN_SUCURSAL_DESTINO,
                            EN_REPARTO,
                            DEVOLUCION_INICIADA,
                            CANCELADO))
            // Final states (ENTREGADO, DEVUELTO, CANCELADO) intentionally have no entry here.
            );

    // ------------------------------------------------------------------------
    // Public API.
    // ------------------------------------------------------------------------

    /**
     * True iff {@code (from, to)} is a valid transition per
     * {@link #VALID_TRANSITIONS}. Always returns {@code false} when
     * {@code from} is a terminal state (no outgoing transitions), even
     * if the pair happens to be in the map (defense in depth — the map
     * should never contain terminal-state keys).
     */
    public static boolean isValidTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        if (FINAL_STATES.contains(from)) {
            return false;
        }
        List<String> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Valid next states from {@code from}. Returns an empty list when
     * {@code from} is terminal or unknown.
     */
    public static List<String> validNextStates(String from) {
        if (from == null || FINAL_STATES.contains(from)) {
            return List.of();
        }
        return VALID_TRANSITIONS.getOrDefault(from, List.of());
    }

    /**
     * True iff {@code state} is terminal ({@link #FINAL_STATES}).
     */
    public static boolean isFinal(String state) {
        return state != null && FINAL_STATES.contains(state);
    }
}
