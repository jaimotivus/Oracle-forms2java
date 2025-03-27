package com.example.application.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Transfer Object for Siniestro (Claim) information. This DTO represents the data
 * structure for claims management and reserve adjustments. It encapsulates data from the
 * SINF50104 Oracle Forms module.
 *
 * <p>This DTO contains adjustment business logic, which might be better placed in a dedicated
 * service in a future refactoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinfDTO implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(SinfDTO.class);

  // Siniestro (Claim) information
  private Long siniestroId;
  private Integer sucursalId;
  private Integer ramoId;
  private LocalDate fechaOcurrencia;
  private Integer estatusSiniestro;
  private String descripcionEstatus;
  private String nombreAsegurado;

  // Policy information
  private Integer polizaSucursalId;
  private Integer polizaRamoId;
  private Long polizaNumero;
  private Long certificadoNumero;
  private Integer beneficiarioNumero;

  // Coverage information
  private Integer ramoContableId;
  private String ramoContableDescripcion;
  private String coberturaId;
  private String coberturaDescripcion;
  private Integer prioridad;

  // Financial information
  @NotNull private BigDecimal sumaAsegurada;
  @NotNull private BigDecimal reservaInicial;
  @NotNull private BigDecimal montoAjustado;
  @NotNull private BigDecimal montoLiquidacion;
  @NotNull private BigDecimal montoRechazo;
  @NotNull private BigDecimal saldo;
  private BigDecimal ajusteReserva;
  private BigDecimal nuevoSaldo;
  private BigDecimal nuevoMontoAjustado;
  private BigDecimal montoAjustadoMovimiento;

  // Status flags
  private String marcaValidacion;
  private String mensajeValidacion;

  // Global values
  private BigDecimal sumaAseguradaPendiente;
  private BigDecimal sumaAseguradaPendienteOriginal;
  private String remesaCarga;

  /**
   * Calculates the new adjusted amount based on the adjustment reserve and current balance.
   *
   * @return The calculated new adjusted amount. Returns BigDecimal.ZERO if montoAjustado is null.
   */
  public BigDecimal calculateNuevoMontoAjustado() {
    if (montoAjustado == null) {
      montoAjustado = BigDecimal.ZERO;
    }

    if (ajusteReserva == null || saldo == null) {
      return montoAjustado;
    }

    return montoAjustado.add(ajusteReserva.subtract(saldo));
  }

  /**
   * Calculates the adjusted movement amount based on adjustment reserve and current balance.
   *
   * @return The calculated adjusted movement amount. Returns BigDecimal.ZERO if either
   *     ajusteReserva or saldo is null.
   */
  public BigDecimal calculateMontoAjustadoMovimiento() {
    if (ajusteReserva == null || saldo == null) {
      return BigDecimal.ZERO;
    }

    return ajusteReserva.subtract(saldo);
  }

  /**
   * Calculates the new balance based on the adjustment reserve.
   *
   * @return The calculated new balance. Returns BigDecimal.ZERO if ajusteReserva is null.
   */
  public BigDecimal calculateNuevoSaldo() {
    return ajusteReserva != null ? ajusteReserva : BigDecimal.ZERO;
  }

  /**
   * Validates if the adjustment reserve is valid based on several business rules.
   *
   * @return true if valid, false otherwise. Sets the mensajeValidacion if validation fails.
   */
  public boolean isAjusteReservaValid() {
    // Null check for ajusteReserva
    if (ajusteReserva == null) {
      mensajeValidacion = "El ajuste de reserva no puede ser nulo.";
      return false;
    }

    // Negative adjustment validation
    if (ajusteReserva.compareTo(BigDecimal.ZERO) < 0) {
      mensajeValidacion = "El ajuste no puede ser menor a 0 (CERO).";
      return false;
    }

    // Zero adjustment validation
    if (ajusteReserva.compareTo(BigDecimal.ZERO) == 0) {
      // This would typically prompt a confirmation dialog in the UI
      return true;
    }

    // Same as current balance validation
    if (saldo != null && ajusteReserva.compareTo(saldo) == 0) {
      mensajeValidacion =
          "Monto de Ajuste debe ser diferente al saldo. Monto Ajustado: "
              + ajusteReserva
              + " Saldo: "
              + saldo;
      return false;
    }

    // Sum insured validation
    if (sumaAsegurada != null && nuevoSaldo != null && nuevoSaldo.compareTo(sumaAsegurada) > 0) {
      mensajeValidacion =
          "Monto de la reserva no debe ser mayor a la suma asegurada que es de " + sumaAsegurada;
      return false;
    }

    return true;
  }

  /**
   * Assigns validation status based on priority and adjustment values.
   *
   * <p>marcaValidacion values: "P" - Priority, "N" - No Priority, "S" - Standard
   */
  public void assignValidationStatus() {
    if (montoAjustadoMovimiento != null && prioridad != null) {
      marcaValidacion = "P"; // Priority
    } else if (montoAjustadoMovimiento != null && prioridad == null) {
      marcaValidacion = "N"; // No Priority
    } else {
      marcaValidacion = "S"; // Standard
    }
  }

  /** Clears adjustment amounts, resetting them to null. */
  public void clearAdjustmentAmounts() {
    this.ajusteReserva = null;
    this.nuevoMontoAjustado = null;
    this.nuevoSaldo = null;
    this.mensajeValidacion = null;
    this.montoAjustadoMovimiento = null;
    this.marcaValidacion = null;
  }

  /**
   * Updates financial values after an adjustment is applied based on the validation status.
   *
   * <p>This method performs calculations based on 'marcaValidacion' and handles potential null
   * values and BigDecimal operations safely. It also includes logging for debugging purposes.
   *
   * @throws IllegalStateException if montoAjustado is null.
   */
  public void applyAdjustment() {
    // Ensure montoAjustado is not null
    if (montoAjustado == null) {
      logger.error("montoAjustado is null in applyAdjustment(). This is an illegal state.");
      throw new IllegalStateException("montoAjustado cannot be null.");
    }

    try {
      if (marcaValidacion != null) {
        switch (marcaValidacion) {
          case "N":
            // Case "N": Add the calculated movement to the adjusted amount and set the new balance
            BigDecimal calculatedMovimientoN = calculateMontoAjustadoMovimiento();
            nuevoMontoAjustado = montoAjustado.add(calculatedMovimientoN);
            montoAjustadoMovimiento = calculatedMovimientoN;
            nuevoSaldo = Objects.requireNonNullElse(ajusteReserva, BigDecimal.ZERO);
            logger.debug(
                "Applying adjustment with marcaValidacion 'N'. nuevoMontoAjustado: {}, nuevoSaldo: {}",
                nuevoMontoAjustado,
                nuevoSaldo);
            break;

          case "K":
            // Case "K": Perform more complex arithmetic
            BigDecimal calculatedMovimientoK = calculateMontoAjustadoMovimiento();
            nuevoMontoAjustado =
                Objects.requireNonNullElse(nuevoMontoAjustado, BigDecimal.ZERO)
                    .add(montoAjustado)
                    .add(calculatedMovimientoK);
            montoAjustadoMovimiento =
                Objects.requireNonNullElse(montoAjustadoMovimiento, BigDecimal.ZERO)
                    .add(Objects.requireNonNullElse(ajusteReserva, BigDecimal.ZERO))
                    .subtract(saldo);
            nuevoSaldo =
                Objects.requireNonNullElse(nuevoSaldo, BigDecimal.ZERO)
                    .add(Objects.requireNonNullElse(ajusteReserva, BigDecimal.ZERO));
            saldo = nuevoSaldo; // Update saldo
            logger.debug(
                "Applying adjustment with marcaValidacion 'K'. nuevoMontoAjustado: {}, nuevoSaldo: {}",
                nuevoMontoAjustado,
                nuevoSaldo);
            break;

          default:
            logger.warn("Unknown marcaValidacion value: {}", marcaValidacion);
            break;
        }
      }

      // Handle cases where the new balance is negative
      if (nuevoSaldo != null && nuevoSaldo.compareTo(BigDecimal.ZERO) < 0) {
        logger.warn(
            "nuevoSaldo is negative. Resetting to original values. nuevoSaldo: {}, montoAjustado: {}",
            nuevoSaldo,
            montoAjustado);
        nuevoMontoAjustado = montoAjustado;
        nuevoSaldo = saldo;
      }

    } catch (NullPointerException e) {
      logger.error("NullPointerException during applyAdjustment: ", e);
      throw new IllegalStateException("A required field is null during adjustment.", e);
    } catch (ArithmeticException e) {
      logger.error("ArithmeticException during applyAdjustment: ", e);
      throw new IllegalStateException("An arithmetic error occurred during adjustment.", e);
    } catch (Exception e) {
      logger.error("Unexpected exception during applyAdjustment: ", e);
      throw new IllegalStateException("An unexpected error occurred during adjustment.", e);
    }
  }

  /**
   * Custom setter for ajusteReserva. Performs validation and sets related fields.
   *
   * @param ajusteReserva The new adjustment reserve value.
   */
  public void setAjusteReserva(BigDecimal ajusteReserva) {
    this.ajusteReserva = ajusteReserva;
    // Recalculate dependent values when ajusteReserva is set
    this.nuevoSaldo = calculateNuevoSaldo();
    this.nuevoMontoAjustado = calculateNuevoMontoAjustado();
    this.montoAjustadoMovimiento = calculateMontoAjustadoMovimiento();
  }
}