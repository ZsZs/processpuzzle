package com.processpuzzle.baseentity.definition.domain;

/**
 * Named display style of a DATE / DATE_TIME attribute. The frontend renders it with
 * Intl.DateTimeFormat in the active language; a null style falls back to the value kind's default.
 */
public record DateFormat(DateStyle dateStyle, TimeStyle timeStyle) {
}
