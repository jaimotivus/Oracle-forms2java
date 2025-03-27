package com.example.application.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entity representing the SINT_RESERVA_COBERTURA_CERTIFI table.
 * This entity stores information about insurance claim reserves for specific coverages.
 * It maps to the data structure used in the SINF501041 Oracle Forms application.
 * <p>
 * This entity should be managed via FormService for complex business logic.
 */
@Entity
@Table(name = "SINT_RESERVA_COBERTURA_CERTIFI")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SinfEntity {

    private static final Logger logger = LoggerFactory.getLogger(SinfEntity.class);

    /**
     * Composite primary key for the SINT_RESERVA_COBERTURA_CERTIFI table
     */
    @EmbeddedId
    private SinfEntityId id;

    /**
     * Branch office code
     */
    @Column(name = "SICC_CACE_CASU_CD_SUCURSA")
    private Integer branchOfficeCode;

    /**
     * Insurance policy number
     */
    @Column(name = "SICC_CACE_CAPO_NU_POLIZA")
    private Long policyNumber;

    /**
     * Certificate number
     */
    @Column(name = "SICC_CACE_NU_CERTIFICADO")
    private Long certificateNumber;

    /**
     * Insured person number
     */
    @Column(name = "SICC_CARC_NU_ASEGURADO")
    private Integer insuredPersonNumber;

    /**
     * Effective date of the coverage
     */
    @Column(name = "SICC_CARC_FE_EFECTIVA")
    private LocalDate effectiveDate;

    /**
     * Sum insured amount
     */
    @Column(name = "SICC_MT_SUMASEG")
    @NotNull
    private BigDecimal insuredAmount;

    /**
     * Initial reserve amount
     */
    @Column(name = "SICC_MT_RESERVA")
    @NotNull
    private BigDecimal initialReserveAmount;

    /**
     * Adjusted amount
     */
    @Column(name = "SICC_MT_AJUSTADO")
    @NotNull
    private BigDecimal adjustedAmount;

    /**
     * Liquidation amount
     */
    @Column(name = "SICC_MT_LIQUIDACION")
    @NotNull
    private BigDecimal liquidationAmount;

    /**
     * Payment amount
     */
    @Column(name = "SICC_MT_PAGO")
    @NotNull
    private BigDecimal paymentAmount;

    /**
     * Rejection amount
     */
    @Column(name = "SICC_MT_RECHAZO")
    @NotNull
    private BigDecimal rejectionAmount;

    /**
     * Deductible amount
     */
    @Column(name = "SICC_MT_DEDUCIBLE")
    @NotNull
    private BigDecimal deductibleAmount;

    /**
     * Coverage priority
     */
    @Column(name = "COB_PRIORIDAD")
    private Integer coveragePriority;

    @PrePersist
    @PreUpdate
    private void prePersist() {
        // Initialize BigDecimal fields to ZERO if null during persistence.
        if (insuredAmount == null) insuredAmount = BigDecimal.ZERO;
        if (initialReserveAmount == null) initialReserveAmount = BigDecimal.ZERO;
        if (adjustedAmount == null) adjustedAmount = BigDecimal.ZERO;
        if (liquidationAmount == null) liquidationAmount = BigDecimal.ZERO;
        if (paymentAmount == null) paymentAmount = BigDecimal.ZERO;
        if (rejectionAmount == null) rejectionAmount = BigDecimal.ZERO;
        if (deductibleAmount == null) deductibleAmount = BigDecimal.ZERO;
    }

    /**
     * Embedded ID class for SinfEntity
     */
    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SinfEntityId {

        /**
         * Branch office code for the claim
         */
        @Column(name = "SICC_SISI_CASU_CD_SUCURSA")
        private Integer claimBranchOfficeCode;

        /**
         * Claim number
         */
        @Column(name = "SICC_SICO_SISI_NU_SINIESTRO")
        private Long claimNumber;

        /**
         * Accounting branch code
         */
        @Column(name = "SICC_SICO_CACB_CARB_CD_RAMO")
        private Integer accountingBranchCode;

        /**
         * Coverage code
         */
        @Column(name = "SICC_SICO_CACB_CD_COBERTURA")
        private String coverageCode;

        /**
         * Policy branch code
         */
        @Column(name = "SICC_SICE_CACE_CARP_CD_RAMO")
        private Integer policyBranchCode;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SinfEntityId that = (SinfEntityId) o;
            return Objects.equals(claimBranchOfficeCode, that.claimBranchOfficeCode) &&
                   Objects.equals(claimNumber, that.claimNumber) &&
                   Objects.equals(accountingBranchCode, that.accountingBranchCode) &&
                   Objects.equals(coverageCode, that.coverageCode) &&
                   Objects.equals(policyBranchCode, that.policyBranchCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(claimBranchOfficeCode, claimNumber, accountingBranchCode, coverageCode, policyBranchCode);
        }
    }

    /**
     * Calculates the current balance of the reserve.
     * This is equivalent to the MT_SALDO calculation in the original Oracle Forms.
     * <p>
     * It sums the initial reserve amount and adjusted amount, then subtracts the liquidation amount and payment amount,
     * and finally adds the rejection amount.  All null values are treated as BigDecimal.ZERO to avoid NullPointerExceptions.
     *
     * @return the current balance, never null.
     */
    @Transient
    public BigDecimal getCurrentBalance() {
        BigDecimal initialReserve = initialReserveAmount != null ? initialReserveAmount : BigDecimal.ZERO;
        BigDecimal adjusted = adjustedAmount != null ? adjustedAmount : BigDecimal.ZERO;
        BigDecimal liquidation = liquidationAmount != null ? liquidationAmount : BigDecimal.ZERO;
        BigDecimal payment = paymentAmount != null ? paymentAmount : BigDecimal.ZERO;
        BigDecimal rejection = rejectionAmount != null ? rejectionAmount : BigDecimal.ZERO;

        return initialReserve.add(adjusted).subtract(liquidation).subtract(payment).add(rejection);
    }

    /**
     * Checks if the adjusted amount exceeds the insured amount.
     * This is part of the validation logic from the original Oracle Forms.
     * <p>
     * If the insured amount is null or zero, this method returns false.  This behavior is based on the assumption
     * that if there's no insured amount, the exceeding check is not relevant.
     *
     * @return true if the adjusted amount exceeds the insured amount, false otherwise.
     */
    @Transient
    public boolean isAdjustedAmountExceedingInsuredAmount() {
        if (insuredAmount == null || insuredAmount.compareTo(BigDecimal.ZERO) == 0) {
            logger.warn("Insured amount is null or zero. Returning false for exceeding check.");
            return false;
        }

        BigDecimal totalReserve = (initialReserveAmount != null ? initialReserveAmount : BigDecimal.ZERO)
                .add(adjustedAmount != null ? adjustedAmount : BigDecimal.ZERO);

        return totalReserve.compareTo(insuredAmount) > 0;
    }

    /**
     * Updates the adjusted amount with a new adjustment value.
     * This is equivalent to part of the fAjusteReserva function in the original Oracle Forms.
     * <p>
     * If newAdjustment is null, a warning is logged, and the method returns without making any changes.
     *
     * @param newAdjustment the new adjustment amount.
     */
    public void updateAdjustedAmount(BigDecimal newAdjustment) {
        if (newAdjustment == null) {
            logger.warn("New adjustment is null. No adjustment will be made.");
            return;
        }

        BigDecimal currentBalance = getCurrentBalance();
        BigDecimal adjustmentDifference = newAdjustment.subtract(currentBalance);

        if (adjustedAmount == null) {
            adjustedAmount = adjustmentDifference;
        } else {
            adjustedAmount = adjustedAmount.add(adjustmentDifference);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SinfEntity that = (SinfEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SinfEntity{" +
               "id=" + id +
               ", branchOfficeCode=" + branchOfficeCode +
               ", policyNumber=" + policyNumber +
               ", certificateNumber=" + certificateNumber +
               ", insuredPersonNumber=" + insuredPersonNumber +
               ", effectiveDate=" + effectiveDate +
               ", insuredAmount=" + insuredAmount +
               ", initialReserveAmount=" + initialReserveAmount +
               ", adjustedAmount=" + adjustedAmount +
               ", liquidationAmount=" + liquidationAmount +
               ", paymentAmount=" + paymentAmount +
               ", rejectionAmount=" + rejectionAmount +
               ", deductibleAmount=" + deductibleAmount +
               ", coveragePriority=" + coveragePriority +
               '}';
    }
}