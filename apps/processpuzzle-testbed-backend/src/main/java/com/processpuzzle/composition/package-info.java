/**
 * Where the feature modules are introduced to each other.
 *
 * <p>Every feature library states what it needs from outside itself as an outbound port and names no
 * other feature. That is what lets each one build, test and deploy alone, and what will let them
 * become separate services without a redesign. Somebody still has to say which implementation
 * answers which port, and that somebody is the application: it is the only component that knows the
 * full set of features it ships.
 *
 * <p>Adapters here are the seam. Today they are direct in-process calls, because everything runs in
 * one JVM and a network hop between two Modulith modules would buy nothing. When a module moves out,
 * the adapter is replaced with one that speaks HTTP and no feature library changes.
 */
package com.processpuzzle.composition;
