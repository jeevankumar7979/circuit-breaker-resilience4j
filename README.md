# Resilience4j Circuit Breaker: Production Interview Guide

This guide uses the `order-service` and `inventory-service` in this repository as its running example. The order service calls the inventory service before accepting an order, and a Resilience4j circuit breaker protects that remote call.

## Example in this repository

```text
Client -> order-service (:8081) -> inventory-service (:8082)
                                  |
                                  +-- failure/slow response can affect order creation
```

The order service creates a named `inventory` circuit breaker. Its current configuration is in `order-service/src/main/java/com/jeevankumar/orderservice/InventoryClientConfig.java`:

| Setting | Current example value | Meaning |
|---|---:|---|
| Sliding window | Count-based, 4 calls | Keep outcomes for the latest four calls in the window |
| Minimum calls | 3 | Do not calculate a failure rate until at least three calls are recorded |
| Failure-rate threshold | 50% | Open the breaker when the measured failure rate reaches this threshold |
| Open-state wait | 10 seconds | Reject calls for this duration before allowing a recovery probe |
| Half-open permitted calls | 2 | Let two calls test whether the dependency recovered |
| Automatic half-open transition | Enabled | Move from open to half-open after the wait without requiring a new request |
| Recorded exception | `RestClientException` | Count matching client exceptions as failed calls |

`OrderService.createOrder` executes the inventory lookup through the breaker. If the breaker is open (`CallNotPermittedException`) or the HTTP client fails (`RestClientException`), this example persists an order with status `INVENTORY_UNAVAILABLE`. That is a deliberate business outcome for this demo, not a universal fallback policy.

Useful endpoints exposed by the order service include:

- `POST /api/v1/orders` — create an order
- `GET /api/v1/orders/{orderId}` — retrieve an order
- `/actuator/health`
- `/actuator/circuitbreakers`
- `/actuator/circuitbreaker-events`

## Interview questions and detailed answers

### 1. What problem does a circuit breaker solve?

A circuit breaker prevents a failing or unresponsive dependency from consuming resources and causing failure to spread through the calling service. It tracks calls to a dependency and temporarily rejects calls when configured failure conditions are met. This gives the dependency time to recover and lets the caller fail fast or use a safe alternative.

It is a resilience mechanism, not a fix for the underlying outage and not a substitute for timeouts, bounded concurrency, or good capacity planning.

### 2. Explain the CLOSED, OPEN, and HALF_OPEN states.

- **CLOSED:** Calls are allowed and their outcomes are measured.
- **OPEN:** Calls are rejected immediately with `CallNotPermittedException`; the remote call is not attempted.
- **HALF_OPEN:** A limited number of calls are allowed as recovery probes. If they succeed sufficiently, the breaker closes; if they fail sufficiently, it opens again.

The breaker can also be **DISABLED** or **FORCED_OPEN** for operational use. Those states bypass normal state-transition behavior in different ways; they should be configured intentionally and monitored.

### 3. When does this example open its circuit?

The example uses a count-based sliding window of four calls, requires at least three recorded calls, and uses a 50% failure-rate threshold. A failure-rate calculation is unavailable until the minimum number of calls has been reached. After that, the breaker opens when the recorded failure rate reaches the threshold. The exact sequence depends on which calls are included in the current window and which exceptions the predicate records.

In production, I would tune the window, minimum call count, threshold, and open duration from dependency latency, traffic volume, SLOs, and failure patterns rather than copy these demo values.

### 4. What is the difference between count-based and time-based sliding windows?

A count-based window evaluates the most recent fixed number of calls. A time-based window evaluates calls observed over a fixed time period. Count-based windows are useful when a predictable number of samples matters; time-based windows can better reflect recent behavior when traffic varies. With low traffic, either approach needs care: the breaker may take a long time to collect enough samples, or a small number of samples may be misleading.

### 5. What does `minimumNumberOfCalls` do?

It prevents the breaker from making a decision from too few samples. For example, a single failed request should not necessarily open a breaker with a 50% threshold. Until the minimum is reached, the failure rate is not evaluated. A value that is too high delays protection; one that is too low can make the breaker react to noise.

### 6. Does a circuit breaker provide a timeout?

No. A circuit breaker does not, by itself, stop a remote request that is stuck. Configure connection and response/read timeouts in the HTTP client so calls have a bounded duration. A slow-call threshold can make slow completed calls contribute to breaker decisions, but it does not cancel or time out the request.

This example builds a `RestClient` without explicit request-factory timeouts, so a production version should configure and test bounded connection and response timeouts for the chosen HTTP client.

### 7. How is a slow-call rate different from a failure rate?

Failure rate measures calls that ended in configured failures. Slow-call rate measures calls whose duration exceeded the configured slow-call duration, whether or not they ultimately succeeded. A breaker can be configured to open when either rate reaches its threshold. Slow-call detection is useful when a dependency is technically returning success but its latency is already harming the caller.

### 8. Should retries be combined with a circuit breaker?

Sometimes, but retry must be bounded and coordinated with the breaker. Retry only transient, retryable failures; use a small attempt limit, backoff, and jitter; and ensure the total retry budget fits inside the caller's deadline. Retries increase dependency load during an incident, so uncontrolled retries can worsen an outage. Decide decorator order deliberately because it changes what the breaker measures: individual attempts or the overall retried operation.

### 9. Does the breaker replace bulkheads?

No. A breaker controls whether calls are permitted based on recent outcomes. A bulkhead limits concurrent work or isolates pools so one slow dependency cannot consume all threads or connections. In production, use both where appropriate, along with client timeouts and an end-to-end request deadline.

### 10. Which exceptions should count as failures?

Count failures that indicate the dependency is unhealthy or unavailable, such as connection failures, timeouts, and selected server-side responses. Ignore or separately handle expected business outcomes, validation errors, and caller-caused errors. For example, an inventory `404` might mean “unknown SKU,” not “inventory service is down”; counting every HTTP error as a breaker failure could open the circuit incorrectly.

The example records every `RestClientException`. Review the HTTP client's exception mapping and refine the predicate in production so expected 4xx responses do not count as dependency failures. Also configure ignored exceptions for outcomes that should neither increase the failure rate nor be treated as a recorded success.

### 11. What should a fallback do?

A fallback must preserve business correctness, not just return a success-shaped response. Good options include returning explicitly stale/read-only data, queuing work for later, rejecting the operation with a clear retryable response, or returning a domain-specific “dependency unavailable” result. The choice depends on the operation and consistency requirements.

For order creation, accepting an order without confirming stock can oversell inventory. This example instead records `INVENTORY_UNAVAILABLE`; in a real system, clarify whether that means the order is rejected, pending, or needs reconciliation. Do not swallow the original exception or report success if the operation did not complete.

### 12. How should the system behave in HALF_OPEN?

Allow only a small, bounded number of probe calls. A successful probe indicates recovery; a failed or still-slow probe should reopen the breaker. Probe volume should not overload a dependency that is only beginning to recover. Consider whether the operation is safe to repeat and whether probes can cause side effects.

### 13. What does automatic transition from OPEN to HALF_OPEN change?

When enabled, the breaker transitions after its open-state wait elapses even if no request arrives. When disabled, it generally remains open until a later call triggers the transition check. Automatic transition can make state and metrics change on schedule, but it does not itself send a health check; the actual permitted probe calls still come from application traffic.

### 14. Is a Resilience4j circuit breaker shared across service instances?

No. A normal Resilience4j breaker is in-memory and local to the application process. Each replica maintains its own state and statistics, so replicas can be in different states. Do not assume one instance opening the breaker opens it across the cluster. If coordinated global state is truly required, evaluate the added complexity and failure modes; commonly, local breakers plus load balancing and dependency-level protection are preferable.

### 15. How do you monitor a circuit breaker in production?

Expose and monitor state, calls permitted/not permitted, successful and failed calls, slow calls, and state-transition events. Correlate these metrics with dependency latency/error rate, caller latency, timeout counts, retries, thread/connection pool saturation, and business outcomes. Alert on sustained open states or rising rejected-call rates, not merely on a brief state transition.

Use bounded metric labels (for example, a fixed breaker name such as `inventory`); do not add customer IDs, order IDs, or other unbounded values as metric tags. Protect actuator endpoints with appropriate network and access controls.

### 16. How would you troubleshoot a breaker that is always OPEN?

Check the breaker state-transition events and metrics, then verify:

1. Which exceptions and slow calls are being recorded or ignored?
2. Is the dependency actually unhealthy, timing out, or returning errors?
3. Are expected 4xx/business responses incorrectly counted as failures?
4. Are the threshold, window, and minimum sample count appropriate for actual traffic?
5. Is the dependency recovering before half-open probes run, and can those probes succeed?
6. Do retries or a missing timeout amplify failures?

Do not simply raise the threshold or force the breaker closed before understanding the cause; that can reintroduce cascading failures.

### 17. How would you handle a breaker that never opens despite an outage?

Verify that the protected call actually uses the intended breaker instance and that the instance name/configuration is correct. Check whether exceptions are ignored or not recorded, whether enough calls have reached the minimum sample count, whether low traffic is delaying evaluation, and whether timeouts are configured so failed calls eventually complete. Confirm that the observed dependency failure occurs inside the protected operation.

### 18. What happens to an HTTP 500 or 404 in this example?

The behavior depends on how Spring's `RestClient` maps the response to an exception and on the configured exception predicate. The predicate here accepts `RestClientException`, so matching client exceptions are counted. This is intentionally broad for a demo. In production, test actual status handling and distinguish dependency-health failures (often selected 5xx/timeouts) from normal domain responses (often some 4xx).

### 19. How do you test circuit-breaker behavior?

Use deterministic tests with a controlled fake/stub dependency or a directly constructed breaker. Verify the transition after the configured minimum samples and threshold, that an open breaker rejects calls without invoking the remote client, and that half-open permits only the configured number of probes. Also test ignored exceptions, fallback/business behavior, recovery, and timeout handling. Avoid tests that depend on real sleeps where the library's controllable clock or other deterministic mechanism is available.

### 20. How do you configure different breakers for different dependencies?

Give each dependency its own named breaker and tune it to that dependency's latency, traffic, error semantics, and business criticality. Avoid sharing one breaker across unrelated downstreams: one failing dependency could then block healthy dependencies. Keep configuration centralized and externalizable where operational teams need to tune it, and verify that the runtime uses the intended instance and configuration.

### 21. Should circuit-breaker configuration be the same in every environment?

Not necessarily. Production values should be driven by measured traffic, latency distributions, dependency SLOs, and acceptable error budgets; local/test environments often need simpler or deterministic settings. Keep configuration versioned, review changes, and make runtime overrides observable. Avoid environment-specific differences that make a production-only failure impossible to reproduce.

### 22. What is the difference between fail-fast and fallback?

Fail-fast means not attempting a call when the breaker is open and returning an error/result promptly. A fallback is the application's explicit alternative behavior after a failure or rejection. The breaker provides the first behavior; it does not define a safe fallback. A fallback should be observable and should not hide a dependency incident.

### 23. How do you prevent duplicate orders when retries or recovery are involved?

Use idempotency at the business/API boundary, for example a client-supplied idempotency key with a uniqueness constraint and persisted result. A circuit breaker does not guarantee exactly-once delivery. If the caller times out after the downstream accepted a request, retrying may duplicate work unless the operation is idempotent or has a reconciliation strategy.

### 24. What are the most common production mistakes?

- Treating the breaker as a substitute for client timeouts.
- Retrying aggressively during an outage.
- Counting business/validation errors as dependency failures.
- Returning success from a fallback when the business operation did not succeed.
- Sharing a breaker across unrelated dependencies.
- Ignoring per-instance state and assuming it is cluster-wide.
- Choosing thresholds without understanding real traffic and latency.
- Exposing actuator/metrics endpoints without access controls.
- Not testing the open and half-open paths.

## A concise interview answer

> “I put a circuit breaker around each remote dependency call, configure explicit client timeouts, and count only failures that indicate dependency health problems. The breaker uses a sliding window and minimum sample count to avoid decisions based on too little data; after the failure or slow-call threshold is reached it opens and fails fast. After a cool-down it allows a small number of half-open probes. I combine it with bounded retries and bulkheads only where appropriate, choose fallbacks based on business consistency, and monitor breaker events alongside latency, errors, and business outcomes. Resilience4j state is local to each application instance, so I do not treat it as a distributed circuit breaker.”

## Repository-specific notes

- The order service uses Spring Cloud CircuitBreaker with Resilience4j on Java 21 and Spring Boot 4.1.1, and constructs the Resilience4j registry/breaker programmatically.
- The inventory URL is configurable through `INVENTORY_BASE_URL` (defaults to `http://localhost:8082`).
- The breaker and event actuator endpoints are exposed in the order service configuration. Keep operational endpoints access-controlled in deployed environments.
- The example is for learning and interview discussion. Before production use, add explicit HTTP timeouts, refine exception classification, validate fallback semantics, and add tests for state transitions and outage behavior.
