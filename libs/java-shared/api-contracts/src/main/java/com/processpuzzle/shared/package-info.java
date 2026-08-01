/**
 * API types shared across features — {@code ErrorResponse}, {@code ImportResult} and friends,
 * generated from {@code shared-api.yaml}.
 *
 * <p>The only hand-written file in {@code api-contracts}: the other generated packages
 * ({@code com.processpuzzle.app.model}, {@code com.processpuzzle.rule.api}, …) are sub-packages of
 * the feature modules and therefore already belong to them, but {@code com.processpuzzle.shared}
 * stands alone and would otherwise be an undeclared module.
 *
 * <p>Declared {@link org.springframework.modulith.ApplicationModule.Type#OPEN} because it is a bag
 * of DTOs with no behaviour to encapsulate.
 */
@ApplicationModule(displayName = "Shared API Contracts", type = ApplicationModule.Type.OPEN)
package com.processpuzzle.shared;

import org.springframework.modulith.ApplicationModule;
