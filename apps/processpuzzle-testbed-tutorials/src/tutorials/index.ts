import type { Tutorial } from '../recorder/tutorial';
import { ENTITY_DESIGNER } from './entity-designer/entity-designer.tutorial';
import { DOCUMENTS } from './documents/documents.tutorial';
import { RULES } from './rules/rules.tutorial';
import { STATES_TUTORIAL } from './states/states.tutorial';
import { CALCULATIONS } from './calculations/calculations.tutorial';
import { WORKFLOWS } from './workflows/workflows.tutorial';
import { CHATS } from './chats/chats.tutorial';
import { APPLICATIONS } from './applications/applications.tutorial';
import { STARTERS } from './starters/starters.tutorial';
import { RELEASE } from './release/release.tutorial';

/** Every tutorial, in the order of the Designer Home cards. */
export const TUTORIALS: Tutorial[] = [ENTITY_DESIGNER, DOCUMENTS, RULES, STATES_TUTORIAL, CALCULATIONS, WORKFLOWS, CHATS, APPLICATIONS, STARTERS, RELEASE];
