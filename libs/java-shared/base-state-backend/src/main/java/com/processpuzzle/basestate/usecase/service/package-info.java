/**
 * Internal services backing the use cases: resolving the {@link
 * com.processpuzzle.basestate.usecase.port.EntityObjectGateway} bean, resolving named guard/action
 * beans, and the state-machine engine itself. Not a named interface — nothing outside {@code
 * usecase} calls into this package directly.
 */
package com.processpuzzle.basestate.usecase.service;
