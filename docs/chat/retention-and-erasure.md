# Chat: retention and erasure

This page explains what the chat stores, for how long, and how personal data is removed. It is written so that the
person responsible for data protection can decide the policy without reading code; the last section lists the decisions
that are theirs.

## What the chat stores

| Data | Where | Personal? |
| --- | --- | --- |
| The text of every message | `chat_message.body` | Yes — people write about people |
| Who wrote it: the identity provider's user id, and the name as it was when they wrote | `chat_message.author_sub`, `author_display_name` | Yes |
| Who opened a conversation, and their name then | `chat_conversation.created_by`, `created_by_name` | Yes |
| Which conversations a user follows, and how far they have read | `chat_read_marker` | Yes (behaviour) |
| The chat definitions (who may read, write, start) | `chat_definition` | No |

The names are a snapshot taken from the sign-in token, because the platform cannot look a user up later. A user who
changes their name later still appears under the old one in old messages.

## How long it is kept

**By default, for ever.** Nothing is deleted unless a retention period is set, either on the chat definition or as the deployment default below.

### Retention (storage limitation)

Each chat definition has a **Retention Days** field (Designer → Chat). Set it, release, and:

- A conversation is **idle** when its newest message — or, if it has none, the moment it was opened — is older than
  that many days.
- A job runs every six hours (`base-chat.retention.interval`) and **deletes idle conversations whole**: the messages,
  the read markers and the conversation. There is no undo, and nothing is kept as an archive.
- The period counts from the **last message**, not from when the conversation closed: a conversation that is still
  being written in is never deleted, whatever its age; one that nobody has touched for the period goes, open or
  closed. A completed workflow run or a shipped order does not delete its chat by itself — the chat goes N days after
  its last message.
- A definition that sets no period takes the **deployment default**, `base-chat.retention.default-days` (set per stack, for
  example in the testbed's `application.yaml`). 0, the default, keeps such conversations for ever. A definition's own
  period always wins over the default.
- Only the **released** definition counts. A definition that is only a draft does not delete anything, and
  conversations whose definition is no longer released have no policy and stay until the organization is erased.
- Each definition has its own period; different chats can keep data for different times (for example 30 days for
  small talk, 7 years for a conversation that is part of a record).

Switch the job off with `base-chat.retention.enabled=false`. `chat.retention.conversations.deleted` counts what it
removed.

### Erasure on request (right to erasure)

`DELETE /organizations/{orgKey}/chat/users/{userSub}` erases one user's chat data:

- Their **messages stay in place** — the numbering and the other people's replies still make sense — but the author
  becomes `erased`, the name is dropped and the text becomes `[removed]`.
- The conversations they **opened** lose their creator.
- Their **read markers** are deleted, so they follow nothing.

The user themself may call it, and so may whoever administers the organization. It is always authenticated, even on a
stack that otherwise runs without authentication. There is **no user-deleted event** on the platform yet, so an
administrator who deletes an account must also call this; it is not done automatically. `chat.users.erased` counts the
calls. The call is logged without the user's id.

Erasing a **whole organization** deletes all of its chat data (definitions, conversations, messages, markers) together
with the rest of its data.

## What this does not cover

- **Backups.** Database backups keep the data until they expire; the retention of backups is an operations matter.
- **Other people's words about the user.** A message by someone else that names the erased user keeps its text.
  Erasure removes what the user *wrote*, not what is *about* them.
- **Moderation.** There is no way for an administrator to remove a single message (for example an insult) while keeping
  the rest. Erasing the author's data is the nearest tool.
- **Open screens.** A browser that has the conversation open keeps what it already received until it reloads.
- **Logs and metrics** hold no message text or user ids from chat.

## Decisions that belong to the data protection owner

The defaults above are assumptions, chosen to be safe to ship (nothing is deleted without being asked). Please confirm
or change:

1. **Is "idle for N days" the right clock?** The alternative is "N days after the conversation or its subject is
   closed" (the order is delivered, the run completed). It keeps active conversations safe too, but needs the platform
   to know when a subject was closed.
2. **Which period for which chat?** Customer-facing chats on records that are legally kept (orders, contracts) may have
   to be kept as long as the record; internal chatter should not be kept longer than it is useful.
3. **Is anonymizing enough for erasure,** or must the message be removed entirely? Anonymizing leaves the text of
   others' replies readable; removing a message leaves a gap in the numbering.
4. **Who may trigger erasure?** Today: the user themself and the organization's administrators.
5. **Should deleting an account erase its chat data automatically?** This needs a "user deleted" event from the
   identity side.
6. **Do you need moderation** (an administrator removes one message, with an audit trail)?
