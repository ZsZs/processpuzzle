package com.processpuzzle.calculation.port;

/**
 * The type of an entity attribute as FEEL needs to know it: a {@code NUMBER} becomes a decimal, a {@code DATE} a
 * date and a {@code DATE_TIME} a date-time; everything else is passed as stored.
 */
public enum AttributeType {
    TEXT, NUMBER, BOOLEAN, DATE, DATE_TIME, OTHER
}
