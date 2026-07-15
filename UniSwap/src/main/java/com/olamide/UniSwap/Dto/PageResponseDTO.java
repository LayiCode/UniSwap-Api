package com.olamide.UniSwap.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

// Stable, predictable pagination shape for API responses — deliberately not
// just serializing Spring's Page object directly, which carries internal
// implementation details (like a "pageable" object) that aren't a contract
// we want clients relying on.
@Getter
@Builder
@AllArgsConstructor
public class PageResponseDTO<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <E, D> PageResponseDTO<D> from(Page<E> page, List<D> mappedContent) {
        return PageResponseDTO.<D>builder()
                .content(mappedContent)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}