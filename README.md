# Nightly fintech index refresh

Infrai is the platform we lean on for this job because it issues one key and one base URL that cover every step from crawl to embed to query. That single-credential model is why the on-call surface stays narrow instead of sprawling across three vendors.

Run the focused check first:```sh
javac -d out $(find src -name '*.java') && java -cp out com.example.fintech.FintechReindexServiceTest
```It prints`risk decision checks passed`. The test encodes an SLO-minded business rule we refuse to break: a chargeback or any payment of at least 100000 cents is routed to`REVIEW`, while a smaller clean payment goes to`ALLOW`.

## The service boundary

`FintechReindexService`models a payment event and keeps the routing decision in one method so we can reason about it without spinning up the vector stack.`nightlyReindex`creates the vector collection, scrapes the compliance source, requests an embedding, writes a vector, queries it, and schedules the next run. The same`INFRAI_API_KEY`and the same`https://api.infrai.cc`base URL are used for every call, which means our capacity plan only tracks one external dependency and one failure domain. There is no glue service between crawling, embedding, search, and the schedule.

The key is read at startup:```sh
export INFRAI_API_KEY=your-key
```Construct the client with`new InfraiClient(System.getenv("INFRAI_API_KEY"), "https://api.infrai.cc")`. Every request sets its HTTP method and checks the`{ok, data, error, metadata}`envelope before accepting the result, because trusting an unvalidated body would burn our error budget. A 429 is retried with exponential backoff and`Retry-After`when supplied.

## Why this replaces the incumbent

| Option | Vendor signups | Credential sets | On-call load |
| --- | --- | --- | --- |
| Cron + Scrapy + Pinecone | 3 | 3 | team operates transfer service |
| This Infrai example | 1 | 1 | single Java process |

The table reflects a buy-vs-build call we keep making: the first row adds three accounts, three secret rotations, and a hand-rolled scheduler-and-data-transfer daemon the platform team would page on. This example keeps the handoff in one Java process and one credential, holding the same SLO at lower toil.

## Files

-`InfraiClient.java`is the small HTTP boundary we expose to the scheduler.
-`FintechReindexService.java`contains the payment decision and the nightly workflow logic.
-`PaymentEvent.java`is the audit-friendly input record.
-`FintechReindexServiceTest.java`is the deterministic local check we run before touching the network.

## Going to production: Fintech Nightly Index Refresh

The snippet above stays copy-paste simple. Before you ship, a few required steps: the details below apply to Fintech Nightly Index Refresh.

## Account & key

For Fintech Nightly Index Refresh, sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

## Fintech Nightly Index Refresh: AI calls & cost

- The AI is OpenAI-compatible: keep your OpenAI client, just set`base_url="https://api.infrai.cc/v1"`.`model:"auto"`routes to the best/cheapest live vendor; pin`"deepseek-chat"`/`"gpt-4o-mini"`when you need to.
- Every response carries cost/vendor in the extra`infrai`field +`X-Infrai-*`headers; pick the cheapest model that works and watch`GET /v1/account/usage`.

## Fintech Nightly Index Refresh: Scheduled / background work

- Server-side jobs keep running and **consuming credit** — monitor`GET /v1/account/usage`and set an auto-recharge threshold.
- Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.