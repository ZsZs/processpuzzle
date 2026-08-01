/**
 * The storage abstraction the Store feature is written against, and the type it returns.
 *
 * <p>Exposed as the {@code storage} named interface so another feature can store and fetch objects
 * without knowing whether MinIO or Firebase Storage is behind it.
 */
@NamedInterface("storage")
package com.processpuzzle.store.usecases.outbound;

import org.springframework.modulith.NamedInterface;
