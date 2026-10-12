# ProcessPuzzle Performance & Stress Test Strategy

Oct 11, 2026 · Zsolt

## Purpose and scope

These tests find out how much load the first production deployment of ProcessPuzzle can carry, what breaks first, and how to scale it. They run against a dedicated, temporary copy of the platform, never against staging.

- **In scope:** the backend (processpuzzle-store, entity, state and workflow services), Keycloak authentication, PostgreSQL, MinIO file storage, rate limiting and abuse resistance.
- **Out of scope for now:** Angular rendering performance, volumetric DDoS, multi-region setups.

## Goals

The tests answer five questions, in this order of priority:

1. **Users:** how does a growing number of users change memory, CPU and disk usage?
2. **Data:** how does growing data volume (database rows and files) change memory, CPU and disk usage?
3. **Crash behaviour:** can the system crash, how does it fail, and does it recover on its own?
4. **DoS:** is there an effective countermeasure against denial-of-service attacks?
5. **Scaling:** how should production be sized and scaled, given roughly thousands of users in the first six months?

## Deployment architecture

![Deployment architecture: 2 temporary VPS, 6 components](deployment-architecture.svg)

The system under test is the repository's own Docker Compose topology on a temporary VPS, with a performance overlay on top; k6 runs from a second temporary VPS, so staging is never touched.

- Topology as deployed: nginx serves the Angular app and proxies `/api` to the Spring Boot backend (50 MB body limit); Keycloak, PostgreSQL 18, MinIO, a json-server mock and a mail sink complete it. Stage adds Coolify's Traefik in front, which the system under test does not replicate.
- Every service carries a memory cap tuned for the 3.8 GiB stage host. The overlay keeps those caps by default (the "as deployed" run) and makes them tunable (the "scaled" run).
- All services share the VPS's CPU, RAM and disk, so results describe the single-VPS deployment as a whole, and per-container metrics show which service uses what.
- Prometheus and Grafana run on the same VPS behind an opt-in profile and cost roughly 0.6-1 GB of RAM; a run without them measures that overhead. Moving them to the generator is a later option.
- Size the system under test like the intended first production machine, and repeat the key runs one size up.
- Put the load generator in the same region as the system under test, never on the same host.

## Load and capacity assumptions

The estimate is thousands of users in the first six months, each uploading dozens of files of 1-10 MB. That puts file storage between tens of GB and several TB, so storage is likely the first real limit.

| Scenario (illustrative) | Registered users | Files per user | File size | File volume |
| --- | --- | --- | --- | --- |
| Low | 3,000 | 24 | 1 MB | about 70 GB |
| Mid | 5,000 | 30 | 5 MB | about 750 GB |
| High | 10,000 | 60 | 10 MB | about 6 TB |

Registered users are not concurrent users. Working assumption: 2-10% are active at the same time at peak, which is about 100-500 concurrent users for 5,000 registered. Registration waves after a launch or campaign are the burst case to plan for.

## Workload model

The load generator is k6. Scripts replay realistic user journeys; the starting point is the Playwright flows, whose API traffic can be recorded as HAR and converted to k6 scenarios. The target is nginx at /api, the path browsers use.

| Scenario | What it exercises | Notes |
| --- | --- | --- |
| Browse and CRUD | Entity reads and writes, EAV/JSONB queries in PostgreSQL | Read/write ratio still to be defined |
| Workflow step | Workflow and state services, task lists | Journey mix still to be defined |
| File upload | processpuzzle-store to MinIO | Sizes of 1, 5 and 10 MB; multipart POST through nginx; the backend buffers the file in heap, twice for images with thumbnails |
| File download | MinIO through the backend, bandwidth | Mixed file sizes |
| Login and token refresh | Keycloak, password hashing | Separate test; API runs need no token while require-authentication is false (stage today); with it true, tokens are pre-generated |
| Registration burst | Auth and onboarding under a sudden wave | Spike scenario |

## Test phases

Each phase changes one variable, so effects of users, data and failure modes stay separable.

1. **Baseline:** one journey at low load; validates scripts, monitoring and the seeded data.
2. **User steps:** ramp through 10, 50, 100, 250, 500 and 1,000 virtual users, holding each step 5-10 minutes. Result: CPU, memory and disk per user.
3. **Data growth:** keep the user load fixed and seed 10%, 50% and 100% of the six-month projection, rerunning the same test each time. Result: effect of data volume alone.
4. **Uploads:** concurrent uploads of 1, 5 and 10 MB, plus downloads. Result: throughput, heap per upload, disk I/O.
5. **Stress:** ramp until failure. Result: breaking point, failure mode, and whether it recovers.
6. **Spike:** a sudden 10x jump in load. Result: how fast it degrades and returns.
7. **Soak:** 4-8 hours at about 70% of the capacity found. Result: leaks (heap, connections, threads), log and temp-file growth.
8. **Abuse and DoS:** application-level attacks, first without and then with mitigations (see DoS section).
9. **Scale step:** repeat the user steps on one machine size larger and, if feasible, with two backend instances. Result: scaling factor.

**Run configurations.** The phases are repeated per configuration, starting with "as deployed": stage memory caps (finds where stage breaks), "scaled" (raised caps, gives the per-user and per-file figures for sizing), "production security" (require-authentication true, a token per virtual user) and "monitoring off" (what the monitoring itself costs).

## Test data strategy

Playwright tests created the test data so far; the k6 project now carries a seeder and a file generator (a skeleton, not yet run). That is too slow for bulk volumes, so the plan splits the work:

- **Database rows:** seed at the full projected volume, either through the REST API from a k6 setup script (realistic) or with SQL `generate_series` (fast). Query cost does not grow linearly with data, so this volume cannot be extrapolated.
- **Files:** generate random files of 1-10 MB. Test at about 50-100 GB, measure the cost per file in MinIO and extrapolate. File storage grows roughly linearly, so filling several hundred GB is not needed.
- **Playwright:** keep it for functional checks. Run the suite against the loaded system to confirm results are still correct, and use its recorded traffic as the basis of the k6 scripts.
- **Test users:** generated accounts in Keycloak, with tokens pre-generated for the main load runs.

## Observability and metrics

On a single VPS all components share CPU, RAM and disk, so every component needs its own metrics. The stack is Prometheus and Grafana with the exporters below, all services run as containers, and the stack lives in tools/docker/perf.

| Component | Metrics | Source |
| --- | --- | --- |
| Host | CPU, RAM, disk I/O, disk space, network | node_exporter |
| Containers | CPU, RAM, I/O per service | cAdvisor |
| Backend and store | Heap, GC, threads, Tomcat and Hikari pools, upload memory | JMX exporter agent (the backend has no Prometheus endpoint) |
| PostgreSQL | Connections, cache hit rate, slow queries, database size | postgres exporter, pg_stat_statements |
| Keycloak | Login latency, JVM heap, CPU | JVM metrics, Keycloak metrics |
| MinIO | Request latency, disk usage, throughput | MinIO Prometheus metrics |
| Load generator | Request rate, latency, errors, its own CPU and network | k6 remote write; the generator host needs its own node_exporter |

## Success criteria

Thresholds are agreed before the first run, so results are judged against fixed targets. The values below are the agreed ones; a change needs a note here.

| Criterion | Proposed threshold |
| --- | --- |
| API latency (excluding uploads) | p95 under 500 ms |
| Error rate | Under 1% |
| Disk usage | Under 80% |
| Process health | No out-of-memory kills during load |
| Soak stability | No upward trend in heap, connections or threads |
| Recovery after stress | Returns to normal without manual intervention |
| Uploads | Throughput target to be defined |
| Load generator | Under 70% CPU, so it is not the bottleneck |

## DoS and abuse testing

Application-level abuse can be tested; volumetric DDoS cannot, and its protection comes from upstream.

**Testable against the temporary system:**

- Login floods, and whether Keycloak brute-force detection reacts
- Rate limits per IP and per user: the repository's nginx config has none, so the first run is a baseline without mitigation
- Slow clients (slowloris) holding request threads, and slow or oversized uploads
- Expensive endpoints called in bulk
- Request size limits and timeouts

**Method:** run each attack without mitigations, then with them enabled, and compare latency, errors and resource use.

**Not testable from a single VPS:** volumetric DDoS. Mitigation is provider-level DDoS filtering or a CDN/WAF in front. Before any heavy traffic, check the acceptable-use policies of both providers, and aim attacks only at the temporary system, with its firewall allowing only the generator's IP.

## Hypotheses and risks

These are expectations to confirm or reject, not findings.

| Hypothesis | How to verify |
| --- | --- |
| Confirmed by the code, to be measured: the store reads each upload into a byte[] (twice for images with thumbnails, plus decoded pixels), so the roughly 500 MiB stage heap is exhausted by a handful of concurrent 10 MB images | Upload phase with heap and GC metrics; compare streaming |
| Slow uploads hold request threads and starve normal API calls | Slow-client test during a normal load |
| Disk I/O is the first bottleneck, because PostgreSQL WAL and MinIO writes share one disk | Per-container I/O in cAdvisor, `iostat` |
| RAM is contended by two JVMs, the PostgreSQL cache and MinIO | Per-container memory over the user steps |
| Query time degrades non-linearly with EAV/JSONB data volume | Data-growth phase with `pg_stat_statements` |
| Keycloak logins are CPU-heavy and limit login bursts | Separate login test and registration burst |
| Presigned uploads straight to MinIO relieve the backend (supported by the store, used today only by the AI media flow) | Compare the mediated path with a presigned prototype |
| Stage's memory caps, not the hardware, set the first limit | As-deployed run against scaled run |
| nginx buffers each upload body (up to 50 MB) before the backend sees it, adding latency and disk I/O | Upload phase: nginx and disk metrics against direct backend uploads |

## From results to production scaling

Production starts on one VPS and is split later, because of cost. The tests turn into sizing figures:

- **Per-load figures:** MB of RAM and CPU per 100 concurrent users, GB of disk per user, database growth per user, upload throughput.
- **Sizing rule:** extrapolate to the target load and add a safety margin of about 30-50%.
- **Validation:** one final run at 2x the target load.

**Expected split order (hypothesis):**

1. File storage out, to separate S3-compatible storage or direct presigned uploads
2. PostgreSQL to its own host
3. A second backend instance behind a load balancer

Vertical scaling comes first because it is cheapest. A second backend instance needs stateless sessions, externalised Spring Modulith events, safe schedulers and no instance-local caches; the scale-out step of the test phases checks this.

## Safety and cost controls

Both machines are temporary and billed hourly; Hetzner is the chosen provider for the temporary machines. Staging at netcup is never a target.

- **Delete, don't just stop:** a powered-off server is still billed. Delete both machines after each test day, after exporting the results.
- **Safety net:** an auto-shutdown in the cloud-init script (`shutdown -h +N`) and a billing alert, so a forgotten machine cannot run for days.
- **Firewall:** the system under test accepts traffic only from the load generator's IP and from the operator. The stack publishes only ports 9090, 7070 and 7000 publicly; everything else stays on loopback.
- **No real integrations:** no real email, payments or production Keycloak users.
- **Gradual ramps:** k6 thresholds with `abortOnFail` stop a run that exceeds error or latency limits.
- **Generator health:** watch its CPU and network, so it never becomes the bottleneck.

## Open questions and next steps

**Open questions**

- Where does the system under test run (provider, region), and which size matches the planned first production machine?
- Which journey mix, read/write ratio and peak concurrency should the scripts assume?
- Is cross-tenant impact (one organisation uploading heavily) part of the scope?
- Should production run with require-authentication true from day one? The tests then need a token per virtual user.
- Does Coolify's Traefik add rate limits or body limits in front of nginx on stage and prod?
- Should generic file uploads move to presigned URLs if the buffering limit proves too low?
- How are the load generator's own CPU and network monitored (node_exporter on that host)?

**Next technical steps**

- [x] Compose file for the system under test with the monitoring stack
- [x] Seeder for the database and a generator for test files
- [x] k6 scenarios from the recorded Playwright traffic
- [x] Agree success thresholds
- [ ] Baseline run and review of the dashboards
- [ ] Start the perf stack on a temporary VPS and check that all Prometheus targets are up
- [ ] Commit and push the perf stack (tools/docker/perf, branch perf/test-stack)
- [ ] First seeder run and k6 smoke test against the system under test
