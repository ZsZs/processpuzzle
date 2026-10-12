# Performance-test stack

The system under test (SUT) for the performance and stress tests: the CI/local topology plus
tunable memory caps, PostgreSQL statement statistics, a JMX agent on the backend, and an opt-in
monitoring stack. No application image changes and no Coolify involvement; it runs on a temporary
VPS next to a separate load-generator VPS.

```
perf-stack.sh                 init / up / down / status / stats / logs / check / config
docker-compose-perf.yaml      overlay, passed LAST after infrastructure, apps and apps-local
prometheus/prometheus.yml     scrape jobs: node, cadvisor, backend, postgres, minio, keycloak
jmx/backend.yml               Tomcat and Hikari rules for the JMX exporter agent
grafana/                      datasource, dashboard provider, one dashboard (38 panels)
```

## Run it

On the SUT VPS (Docker with Compose v2, this repository cloned, outbound access to GHCR and Maven
Central; log in to GHCR first if the images are private):

```bash
tools/docker/perf/perf-stack.sh init <public-ip>    # writes .env.perf with random secrets (git-ignored)
tools/docker/perf/perf-stack.sh check               # validates the merged compose files
tools/docker/perf/perf-stack.sh up                  # PERF_MONITORING=off to start without monitoring
```

- **Firewall.** `init` publishes four ports on all interfaces, because the load generator has to
  reach them: 9090 (frontend and `/api`), 7070 (Keycloak), 7000 (MinIO S3, which presigned URLs
  name). Allow them only from the generator's IP and yours. Everything else is on loopback.
- **Grafana and Prometheus** are on loopback too: `ssh -L 3001:127.0.0.1:3001 <sut>` for Grafana. For
  k6 on another host, tunnel 9099 the same way, or publish it and firewall it.
- **k6** pushes its client-side metrics into the same Prometheus, so server and client share one time
  axis: `K6_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:9099/api/v1/write
  K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)" k6 run -o experimental-prometheus-rw script.js`
  (the output name can differ between k6 versions).

## Runs to make

| Run | How |
| --- | --- |
| As deployed | Defaults: every cap equals the stage value (a 3.8 GiB host). Shows where stage breaks. |
| Scaled | Uncomment the caps in `.env.perf` (`PERF_BACKEND_MEM_LIMIT` and friends); JVM heaps follow. |
| Production security | `PERF_REQUIRE_AUTHENTICATION=true`. Stage runs `false` today, so the tenant API needs no token. |
| Without monitoring | `PERF_MONITORING=off`, to measure what the monitoring itself costs. |

`down` keeps the volumes, so data survives between runs; `down -v` is the clean slate. Before
destroying the VPS, stop Prometheus and archive its volume `processpuzzle_perf-prometheus-data`.

## What the code review found (matters for the tests)

- **Uploads are buffered in the backend heap.** `MinioFileStorageService.uploadObject` reads the whole
  stream into a `byte[]`, and for images with thumbnails `UploadObject` reads it once more, so an
  image costs about twice its size plus the decoded pixels for the thumbnail. With the stage heap
  (about 500 MiB) a handful of concurrent 10 MB images is the expected breaking point. Check the
  heap panel during the upload phase.
- Direct-to-MinIO presigned uploads exist only in the AI media flow, not in the generic store upload.
- nginx buffers request bodies up to 50 MB before they reach the backend (`client_max_body_size 50m`).
- `/api/` is proxied with the prefix stripped, so anything the backend serves under `/actuator` is
  reachable through nginx. That is why this stack does not add a public metrics endpoint.

## Check on the first run

The compose files render and validate against the repository's real files, but the containers have
not been started anywhere yet. Open Prometheus, Status, Targets: all eight jobs should be up.

- **Backend (JMX agent).** Needs Java 25 support in the agent version (`PERF_JMX_EXPORTER_VERSION`,
  default 1.0.1). If the backend fails to start, remove the agent flag first. The Tomcat and Hikari
  panels stay empty if the MBean patterns in `jmx/backend.yml` do not match; the JVM panels do not
  depend on them.
- **MinIO and Keycloak.** Metric names follow the upstream documentation; compare against
  `curl minio:9000/minio/v2/metrics/cluster` from inside the network if a panel shows no data. The
  Keycloak job needs its management port 9000 reachable from other containers.
- **k6 panels** need the trend-stat setting above; their names are checked against the k6 docs only.
- **Overhead.** Monitoring takes roughly 0.6 to 1 GB RAM on the SUT, which is why the stage-sized
  runs use `PERF_MONITORING=off` for a comparison run. Moving Prometheus and Grafana to the
  generator VPS removes it from the SUT; that needs the exporter ports published and is a later step.
- **Alternative backend metrics.** A Micrometer Prometheus registry would add request latency
  histograms, but it means a dependency, an exposure setting and a change to `SecurityConfig`, so it
  is a decision for the repository rather than for this stack.
