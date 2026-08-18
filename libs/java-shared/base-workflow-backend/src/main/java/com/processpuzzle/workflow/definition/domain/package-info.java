/**
 * JPA entities, repositories, and domain services for process/tool definitions. Not exposed as a
 * Spring Modulith named interface — nothing outside this module needs to reach into definition
 * internals; the execution layer reads definitions through
 * {@link com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository} directly since
 * both packages live in the same module.
 */
package com.processpuzzle.workflow.definition.domain;
