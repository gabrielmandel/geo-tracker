export interface ApiDefinition {
  readonly baseUrl: string;
  /** Attach the session bearer token to requests for this API. */
  readonly auth?: boolean;
  /** Ships in the browser bundle, so only use public or domain-restricted keys here. */
  readonly apiKey?: { readonly header: string; readonly value: string };
}
