package com.trazalga.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * DTO genérico para respuestas paginadas de la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PageDTO<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        return PageDTO.<T>builder()
                .content(content != null ? content : Collections.emptyList())
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(pageNumber == 0)
                .last(pageNumber >= totalPages - 1)
                .build();
    }

    public static <T> PageDTO<T> vacia(int pageNumber, int pageSize) {
        return PageDTO.<T>builder()
                .content(Collections.emptyList())
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }
}
