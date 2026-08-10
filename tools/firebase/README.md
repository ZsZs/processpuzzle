### Google Cloud Platform (per Firebase project)
Three things the deploy pipeline **cannot** provision for itself. They are per-project, so
`processpuzzle-testbed-stage` (STAGE) and `processpuzzle-testbed` (PROD) each need them independently, and a
fresh project needs all three before the `ARTIFACT` control works end to end. Substitute the project id for
`<p>` below.

| # | What | Symptom when missing |
| --- | --- | --- |
| 1 | Default **Storage bucket** provisioned | `firebase deploy --only storage` fails: *Firebase Storage has not been set up on project …* |
| 2 | `roles/iam.serviceAccountTokenCreator` on the functions runtime SA, **granted to itself** | `objectStore` returns 500 `signingFailure` from `/objects/:bucket/:id/uri`; artifact thumbnails silently fall back to a MIME icon |
| 3 | `allUsers` holds `roles/run.invoker` on the **`objectstore`** Cloud Run service | Upload 401s with `Invalid IAP credentials: empty token` before the handler runs; the artifact row never appears |

**1 — Storage bucket.** The CLI enables `firebasestorage.googleapis.com` but cannot create the bucket. Click
*Get Started* on the console's Storage page, or:
```sh
curl -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  "https://firebasestorage.googleapis.com/v1beta/projects/<p>/defaultBucket"
```
The bucket is named `<p>.firebasestorage.app`, which is what `apps/processpuzzle-testbed/src/run-time-conf/config.*.json`
expects. Needs `firebasestorage.defaultBucket.create` (i.e. `roles/firebase.admin`).

**2 — Signed download URIs.** `FirebaseFileStorageService.getObjectUri` signs through the IAM Credentials
`signBlob` API, which requires the runtime service account to be a token creator **on itself**. A gen-2
function may run as the App Engine *or* the Compute default SA, so derive it rather than assume:
```sh
SA=$(gcloud run services describe objectstore --region=europe-central2 --project=<p> \
     --format='value(spec.template.spec.serviceAccountName)')
gcloud iam service-accounts add-iam-policy-binding "$SA" --member="serviceAccount:$SA" \
  --role="roles/iam.serviceAccountTokenCreator" --project=<p>
```
Neither unit tests nor the Storage emulator exercise this path — the emulator does no signing at all — so only
a deployed smoke test catches it.

**3 — Public invoker on `objectStore`.** `firebase deploy` applies the `allUsers` invoker grant when a function
is *created*; on an **update** it re-applies the Cloud Run IAM policy only for an invoker the source declares.
`object-store.function.ts` therefore states `invoker: 'public'` explicitly, so every deploy asserts the grant —
which means the deploy service account needs `run.services.setIamPolicy` (`roles/run.admin`). Without that
permission the deploy now fails loudly at *set invoker* rather than leaving a private service behind; grant the
role, or repair a project by hand once:
```sh
gcloud run services add-iam-policy-binding objectstore --region europe-central2 --project <p> \
  --member allUsers --role roles/run.invoker
```

The deploy service account behind `FIREBASE_SERVICE_ACCOUNT` consequently needs `roles/firebase.admin`,
`roles/cloudfunctions.admin`, `roles/run.admin`, `roles/artifactregistry.admin` and
`roles/iam.serviceAccountUser`.
