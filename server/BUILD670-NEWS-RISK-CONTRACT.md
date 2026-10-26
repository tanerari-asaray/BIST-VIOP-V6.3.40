# Reviewed news risk contract

GET /v1/bist/news-risk/ASELS?asOf=<decision epoch milliseconds>

Response: {"symbol":"ASELS","state":"CLEAR|CRITICAL|UNKNOWN","coverageThrough":1792500000000,"availableAt":1792500000000,"source":"review service identifier","verified":true}

All values above are schema examples, not market evidence. `availableAt` is when the coverage verdict was known; it must not exceed the requested decision timestamp. `coverageThrough` must cover that timestamp and must not exceed it by over 15 seconds. UNKNOWN, missing or unverified evidence is not permission to trade. Unknown publication time or after-close disclosure must not be inserted into an earlier decision. No CLEAR inference is made from an empty news list.

The gateway forwards this path to BORSA_NEWS_UPSTREAM_URL with the server-side NEWS token; it does not classify headlines or manufacture coverage. No provider has been connected or deployed by this source change.
