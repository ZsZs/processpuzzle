package com.processpuzzle.orgadmin.usecases.outbound;

import java.util.List;

/**
 * One page of directory results.
 *
 * <p>Not a Spring {@code Page}: Keycloak's user search is offset-based and does <em>not</em> return a
 * total count — computing one would be a second, separate count call per page. So the total is
 * carried explicitly and may be an estimate, and this type exists rather than pretending to be a
 * repository page whose {@code getTotalPages()} would be wrong.
 *
 * @param totalElements best known total; the provider may not report an exact one
 */
public record DirectoryPage(List<DirectoryUser> content, long totalElements, int number, int size) {

    public DirectoryPage {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
