import { failureKindOf } from '../domain/chat.service';

/** Why a topic could not be started, in the terms the panel and the topics list say it. */
export type TopicFailure = 'forbidden' | 'conflict' | 'other';

export function topicFailureOf(error: unknown): TopicFailure {
  const kind = failureKindOf(error);
  return kind === 'forbidden' || kind === 'conflict' ? kind : 'other';
}

/** The translation key that words a failure. */
export function topicFailureKey(failure: TopicFailure): string {
  switch (failure) {
    case 'forbidden':
      return 'base_chat.topics.create_forbidden';
    case 'conflict':
      return 'base_chat.topics.create_duplicate';
    default:
      return 'base_chat.topics.create_failed';
  }
}
