/**
 * CDM-aligned, state-saving domain model for the position service.
 *
 * <p>Reference: FINOS Common Domain Model
 * ({@code https://github.com/finos/common-domain-model}).
 *
 * <p>Firm mapping from the existing {@code DOBasket*} objects:
 * <ul>
 *   <li>{@code DOBasketActivity} / {@code DOBasketActivityTable} →
 *       {@link com.bank.esps.domain.cdm.basket.BasketActivity}</li>
 *   <li>{@code DOBasketActivityDetails} (hedge trades allocated to a contract) →
 *       {@link com.bank.esps.domain.cdm.basket.BasketActivityDetail}</li>
 *   <li>{@code DOBasketActivityOpenLotTable} →
 *       {@link com.bank.esps.domain.cdm.basket.OpenLot}</li>
 *   <li>{@code DOBasketActClosingLotTable} →
 *       {@link com.bank.esps.domain.cdm.basket.ClosingLot}</li>
 *   <li>{@code DOBasketSettlementTable} →
 *       {@link com.bank.esps.domain.cdm.basket.BasketSettlement}</li>
 *   <li>{@code DOBasketActDivOpenLotTable} →
 *       {@link com.bank.esps.domain.cdm.basket.DividendOpenLot}</li>
 *   <li>{@code DOBasketActDivClosingTable} →
 *       {@link com.bank.esps.domain.cdm.basket.DividendClosingLot}</li>
 * </ul>
 *
 * <p>Position identity is {@code contractId + securityId + direction}. Account
 * and book are attributes. Portfolio swaps are one contract with N Position
 * rows (one per constituent security + long/short). CIB is underlier
 * metadata ({@code identifierScheme} + {@code instrumentClass}), not a
 * product type.
 *
 * <p>Products are extensible: {@link com.bank.esps.domain.cdm.product.FinancialProduct}
 * is a CDM {@code NonTransferableProduct} with composable payouts and an
 * {@link com.bank.esps.domain.cdm.product.Underlier} choice (equity, index, FX,
 * rates, commodity, credit, or basket). {@code SWAP} and {@code CFD} are first
 * product types; forwards and options can be added without changing basket
 * persistence.
 *
 * <p>Persistence is state-saving, not event-sourced: allocate a hedge, then
 * {@link com.bank.esps.domain.cdm.repository.BasketActivityRepository#save}
 * the whole aggregate. History lives in the child rows.
 */
package com.bank.esps.domain.cdm;
