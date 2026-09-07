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
 * Deterministic position identity. ESPS extension of CDM
 * {@code PositionIdentifier}: Hash(account|instrument|currency|direction).
 */
@Getter
@EqualsAndHashCode
public final class PositionKey {
    private final String account;
    private final String instrument;
    private final String currency;
    private final PositionDirection direction;
    private final String value;

    private PositionKey(String account, String instrument, String currency,
                        PositionDirection direction, String value) {
        this.account = account;
        this.instrument = instrument;
        this.currency = currency;
        this.direction = direction;
        this.value = value;
    }

    public static PositionKey of(String account, String instrument, String currency, PositionDirection direction) {
        String normalizedAccount = normalize(account);
        String normalizedInstrument = normalize(instrument);
        String normalizedCurrency = normalize(currency);
        PositionDirection dir = direction != null ? direction : PositionDirection.LONG;
        String input = String.join("|", normalizedAccount, normalizedInstrument, normalizedCurrency, dir.name());
        return new PositionKey(normalizedAccount, normalizedInstrument, normalizedCurrency, dir, hash(input));
    }

    public PositionKey opposite() {
        return of(account, instrument, currency, direction.opposite());
    }

    private static String normalize(String value) {
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
