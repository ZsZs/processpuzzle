package com.processpuzzle.core.rsql;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

public final class SortParser {

    private SortParser() {
    }

    public static Sort parse(String order) {
        if (order == null || order.isBlank()) {
            return Sort.unsorted();
        }

        String[] tokens = order.split(",");
        List<Sort.Order> orders = new ArrayList<>();
        int i = 0;
        while (i < tokens.length) {
            String property = tokens[i].trim();
            if (property.isEmpty()) {
                throw new IllegalArgumentException("Invalid order: empty property in '" + order + "'");
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (i + 1 < tokens.length && isDirection(tokens[i + 1])) {
                direction = Sort.Direction.fromString(tokens[i + 1].trim());
                i += 2;
            } else {
                i += 1;
            }
            orders.add(new Sort.Order(direction, property));
        }
        return Sort.by(orders);
    }

    private static boolean isDirection(String token) {
        String t = token.trim().toLowerCase();
        return t.equals("asc") || t.equals("desc");
    }
}
