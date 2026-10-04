package com.processpuzzle.ai.galleries;

import java.util.UUID;

/**
 * What the AI feature wants to hear about its subjects: that one changed, so that its gallery follows
 * its photos, or that one is gone, so that its gallery goes too. The composition root calls it from
 * the subject-owning feature's events; this module subscribes to nothing itself, since that would
 * name the feature.
 *
 * <p>Both calls are idempotent and cheap for a subject of a type without a recognition profile, so the
 * caller need not filter. Neither throws for a subject this module does not know.
 */
public interface SubjectGalleries {

    void subjectChanged(String orgKey, String entityName, UUID objectId);

    void subjectDeleted(String orgKey, String entityName, UUID objectId);
}
