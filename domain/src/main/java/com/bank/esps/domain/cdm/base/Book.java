package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trading book used for book-level access control. ESPS extension of
 * CDM party / account reference data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book {
    private String bookId;
    private String bookName;

    public static Book of(String bookId) {
        return Book.builder().bookId(bookId).build();
    }
}
