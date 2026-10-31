import type { components } from './openapi';

export type Schemas = components['schemas'];

// The API always sends every field (null when empty); the generator marks nullable fields as optional.
export type ApiModel<K extends keyof Schemas> = { [P in keyof Schemas[K]]-?: Exclude<Schemas[K][P], undefined> };
