package com.foodrisk.exception;

/**
 * Thrown when packaging images uploaded for a single analysis belong to conflicting or different products.
 */
public class ProductMismatchException extends RuntimeException {

    private final ErrorCategory category;
    private final String mismatchReason;

    public ProductMismatchException(String message) {
        this(message, null);
    }

    public ProductMismatchException(String message, String mismatchReason) {
        super(message);
        this.category = ErrorCategory.PRODUCT_MISMATCH;
        this.mismatchReason = mismatchReason;
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public String getMismatchReason() {
        return mismatchReason;
    }
}
