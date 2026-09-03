/**
 * The tenant itself: {@link com.processpuzzle.platformadmin.domain.Organization}, its
 * {@link com.processpuzzle.platformadmin.domain.OrganizationStatus} lifecycle, and the billing
 * records attached to it — {@link com.processpuzzle.platformadmin.domain.Plan},
 * {@link com.processpuzzle.platformadmin.domain.Subscription},
 * {@link com.processpuzzle.platformadmin.domain.UsageRecord} and
 * {@link com.processpuzzle.platformadmin.domain.Invoice}.
 *
 * <p>Exposed as the {@code domain} named interface. base-app needs to read an
 * {@code Organization} — its provisioning flow returns one, and its default-app loader keys off the
 * status — so the entity and the enum have to be reachable. The repositories are reachable as a
 * consequence of Java package visibility rather than by intent: consumers should go through
 * {@code platformadmin :: usecase}, which is where the guard checks live.
 */
@NamedInterface("domain")
package com.processpuzzle.platformadmin.domain;

import org.springframework.modulith.NamedInterface;
