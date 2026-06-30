package ar.com.logistics.shipment.tracking;

import static ar.com.logistics.shipment.fsm.PackageFsm.CANCELADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.CREADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.DEVUELTO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ENTREGADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ENTREGA_FALLIDA;
import static ar.com.logistics.shipment.fsm.PackageFsm.EN_TRANSITO_A_DESTINO;
import static ar.com.logistics.shipment.fsm.PackageFsm.PRE_ALTA;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShipmentStatusCalculator}. Covers every PRD §7.6
 * aggregation rule, including the ENTREGADO_PARCIAL edge cases and the
 * most-atrasado severity fallback.
 *
 * <p>Note: in this codebase's vocabulary, "most-atrasado" means "the
 * state furthest along in the delivery flow" (highest severity in the
 * forward-flow ranking), not "the state furthest behind". The
 * severity map ranks later states with higher numbers, so the
 * {@code max(severity)} fallback returns the most advanced state.
 */
class ShipmentStatusCalculatorTest {

    // ------------------------------------------------------------------------
    // Degenerate inputs.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Empty list returns null (caller validates)")
    void empty_list_returns_null() {
        assertThat(ShipmentStatusCalculator.calculate(List.of())).isNull();
    }

    @Test
    @DisplayName("Null list returns null (caller validates)")
    void null_list_returns_null() {
        assertThat(ShipmentStatusCalculator.calculate(null)).isNull();
    }

    @Test
    @DisplayName("Single-package shipment returns the package's own status")
    void single_package_returns_its_status() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(ENTREGADO))).isEqualTo(ENTREGADO);
        assertThat(ShipmentStatusCalculator.calculate(List.of(CREADO))).isEqualTo(CREADO);
        assertThat(ShipmentStatusCalculator.calculate(List.of(CANCELADO))).isEqualTo(CANCELADO);
    }

    // ------------------------------------------------------------------------
    // Rule 1: all packages ENTREGADO → shipment ENTREGADO.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 1: all packages ENTREGADO → shipment ENTREGADO")
    void all_entregado_returns_entregado() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(ENTREGADO, ENTREGADO, ENTREGADO)))
                .isEqualTo(ENTREGADO);
    }

    // ------------------------------------------------------------------------
    // Rule 2: ENTREGADO_PARCIAL.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 2: ENTREGADO + ENTREGA_FALLIDA → ENTREGADO_PARCIAL (happy partial)")
    void entregado_and_entrega_fallida_is_parcial() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(ENTREGADO, ENTREGA_FALLIDA)))
                .isEqualTo(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
        assertThat(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL).isEqualTo("ENTREGADO_PARCIAL");
    }

    @Test
    @DisplayName("Rule 2: ENTREGADO + DEVUELTO → ENTREGADO_PARCIAL (variant)")
    void entregado_and_devuelto_is_parcial() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(ENTREGADO, DEVUELTO)))
                .isEqualTo(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
    }

    @Test
    @DisplayName("Rule 2: ENTREGADO + ENTREGA_FALLIDA + DEVUELTO → ENTREGADO_PARCIAL")
    void entregado_with_both_failures_is_parcial() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(ENTREGADO, ENTREGA_FALLIDA, DEVUELTO)))
                .isEqualTo(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
    }

    @Test
    @DisplayName("Rule 2 excludes CANCELADO: ENTREGADO + CANCELADO falls through to rule 5")
    void entregado_and_cancelado_is_not_parcial() {
        // CANCELADO is terminal but is NOT in the {ENTREGA_FALLIDA, DEVUELTO}
        // allowlist for ENTREGADO_PARCIAL. Falls through to rule 5.
        // max severity: ENTREGADO(19) vs CANCELADO(17) → ENTREGADO.
        String result = ShipmentStatusCalculator.calculate(List.of(ENTREGADO, CANCELADO));
        assertThat(result).isNotEqualTo(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
        assertThat(result).isEqualTo(ENTREGADO);
    }

    @Test
    @DisplayName("Rule 2 requires non-ENTREGADO to be terminal: CREADO + ENTREGADO falls through")
    void creado_and_entregado_is_not_parcial() {
        // CREADO is non-terminal, so ENTREGADO_PARCIAL does not apply.
        // Falls through to rule 5. max severity: ENTREGADO(19) vs CREADO(3) → ENTREGADO.
        String result = ShipmentStatusCalculator.calculate(List.of(CREADO, ENTREGADO));
        assertThat(result).isNotEqualTo(ShipmentStatusCalculator.STATUS_ENTREGADO_PARCIAL);
        assertThat(result).isEqualTo(ENTREGADO);
    }

    // ------------------------------------------------------------------------
    // Rule 3: all packages DEVUELTO → shipment DEVUELTO.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 3: all packages DEVUELTO → shipment DEVUELTO")
    void all_devuelto_returns_devuelto() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(DEVUELTO, DEVUELTO, DEVUELTO)))
                .isEqualTo(DEVUELTO);
    }

    // ------------------------------------------------------------------------
    // Rule 4: all packages CANCELADO → shipment CANCELADO.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 4: all packages CANCELADO → shipment CANCELADO")
    void all_cancelado_returns_cancelado() {
        assertThat(ShipmentStatusCalculator.calculate(List.of(CANCELADO, CANCELADO, CANCELADO)))
                .isEqualTo(CANCELADO);
    }

    // ------------------------------------------------------------------------
    // Rule 5: most-atrasado fallback (max severity).
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Rule 5: CREADO + EN_TRANSITO_A_DESTINO → EN_TRANSITO_A_DESTINO (later in flow wins)")
    void rule_5_picks_later_state() {
        // EN_TRANSITO_A_DESTINO (severity 10) > CREADO (severity 3).
        assertThat(ShipmentStatusCalculator.calculate(List.of(CREADO, EN_TRANSITO_A_DESTINO)))
                .isEqualTo(EN_TRANSITO_A_DESTINO);
    }

    @Test
    @DisplayName("Rule 5: PRE_ALTA + CREADO → CREADO (later in flow wins)")
    void rule_5_pre_alta_and_creado() {
        // CREADO (severity 3) > PRE_ALTA (severity 1).
        assertThat(ShipmentStatusCalculator.calculate(List.of(PRE_ALTA, CREADO)))
                .isEqualTo(CREADO);
    }

    @Test
    @DisplayName("Rule 5: PRE_ALTA + ENTREGADO → ENTREGADO (terminal wins)")
    void rule_5_pre_alta_and_entregado() {
        // ENTREGADO (severity 19) wins.
        assertThat(ShipmentStatusCalculator.calculate(List.of(PRE_ALTA, ENTREGADO)))
                .isEqualTo(ENTREGADO);
    }
}
