package com.processpuzzle.rule.adapter.inbound.rsql;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortParserTest {

    @Test
    void nullOrBlankInput_isUnsorted() {
        assertThat(SortParser.parse(null).isSorted()).isFalse();
        assertThat(SortParser.parse("").isSorted()).isFalse();
        assertThat(SortParser.parse("   ").isSorted()).isFalse();
    }

    @Test
    void singleProperty_defaultsToAsc() {
        Sort sort = SortParser.parse("name");
        assertThat(sort).containsExactly(Sort.Order.asc("name"));
    }

    @Test
    void singlePropertyWithDirection() {
        assertThat(SortParser.parse("name,asc")).containsExactly(Sort.Order.asc("name"));
        assertThat(SortParser.parse("name,desc")).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void directionIsCaseInsensitive() {
        assertThat(SortParser.parse("name,DESC")).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void multipleCriteria() {
        Sort sort = SortParser.parse("name,asc,createdAt,desc");
        assertThat(sort).containsExactly(Sort.Order.asc("name"), Sort.Order.desc("createdAt"));
    }

    @Test
    void mixedWithAndWithoutDirection() {
        Sort sort = SortParser.parse("name,createdAt,desc");
        assertThat(sort).containsExactly(Sort.Order.asc("name"), Sort.Order.desc("createdAt"));
    }

    @Test
    void unknownDirectionToken_isTreatedAsAnotherProperty() {
        // Aligns with Spring's forgiving PageableArgumentResolver — anything that isn't
        // asc/desc is just another property, so JPA can decide whether it's a real column.
        Sort sort = SortParser.parse("name,sideways");
        assertThat(sort).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("sideways"));
    }

    @Test
    void emptyPropertyBetweenCommas_rejected() {
        assertThatThrownBy(() -> SortParser.parse("name,,asc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whitespaceAroundTokens_isTrimmed() {
        assertThat(SortParser.parse(" name , asc "))
                .containsExactly(Sort.Order.asc("name"));
    }

    @Test
    void returnedSortContainsExpectedOrders() {
        Sort sort = SortParser.parse("severity,desc,name,asc");
        List<Sort.Order> orders = sort.stream().toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("severity");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("name");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
