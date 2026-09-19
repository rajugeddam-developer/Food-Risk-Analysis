package com.foodrisk.cache;

import com.foodrisk.dto.NormalizedFoodData;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * M12 Deterministic Product Fingerprinting Service.
 *
 * Computes a SHA-256 fingerprint from:
 * 1. Normalized product name (lowercase, trimmed).
 * 2. Canonical ingredient names (sorted alphabetically, lowercase, joined with delimiter).
 * 3. Declared nutrition basis (PER_100G / PER_SERVING).
 */
@Service
public class ProductFingerprintService {

    public String computeFingerprint(NormalizedFoodData data) {
        if (data == null) {
            throw new IllegalArgumentException("NormalizedFoodData cannot be null for fingerprint generation");
        }

        String name = data.productName() != null ? data.productName().trim().toLowerCase(Locale.ROOT) : "";

        List<String> ingredients = Collections.emptyList();
        if (data.ingredients() != null) {
            ingredients = data.ingredients().stream()
                    .filter(i -> i != null && i.name() != null && !i.name().isBlank())
                    .map(i -> i.name().trim().toLowerCase(Locale.ROOT))
                    .sorted()
                    .collect(Collectors.toList());
        }

        String basis = "";
        if (data.nutrition() != null && data.nutrition().basis() != null) {
            basis = data.nutrition().basis().trim().toUpperCase(Locale.ROOT);
        }

        String canonicalString = name + "::" + String.join("|", ingredients) + "::" + basis;

        return sha256Hex(canonicalString);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
