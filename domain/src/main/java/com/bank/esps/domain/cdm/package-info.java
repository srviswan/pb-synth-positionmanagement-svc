/**
 * CDM-aligned domain model for the position service.
 *
 * <p>This package is a pragmatic subset of the FINOS Common Domain Model
 * ({@code https://github.com/finos/common-domain-model}) scoped to position
 * keeping. Types and functions follow CDM names and cardinality where they
 * apply to this service, and add ESPS-specific extensions (tax-lot methods,
 * book, hybrid settlement tracking, direction-aware position keys, UPI).
 *
 * <p>Primary CDM mappings:
 * <ul>
 *   <li>{@code cdm.event.position.CounterpartyPosition} → {@link com.bank.esps.domain.cdm.position.CounterpartyPosition}</li>
 *   <li>{@code cdm.event.common.CounterpartyPositionState} → {@link com.bank.esps.domain.cdm.position.CounterpartyPositionState}</li>
 *   <li>{@code cdm.event.common.CounterpartyPositionBusinessEvent} → {@link com.bank.esps.domain.cdm.event.CounterpartyPositionBusinessEvent}</li>
 *   <li>{@code cdm.event.common.QuantityChangeInstruction} → {@link com.bank.esps.domain.cdm.event.QuantityChangeInstruction}</li>
 *   <li>{@code cdm.observable.asset.PriceQuantity} → {@link com.bank.esps.domain.cdm.position.PriceQuantity}</li>
 * </ul>
 */
package com.bank.esps.domain.cdm;
