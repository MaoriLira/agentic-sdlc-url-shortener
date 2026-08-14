---
tags: [runbook, postgresql, redis, kafka, developer-operations]
---

# Infrastructure Connectivity Runbook

Related: [[../00-Executive-Summary]] · [[../Dashboards/04-Setup-and-Run]] · [[../Jira-Tickets/Epic-URL-800-Developer-Operations]]

**Ticket:** [[../Jira-Tickets/Epic-URL-800-Developer-Operations#URL-801 — Infrastructure connectivity & diagnostics runbook|URL-801]]

[[../Dashboards/04-Setup-and-Run]] tells you how to *start* the stack. This page tells you how
to *look inside* it once it's running — specific to this application's schemas, key
namespaces, and topics, not a general Postgres/Redis/Kafka tutorial.

> [!success] Every command below was run against a real local stack while writing this page
> Not copied from documentation or memory — commands were executed via `docker-compose exec`
> against this project's actual `docker-compose.yml`, and the example output shown (short
> codes, keys, offsets) is genuine output from that session, not fabricated.

Prerequisite for all three sections: `docker-compose up -d` from the repository root (see
[[../Dashboards/04-Setup-and-Run]]).

---

## PostgreSQL

**Connect via CLI:**

```bash
docker-compose exec postgres psql -U urlshortener -d urlshortener
```

**Connect via GUI** (TablePlus, DBeaver, pgAdmin, etc.):

| Field | Value |
|---|---|
| Host | `localhost` |
| Port | `5435` (not the default 5432 — see [[../Dashboards/04-Setup-and-Run]] for why) |
| Database | `urlshortener` |
| User / Password | `urlshortener` / `urlshortener` |

**Verify migrations ran** (should show all 5, `success = t`):

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```
```
 version |           description            | success |        installed_on
---------+----------------------------------+---------+----------------------------
 1       | init schema                      | t       | 2026-08-13 19:25:02.002213
 2       | analytics schema                 | t       | 2026-08-13 19:25:02.025159
 3       | short code sequence              | t       | 2026-08-13 19:25:02.037101
 4       | seed demo client                 | t       | 2026-08-13 19:25:02.043226
 5       | alias reuse partial unique index | t       | 2026-08-13 19:25:02.050468
```

**Confirm the schemas exist:**

```sql
\dn
```
```
   Name    |       Owner
-----------+-------------------
 analytics | urlshortener
 core      | urlshortener
 public    | pg_database_owner
```

**Diagnostic query — recent short URLs:**

```sql
SELECT short_code, long_url, status, created_at, expires_at
FROM core.url_mappings
ORDER BY created_at DESC
LIMIT 5;
```

**Diagnostic query — short URLs with their click counts** (the query you actually want when
debugging "why does this link show 0 clicks" — `LEFT JOIN` so unclicked links still show up):

```sql
SELECT m.short_code, m.status, s.total_clicks, s.last_clicked_at
FROM core.url_mappings m
LEFT JOIN analytics.click_summary s ON m.short_code = s.short_code
ORDER BY s.total_clicks DESC NULLS LAST
LIMIT 5;
```
```
 short_code | status  | total_clicks |        last_clicked_at
------------+---------+--------------+-------------------------------
 1000003    | DELETED |            2 | 2026-08-14 02:51:47.547193+00
 1000001    | ACTIVE  |            1 | 2026-08-14 02:40:35.841771+00
 1000000    | ACTIVE  |              |
```

If `total_clicks` is blank for a code you just clicked, give it a few seconds — aggregation is
async over Kafka (see the [[../Guardrails/Kafka-Idempotent-Aggregation-and-DLQ|Kafka guardrail]]),
not synchronous with the redirect.

---

## Redis

**Connect:**

```bash
docker-compose exec redis redis-cli
```
```
127.0.0.1:6379> ping
PONG
```

**The four key namespaces this application actually uses:**

| Prefix | What it is | TTL |
|---|---|---|
| `url:{shortCode}` | Cache-aside entry, longUrl | ~24h + jitter (`urlshortener.cache.ttl-seconds`) |
| `lock:{shortCode}` | Stampede-protection lock, very short-lived | 2s |
| `ratelimit:{scope}:{identifier}:{windowMinute}` | Fixed-window rate-limit counter | ~65s |
| `processed:{eventId}` | Kafka-consumer idempotency guard | 24h |

**Check keys** (`KEYS` is fine for local dev; it's O(N) and blocking, so use `SCAN` instead
against anything resembling production data):

```bash
docker-compose exec redis redis-cli KEYS 'url:*'
docker-compose exec redis redis-cli GET url:1000001
docker-compose exec redis redis-cli TTL url:1000001
```
```
url:1000001
https://example.com/v1.0.0-release-smoke-test
92250
```

**Verify a rate-limit counter mid-window:**

```bash
docker-compose exec redis redis-cli KEYS 'ratelimit:*'
docker-compose exec redis redis-cli GET 'ratelimit:redirect:0:0:0:0:0:0:0:1:29778861'
```
```
2
```
(Two redirects counted in this window from `::1` — curl over IPv6 loopback expands to that
form; the [[../Guardrails/Structured-Logging-and-Data-Masking|IP-masking guardrail]] masks the
last octet when this identifier is logged, not the Redis key itself.)

**Verify cache hit/miss behavior live** — `MONITOR` streams every command hitting Redis in
real time, which is the most direct way to see whether a redirect hit the cache or not (a hit
shows only a `GET`; a miss shows `GET`, then `SETNX`/`SET` as the cache-aside path populates
it). Cross-reference against the app's own `DEBUG`-level log line from
[[../Guardrails/Cache-Aside-Graceful-Degradation]] if you also have `logging.level.com.example.urlshortener=DEBUG` set.

```bash
docker-compose exec redis redis-cli MONITOR
# in another terminal: curl -i http://localhost:8080/1000001
```

Real capture from a single redirect (cache already warm, so no `SET` — just the rate-limit
counter, the cache read, and the async idempotency guard from the Kafka consumer catching up
a moment later):

```
"INCR" "ratelimit:redirect:0:0:0:0:0:0:0:1:29778861"
"GET" "url:1000001"
"SET" "processed:9c546b1c-f27e-4129-be58-f474d24af603" "1" "EX" "86400" "NX"
```

---

## Kafka

**List topics:**

```bash
docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```
```
__consumer_offsets
click-events
```

**Describe the topic** (confirms the 3-partition, replication-factor-1 layout decided in
[[../Jira-Tickets/Epic-URL-200-Async-Analytics#URL-203 — Message Broker Selection & Provisioning|URL-203]]):

```bash
docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic click-events
```
```
Topic: click-events  PartitionCount: 3  ReplicationFactor: 1
	Partition: 0  Leader: 1  Replicas: 1  Isr: 1
	Partition: 1  Leader: 1  Replicas: 1  Isr: 1
	Partition: 2  Leader: 1  Replicas: 1  Isr: 1
```

**Check consumer lag** — the single most useful Kafka diagnostic for this app. `LAG` should
sit at (or return quickly to) `0`; a persistently growing lag means `AnalyticsConsumerService`
is falling behind or stuck:

```bash
docker-compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group analytics-consumer
```
```
GROUP              TOPIC         PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
analytics-consumer click-events  2          6               6               0
```

**Watch messages flow live**, verifying async analytics end to end:

```bash
docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic click-events --from-beginning --max-messages 1
```

Real captured message (matches the
[[../Jira-Tickets/Epic-URL-200-Async-Analytics#URL-201 — Analytics Event Schema Definition|ClickEvent schema]]
exactly — `eventId` for idempotency, `geoRegion` reserved/unpopulated per
[[../Architecture-Decisions/ADR-12-Geo-Analytics-Scope|ADR-12]]):

```json
{"eventId":"085b3001-3e75-4b2d-974b-1d6054b3568c","schemaVersion":1,"shortCode":"1000003","timestamp":1786675898.918719000,"referrer":null,"userAgent":"insomnia/13.1.0","geoRegion":null}
```

Drop `--from-beginning` and `--max-messages 1` to tail new messages live while you click a
redirect in another terminal — the fastest way to confirm the whole async pipeline
(redirect → publish → consume → aggregate) is actually working end to end.

---

## See also

- [[../Dashboards/04-Setup-and-Run]] — starting the stack in the first place
- [[../Guardrails/Cache-Aside-Graceful-Degradation]] — what happens when Redis *isn't*
  reachable (the app keeps working; this runbook assumes the happy path)
- [[../Guardrails/Kafka-Idempotent-Aggregation-and-DLQ]] — the `processed:*` keys and DLQ
  table this runbook's Redis/Kafka sections reference
