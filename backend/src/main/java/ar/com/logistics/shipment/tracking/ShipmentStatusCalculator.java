package ar.com.logistics.shipment.tracking;

import static ar.com.logistics.shipment.fsm.PackageFsm.CANCELADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.DEVUELTO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ENTREGADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ENTREGA_FALLIDA;

import ar.com.logistics.shipment.fsm.PackageFsm;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregate the shipment-level {@code status} from its packages.
 *
 * <p>Per PRD §7.6, the {@code Shipment} has NO FSM of its own — its
 * status is DERIVED from the statuses of its packages. The aggregation
 * rules, in priority order:
 *
 * <ol>
 *   <li>All packages {@code ENTREGADO} → shipment {@code ENTREGADO}</li>
 *   <li>At least one {@code ENTREGADO} AND the rest are terminal-but-different
 *       ({@code ENTREGA_FALLIDA}, {@code DEVUELTO}) → shipment
 *       {@code ENTREGADO_PARCIAL}</li>
 *   <li>All packages {@code DEVUELTO} → shipment {@code DEVUELTO}</li>
 *   <li>All packages {@code CANCELADO} → shipment {@code CANCELADO}</li>
 *   <li>Otherwise → the most-atrasado package state (highest severity
 *       in the flow)</li>
 * </ol>
 *
 * <p>The "most-atrasado" fallback is computed by a hand-tuned
 * {@link #SEVERITY} ranking: states further along in the
 * forward-flow have higher severity; cancel-like states sit at the
 * top. The exact ranking is documented inline.
 */
public final class ShipmentStatusCalculator {

    private ShipmentStatusCalculator() {
        // static utility
    }

    /**
     * Severity ranking for the "most-atrasado package" fallback.
     * Higher number = more atrasado = more representative of the
     * shipment's overall state when packages are at different stages.
     * Tuned to match the operator's intuition from the original
     * Transify v2.7 design notes.
     */
    private static final java.util.Map<String, Integer> SEVERITY = java.util.Map.ofEntries(
            java.util.Map.entry(PackageFsm.PRE_ALTA, 1),
            java.util.Map.entry(PackageFsm.ESPERANDO_APROBACION_RUTA, 2),
            java.util.Map.entry(PackageFsm.CREADO, 3),
            java.util.Map.entry(PackageFsm.RECIBIDO_EN_SUCURSAL_ORIGEN, 4),
            java.util.Map.entry(PackageFsm.RETENIDO_DOCUMENTACION, 5),
            java.util.Map.entry(PackageFsm.CLASIFICADO, 6),
            java.util.Map.entry(PackageFsm.EN_TRANSITO_A_HUB, 7),
            java.util.Map.entry(PackageFsm.EN_HUB, 8),
            java.util.Map.entry(PackageFsm.EN_TRANSITO_CON_ALIADO, 9),
            java.util.Map.entry(PackageFsm.EN_TRANSITO_A_DESTINO, 10),
            java.util.Map.entry(PackageFsm.RECIBIDO_EN_SUCURSAL_DESTINO, 11),
            java.util.Map.entry(PackageFsm.EN_REPARTO, 12),
            java.util.Map.entry(PackageFsm.INCIDENTE_ACTIVO, 13),
            java.util.Map.entry(ENTREGA_FALLIDA, 14),
            java.util.Map.entry(PackageFsm.DEVOLUCION_INICIADA, 15),
            java.util.Map.entry(PackageFsm.RETENIDO, 16),
            // Terminal:
            java.util.Map.entry(CANCELADO, 17),
            java.util.Map.entry(DEVUELTO, 18),
            java.util.Map.entry(ENTREGADO, 19));

    /** The aggregation that {@link #calculate(List)} returns for {@code ENTREGADO_PARCIAL}. */
    public static final String STATUS_ENTREGADO_PARCIAL = "ENTREGADO_PARCIAL";

    /**
     * Compute the shipment status from a list of package statuses.
     * The list may contain duplicates (a package is a single input,
     * not a status — this method takes the statuses). Empty list
     * returns {@code null} (caller's responsibility to validate).
     *
     * @param packageStatuses the current status of every package in the shipment
     * @return the aggregated shipment status, or {@code null} if the input is empty
     */
    public static String calculate(List<String> packageStatuses) {
        if (packageStatuses == null || packageStatuses.isEmpty()) {
            return null;
        }

        // Degenerate single-package shipments: just return the package status.
        if (packageStatuses.size() == 1) {
            return packageStatuses.get(0);
        }

        Set<String> distinct = new HashSet<>(packageStatuses);

        // Rule 1: all ENTREGADO
        if (distinct.size() == 1 && distinct.contains(ENTREGADO)) {
            return ENTREGADO;
        }

        // Rule 2: ENTREGADO_PARCIAL — at least one ENTREGADO AND rest are
        // terminal-but-different. The PRD specifies ENTREGA_FALLIDA and
        // DEVUELTO as the "terminal but different" examples; CANCELADO
        // is also terminal but is NOT considered "delivered in part" —
        // a fully-cancelled shipment is CANCELADO (rule 4).
        if (distinct.contains(ENTREGADO)) {
            Set<String> nonEntregado = new HashSet<>(distinct);
            nonEntregado.remove(ENTREGADO);
            // All non-ENTREGADO must be in this allowlist
            boolean allAllowed = nonEntregado.stream().allMatch(s -> s.equals(ENTREGA_FALLIDA) || s.equals(DEVUELTO));
            if (allAllowed && !nonEntregado.isEmpty()) {
                return STATUS_ENTREGADO_PARCIAL;
            }
        }

        // Rule 3: all DEVUELTO
        if (distinct.size() == 1 && distinct.contains(DEVUELTO)) {
            return DEVUELTO;
        }

        // Rule 4: all CANCELADO
        if (distinct.size() == 1 && distinct.contains(CANCELADO)) {
            return CANCELADO;
        }

        // Rule 5: most-atrasado package state.
        return packageStatuses.stream()
                .max(java.util.Comparator.comparingInt(s -> SEVERITY.getOrDefault(s, 0)))
                .orElse(null);
    }
}
