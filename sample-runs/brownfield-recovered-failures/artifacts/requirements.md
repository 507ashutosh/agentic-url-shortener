# requirements

**Summary:** Optional per-link expiry in whole days; expired links stop redirecting and return 410 Gone

## Decisions and assumptions

- **ASSUMPTION:** N is chosen per link by the caller when shortening, not a single global setting
- **ASSUMPTION:** Links created without an expiry never expire, so existing clients and existing links behave exactly as today
- **ASSUMPTION:** N is a whole number of days between 1 and 3650 (10 years)
- **ASSUMPTION:** An expired link answers 410 Gone (the resource existed but is gone), not 404
- **ASSUMPTION:** Stats stay readable after expiry so owners can still see historical clicks
- **QUESTION:** Should expired links be deleted after a retention period, or kept forever for stats?
- **QUESTION:** Should the service enforce a default maximum expiry for all new links?

## Report

## Intent

Let a caller limit how long a short link works. After the chosen number of days the link must stop redirecting.

## Scope

In scope:
- An optional expiry, in days, when creating a link
- Redirect behaviour for expired links
- Exposing the expiry time in API responses

Out of scope:
- Deleting or archiving expired links
- Changing the expiry of an existing link
- Expiry by click count

## Acceptance criteria

1. `POST /shorten` accepts an optional integer `expiresInDays`.
2. With `expiresInDays = N`, the link's expiry time is creation time + N days, and it is returned as `expiresAt`.
3. Without `expiresInDays`, `expiresAt` is null and the link never expires.
4. `expiresInDays` outside 1..3650 is rejected with 400 and a clear message.
5. Before the expiry time, `GET /{code}` redirects (302) and counts the click as today.
6. At or after the expiry time, `GET /{code}` returns 410 Gone and does not count a click.
7. `GET /stats/{code}` still works after expiry and shows `expiresAt` and whether the link has `expired`.
8. All existing tests keep passing.
