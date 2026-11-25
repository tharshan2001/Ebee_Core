package com.ebee.inventory.dto;

import java.math.BigDecimal;

/** Expired stock removed: batches touched, units written off, their cost, units held back by reservations. */
public record WriteOffResult(int batches, int units, BigDecimal value, int unitsKeptForReservations) {
}
