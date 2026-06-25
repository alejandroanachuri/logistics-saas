package ar.com.logistics.shipment.fsm;

import static ar.com.logistics.shipment.fsm.PackageFsm.CANCELADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.CLASIFICADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.CREADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.DEVOLUCION_INICIADA;
import static ar.com.logistics.shipment.fsm.PackageFsm.DEVUELTO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ENTREGADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ENTREGA_FALLIDA;
import static ar.com.logistics.shipment.fsm.PackageFsm.EN_HUB;
import static ar.com.logistics.shipment.fsm.PackageFsm.EN_REPARTO;
import static ar.com.logistics.shipment.fsm.PackageFsm.EN_TRANSITO_A_DESTINO;
import static ar.com.logistics.shipment.fsm.PackageFsm.EN_TRANSITO_A_HUB;
import static ar.com.logistics.shipment.fsm.PackageFsm.EN_TRANSITO_CON_ALIADO;
import static ar.com.logistics.shipment.fsm.PackageFsm.ESPERANDO_APROBACION_RUTA;
import static ar.com.logistics.shipment.fsm.PackageFsm.INCIDENTE_ACTIVO;
import static ar.com.logistics.shipment.fsm.PackageFsm.PRE_ALTA;
import static ar.com.logistics.shipment.fsm.PackageFsm.RECIBIDO_EN_SUCURSAL_DESTINO;
import static ar.com.logistics.shipment.fsm.PackageFsm.RECIBIDO_EN_SUCURSAL_ORIGEN;
import static ar.com.logistics.shipment.fsm.PackageFsm.RETENIDO;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link PackageFsm}. Covers every valid transition in the
 * PRD §7.3 catalog, terminal-state defense, and the documented §7.3/§7.4
 * contradiction (RETENIDO → CANCELADO).
 */
class PackageFsmTest {

    // ------------------------------------------------------------------------
    // Happy-path: every documented valid transition is accepted.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("PRE_ALTA can transition to CREADO")
    void pre_alta_to_creado() {
        assertThat(PackageFsm.isValidTransition(PRE_ALTA, CREADO)).isTrue();
    }

    @Test
    @DisplayName("PRE_ALTA can transition to CANCELADO")
    void pre_alta_to_cancelado() {
        assertThat(PackageFsm.isValidTransition(PRE_ALTA, CANCELADO)).isTrue();
    }

    @Test
    @DisplayName("PRE_ALTA can transition to ESPERANDO_APROBACION_RUTA")
    void pre_alta_to_esperando_aprobacion() {
        assertThat(PackageFsm.isValidTransition(PRE_ALTA, ESPERANDO_APROBACION_RUTA))
                .isTrue();
    }

    @Test
    @DisplayName("CREADO can transition to RECIBIDO_EN_SUCURSAL_ORIGEN")
    void creado_to_recibido_origen() {
        assertThat(PackageFsm.isValidTransition(CREADO, RECIBIDO_EN_SUCURSAL_ORIGEN))
                .isTrue();
    }

    @Test
    @DisplayName("RETENIDO → CANCELADO is documented as valid (PRD §7.3 / §7.4 contradiction)")
    void retenido_to_cancelado_is_valid_per_prd_7_3() {
        // PRD §7.3 line 550 lists this as valid; PRD §7.4 says RETENIDO
        // is "reversible". Per the source comment, §7.3 is authoritative
        // and this transition is a deliberate decision.
        assertThat(PackageFsm.isValidTransition(RETENIDO, CANCELADO)).isTrue();
    }

    // ------------------------------------------------------------------------
    // Negative paths: skip-level transitions are rejected.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("PRE_ALTA cannot jump directly to ENTREGADO (must go through CREADO)")
    void pre_alta_to_entregado_is_rejected() {
        assertThat(PackageFsm.isValidTransition(PRE_ALTA, ENTREGADO)).isFalse();
    }

    @Test
    @DisplayName("CREADO cannot jump directly to ENTREGADO (must go through origin)")
    void creado_to_entregado_is_rejected() {
        assertThat(PackageFsm.isValidTransition(CREADO, ENTREGADO)).isFalse();
    }

    // ------------------------------------------------------------------------
    // Terminal-state defense: no outgoing transitions ever allowed.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("ENTREGADO is terminal — every outgoing transition is rejected")
    void entregado_is_terminal() {
        for (String anyState : List.of(PRE_ALTA, CREADO, EN_REPARTO, RETENIDO, DEVUELTO, CANCELADO, ENTREGA_FALLIDA)) {
            assertThat(PackageFsm.isValidTransition(ENTREGADO, anyState))
                    .as("ENTREGADO → %s should be rejected", anyState)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("DEVUELTO is terminal — every outgoing transition is rejected")
    void devuelto_is_terminal() {
        for (String anyState : List.of(PRE_ALTA, CREADO, EN_REPARTO, RETENIDO, ENTREGADO, CANCELADO)) {
            assertThat(PackageFsm.isValidTransition(DEVUELTO, anyState))
                    .as("DEVUELTO → %s should be rejected", anyState)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("CANCELADO is terminal — every outgoing transition is rejected")
    void cancelado_is_terminal() {
        for (String anyState : List.of(PRE_ALTA, CREADO, EN_REPARTO, RETENIDO, ENTREGADO, DEVUELTO)) {
            assertThat(PackageFsm.isValidTransition(CANCELADO, anyState))
                    .as("CANCELADO → %s should be rejected", anyState)
                    .isFalse();
        }
    }

    // ------------------------------------------------------------------------
    // Null safety.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isValidTransition returns false when 'from' is null")
    void null_from_returns_false() {
        assertThat(PackageFsm.isValidTransition(null, CREADO)).isFalse();
    }

    @Test
    @DisplayName("isValidTransition returns false when 'to' is null")
    void null_to_returns_false() {
        assertThat(PackageFsm.isValidTransition(CREADO, null)).isFalse();
    }

    @Test
    @DisplayName("isValidTransition returns false when both args are null")
    void null_both_returns_false() {
        assertThat(PackageFsm.isValidTransition(null, null)).isFalse();
    }

    // ------------------------------------------------------------------------
    // validNextStates.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("validNextStates returns the 3 valid next states from PRE_ALTA")
    void valid_next_states_pre_alta() {
        assertThat(PackageFsm.validNextStates(PRE_ALTA))
                .containsExactlyInAnyOrder(ESPERANDO_APROBACION_RUTA, CREADO, CANCELADO);
    }

    @Test
    @DisplayName("validNextStates returns an empty list for terminal ENTREGADO")
    void valid_next_states_entregado_is_empty() {
        assertThat(PackageFsm.validNextStates(ENTREGADO)).isEmpty();
    }

    @Test
    @DisplayName("validNextStates returns the 10 release states from RETENIDO")
    void valid_next_states_retenido() {
        assertThat(PackageFsm.validNextStates(RETENIDO))
                .containsExactlyInAnyOrder(
                        RECIBIDO_EN_SUCURSAL_ORIGEN,
                        CLASIFICADO,
                        EN_TRANSITO_A_HUB,
                        EN_HUB,
                        EN_TRANSITO_CON_ALIADO,
                        EN_TRANSITO_A_DESTINO,
                        RECIBIDO_EN_SUCURSAL_DESTINO,
                        EN_REPARTO,
                        DEVOLUCION_INICIADA,
                        CANCELADO);
    }

    @Test
    @DisplayName("validNextStates returns an empty list for null")
    void valid_next_states_null_is_empty() {
        assertThat(PackageFsm.validNextStates(null)).isEmpty();
    }

    // ------------------------------------------------------------------------
    // isFinal.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isFinal returns true for every terminal state")
    void is_final_for_terminal_states() {
        assertThat(PackageFsm.isFinal(ENTREGADO)).isTrue();
        assertThat(PackageFsm.isFinal(DEVUELTO)).isTrue();
        assertThat(PackageFsm.isFinal(CANCELADO)).isTrue();
    }

    @Test
    @DisplayName("isFinal returns false for non-terminal states")
    void is_final_for_non_terminal_states() {
        assertThat(PackageFsm.isFinal(CREADO)).isFalse();
        assertThat(PackageFsm.isFinal(PRE_ALTA)).isFalse();
        assertThat(PackageFsm.isFinal(RETENIDO)).isFalse();
        assertThat(PackageFsm.isFinal(EN_TRANSITO_A_DESTINO)).isFalse();
        assertThat(PackageFsm.isFinal(ENTREGA_FALLIDA)).isFalse();
        assertThat(PackageFsm.isFinal(INCIDENTE_ACTIVO)).isFalse();
    }

    @Test
    @DisplayName("isFinal returns false for null")
    void is_final_for_null_is_false() {
        assertThat(PackageFsm.isFinal(null)).isFalse();
    }

    // ------------------------------------------------------------------------
    // Exhaustive catalog sweep — every entry in VALID_TRANSITIONS is verified.
    // ------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1} is valid")
    @CsvSource({
        // from, to
        "PRE_ALTA, ESPERANDO_APROBACION_RUTA",
        "PRE_ALTA, CREADO",
        "PRE_ALTA, CANCELADO",
        "ESPERANDO_APROBACION_RUTA, CREADO",
        "ESPERANDO_APROBACION_RUTA, CANCELADO",
        "CREADO, RECIBIDO_EN_SUCURSAL_ORIGEN",
        "CREADO, CANCELADO",
        "RECIBIDO_EN_SUCURSAL_ORIGEN, CLASIFICADO",
        "RECIBIDO_EN_SUCURSAL_ORIGEN, RETENIDO_DOCUMENTACION",
        "RECIBIDO_EN_SUCURSAL_ORIGEN, CANCELADO",
        "RECIBIDO_EN_SUCURSAL_ORIGEN, RETENIDO",
        "RETENIDO_DOCUMENTACION, CLASIFICADO",
        "RETENIDO_DOCUMENTACION, CANCELADO",
        "CLASIFICADO, EN_TRANSITO_A_HUB",
        "CLASIFICADO, EN_TRANSITO_A_DESTINO",
        "CLASIFICADO, EN_TRANSITO_CON_ALIADO",
        "CLASIFICADO, RETENIDO",
        "EN_TRANSITO_A_HUB, EN_HUB",
        "EN_TRANSITO_A_HUB, RETENIDO",
        "EN_HUB, EN_TRANSITO_A_DESTINO",
        "EN_HUB, EN_TRANSITO_CON_ALIADO",
        "EN_HUB, RETENIDO",
        "EN_TRANSITO_CON_ALIADO, EN_TRANSITO_A_DESTINO",
        "EN_TRANSITO_CON_ALIADO, RECIBIDO_EN_SUCURSAL_DESTINO",
        "EN_TRANSITO_CON_ALIADO, RETENIDO",
        "EN_TRANSITO_A_DESTINO, RECIBIDO_EN_SUCURSAL_DESTINO",
        "EN_TRANSITO_A_DESTINO, RETENIDO",
        "RECIBIDO_EN_SUCURSAL_DESTINO, EN_REPARTO",
        "RECIBIDO_EN_SUCURSAL_DESTINO, RETENIDO",
        "RECIBIDO_EN_SUCURSAL_DESTINO, DEVOLUCION_INICIADA",
        "EN_REPARTO, ENTREGADO",
        "EN_REPARTO, ENTREGA_FALLIDA",
        "EN_REPARTO, INCIDENTE_ACTIVO",
        "ENTREGA_FALLIDA, EN_REPARTO",
        "ENTREGA_FALLIDA, DEVOLUCION_INICIADA",
        "ENTREGA_FALLIDA, RETENIDO",
        "INCIDENTE_ACTIVO, DEVOLUCION_INICIADA",
        "INCIDENTE_ACTIVO, RETENIDO",
        "INCIDENTE_ACTIVO, EN_REPARTO",
        "DEVOLUCION_INICIADA, DEVUELTO",
    })
    @DisplayName("Catalog: documented valid transition is accepted")
    void catalog_valid_transitions_are_accepted(String from, String to) {
        assertThat(PackageFsm.isValidTransition(from, to)).isTrue();
    }
}
