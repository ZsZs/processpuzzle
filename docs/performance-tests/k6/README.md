# k6 load tests for ProcessPuzzle

Skeleton: syntax-checked with Node only; k6 was not available, so **nothing has run**. Run `seed` and a
`baseline` against the perf stack (`tools/docker/perf`) first and fix what the real API says.

```
lib/config.js     URLs, credentials, thresholds (the strategy's success criteria, abortOnFail)
lib/auth.js       Keycloak password grant (public client, test-user), used when AUTH=on
lib/api.js        entity definition/entities, multipart upload, presigned download
lib/files.js      random 1/5/10 MB buffers, built once in init
lib/profiles.js   user steps, stress, spike, soak shapes
lib/journeys.js   browse and CRUD journey (read/write ratio and think time are placeholders)
scenarios/main.js PHASE=baseline|users|stress|spike|soak|uploads
seed/seed.js      rows and files through the REST API
run.sh            runs a phase, pushes metrics to the SUT's Prometheus
```

```bash
export BASE_URL=http://<sut>:9090 KEYCLOAK_URL=http://<sut>:7070 PROM=http://127.0.0.1:9099  # ssh -L 9099:127.0.0.1:9099 <sut>
./run.sh seed          # SEED_COUNT, SEED_FILES, FILE_MB via EXTRA_K6_ARGS="-e SEED_COUNT=100000"
./run.sh baseline && ./run.sh users
AUTH=on ...            # with PERF_REQUIRE_AUTHENTICATION=true on the SUT
```

## Unverified assumptions (check on the first run)
- `ORG_KEY` (default `processpuzzle-testbed`) is the tenant path segment; the status value `ACTIVE`, the
  list response shape (`content[]`) and the `/uri` response field are read from the OpenAPI files only.
- Password grant is enabled on the realm's `processpuzzle-testbed` client (it is in the realm import);
  one shared user means one token for all VUs. Per-VU users/tokens (the "production security" run) are not built yet.
- Each VU holds its own copy of the upload buffers (1+5+10 MB): size the generator for it, or lower `SIZES_MB`.
- Not covered yet: login/registration burst, DoS/abuse scenarios, downloads in a scenario, workflow journeys,
  HAR-derived scripts.
- `-o experimental-prometheus-rw` is the older output name; newer k6 versions call it `prometheus-rw`.
