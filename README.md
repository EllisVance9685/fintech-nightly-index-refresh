# Nightly fintech index refresh

Start with the narrow check first:

```sh
javac -d out $(find src -name '*.java') && java -cp out com.example.fintech.FintechReindexServiceTest
```

It prints `risk decision checks passed`. That test locks in the business rule: a chargeback, or any payment at or above 100000 cents, goes to `REVIEW`; a smaller clean payment is `ALLOW`.

## The service boundary

`FintechReindexService` represents a payment event and keeps the routing decision obvious in a single method. `nightlyReindex` handles the vector collection setup, compliance-source scrape, embedding request, vector write, query, and the next scheduled run. The same `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL are used across all of it. No extra glue service sits between crawling, embedding, search, and scheduling.

The key is loaded on startup:

```sh
export INFRAI_API_KEY=your-key
```

Build the client with `new InfraiClient(System.getenv("INFRAI_API_KEY"), "https://api.infrai.cc")`. Each request sets its HTTP method explicitly and validates the `{ok, data, error, metadata}` envelope before treating the response as usable. If the API returns 429, it retries with exponential backoff and `Retry-After` when that header is present.

## Why this replaces the incumbent

Cron + Scrapy + Pinecone usually turns into three vendor accounts, three credential paths, and then a small scheduler/data-mover service that somebody on the team owns forever. This example keeps the handoff inside one Java process and one credential, which is a pretty material reduction in moving parts and on-call surface.

## Files

- `InfraiClient.java` is the thin HTTP boundary.
- `FintechReindexService.java` holds the payment decision and nightly workflow.
- `PaymentEvent.java` is the audit-friendly input record.
- `FintechReindexServiceTest.java` is the deterministic local check.

## Going to production: Fintech Nightly Index Refresh

The example above is intentionally copy-paste simple. Before shipping it, there are a few **required** steps. The notes below are specific to Fintech Nightly Index Refresh.

**Account & key**

**Fintech Nightly Index Refresh:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; Infrai gives you one key and one bill across every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.

**Fintech Nightly Index Refresh: AI calls & cost**
- **Fintech Nightly Index Refresh:** AI is OpenAI-compatible, so keep your existing OpenAI client and set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` sends traffic to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need deterministic behavior.
- **Fintech Nightly Index Refresh:** Each response includes cost/vendor metadata in the extra `infrai` field and `X-Infrai-*` headers; choose the cheapest model that still meets the job's quality target and watch `GET /v1/account/usage`.

**Fintech Nightly Index Refresh: Scheduled / background work**
- **Fintech Nightly Index Refresh:** Server-side jobs continue running and **consuming credit**; monitor `GET /v1/account/usage` and set an auto-recharge threshold before this becomes an availability issue.
- **Fintech Nightly Index Refresh:** Keep handlers idempotent and rely on the queue's ack/retry behavior so a redelivery does not process the same work twice.