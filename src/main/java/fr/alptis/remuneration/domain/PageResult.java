package fr.alptis.remuneration.domain;

import java.util.List;

/**
 * A page of results returned by a server-paginated list endpoint.
 *
 * @param <T>   the item type
 * @param items the items on this page
 * @param page  the zero-based page index
 * @param size  the page size
 * @param total the total number of items across all pages
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {
}
