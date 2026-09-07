package com.bank.esps.domain.cdm.position;

import com.bank.esps.domain.cdm.base.PositionDirection;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic position identity.
 *
 * <p>State-saving Position grain: Hash(contractId|securityId|direction).
 * Account and book are attributes of the Position, not part of the key.
 *
 * <p>The four-argument factory remains for the event-sourced compatibility
 * path: Hash(account|instrument|currency|direction).
 */
@Getter
@EqualsAndHashCode
public final class PositionKey {
    private final String contractId;
    private final String securityId;
    private final String account;
    private final String instrument;
    private final String currency;
    private final PositionDirection direction;
    private final String value;

    private PositionKey(String contractId, String securityId, String account, String instrument,
                        String currency, PositionDirection direction, String value) {
        this.contractId = contractId;
        this.securityId = securityId;
        this.account = account;
        this.instrument = instrument;
        this.currency = currency;
        this.direction = direction;
        this.value = value;
    }

    /**
     * Position grain used by {@code BasketActivity}: contract + security + long/short.
     */
    public static PositionKey of(String contractId, String securityId, PositionDirection direction) {
        String normalizedContract = normalize(contractId);
        String normalizedSecurity = normalize(securityId);
        PositionDirection dir = direction != null ? direction : PositionDirection.LONG;
        String input = String.join("|", normalizedContract, normalizedSecurity, dir.name());
        return new PositionKey(normalizedContract, normalizedSecurity, null, normalizedSecurity, null, dir, hash(input));
    }

    /**
     * Event-store compatibility key: account + instrument + currency + direction.
     */
    public static PositionKey of(String account, String instrument, String currency, PositionDirection direction) {
        String normalizedAccount = normalize(account);
        String normalizedInstrument = normalize(instrument);
        String normalizedCurrency = normalize(currency);
        PositionDirection dir = direction != null ? direction : PositionDirection.LONG;
        String input = String.join("|", normalizedAccount, normalizedInstrument, normalizedCurrency, dir.name());
        return new PositionKey(null, normalizedInstrument, normalizedAccount, normalizedInstrument,
                normalizedCurrency, dir, hash(input));
    }

    public PositionKey opposite() {
        if (contractId != null && !contractId.isBlank()) {
            return of(contractId, securityId, direction.opposite());
        }
        return of(account, instrument, currency, direction.opposite());
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                String part = Integer.toHexString(0xff & hashBytes[i]);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(Objects.hashCode(input));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
