#!/usr/bin/env python3
"""Makes one Coolify resource's environment equal to a committed env file plus named GitHub secrets.

Invoked by action.yml, which passes every input through the environment. Standard library only, so
the step needs nothing installed on the runner. Deliberately Python 3.9-compatible, so it can be run
locally against a mock without a newer interpreter.

Modes (MODE):
  off     do nothing.
  report  compare and print drift as WARNINGS; never writes, never fails the job. The default, so
          adopting this action changes no deployment until a mode is chosen deliberately.
  check   compare and FAIL on drift; never writes.
  sync    upsert every managed key through the bulk endpoint, then read back and FAIL if the
          resource still differs.

What is "managed": every KEY=VALUE line of ENV_FILE, plus every name in SECRET_KEYS, whose value is
read from SECRETS_JSON (the workflow's `toJSON(secrets)`). Keys present in Coolify but managed by
neither are reported and left alone -- this never deletes.

Everything that can be decided before calling Coolify is decided first. In `check` and `sync` a
missing secret, a malformed env file or a compose variable nobody accounts for stops the step
before the first request, so a half-configured workflow cannot push a partial environment.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

KEY_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
# `${NAME`, which covers `${NAME}`, `${NAME:-default}` and `${NAME-default}` alike.
COMPOSE_VARIABLE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)")
MODES = ("off", "report", "check", "sync")
# Coolify generates these itself (domains, generated passwords); they are not drift.
COOLIFY_MAGIC_PREFIXES = ("SERVICE_FQDN_", "SERVICE_URL_", "SERVICE_PASSWORD_", "SERVICE_USER_", "SERVICE_BASE64_")


class Reporter:
    """Routes findings to ::error:: or ::warning:: depending on whether the mode is strict."""

    def __init__(self, strict):
        self.strict = strict
        self.failed = False

    def problem(self, message):
        if self.strict:
            print(f"::error::{message}")
            self.failed = True
        else:
            print(f"::warning::{message}")

    @staticmethod
    def warn(message):
        print(f"::warning::{message}")

    @staticmethod
    def info(message):
        print(message)


def words(value):
    return [word for word in re.split(r"[\s,]+", value or "") if word]


def parse_env_file(path, reporter):
    """KEY=VALUE lines, in order. Comments and blank lines are skipped; nothing is interpolated."""
    values = {}
    with open(path, encoding="utf-8") as handle:
        for number, raw in enumerate(handle, start=1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            key, separator, value = line.partition("=")
            key = key.strip()
            if not separator or not KEY_PATTERN.match(key):
                reporter.problem(f"{path}:{number} is not a KEY=VALUE line.")
                continue
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
                value = value[1:-1]
            if key in values:
                reporter.problem(f"{path}:{number} sets {key} a second time.")
            values[key] = value
    return values


def compose_variables(path):
    """Every variable the compose file interpolates, ignoring the ones only its comments mention."""
    found = set()
    with open(path, encoding="utf-8") as handle:
        for raw in handle:
            if raw.lstrip().startswith("#"):
                continue
            found.update(COMPOSE_VARIABLE.findall(raw))
    return found


def resource_from_webhook(webhook):
    """`https://coolify.example/api/v1/deploy?uuid=abc` -> (`https://coolify.example`, `abc`)."""
    parsed = urllib.parse.urlparse(webhook)
    uuid = urllib.parse.parse_qs(parsed.query).get("uuid", [""])[0]
    if not parsed.scheme or not parsed.netloc or not uuid:
        return None, None
    return f"{parsed.scheme}://{parsed.netloc}", uuid


class Coolify:
    def __init__(self, base, token):
        self.base = base.rstrip("/")
        self.token = token

    def request(self, method, path, body=None):
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(f"{self.base}/api/v1{path}", data=data, method=method)
        request.add_header("Authorization", f"Bearer {self.token}")
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = response.read().decode("utf-8")
                return response.status, json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            payload = error.read().decode("utf-8", errors="replace")
            try:
                return error.code, json.loads(payload)
            except ValueError:
                return error.code, {"message": payload[:300]}


def error_message(payload):
    """Coolify's `message`, never the whole body: a 422 can echo the values that were sent."""
    if isinstance(payload, dict) and payload.get("message"):
        return str(payload["message"])
    return "no message"


def detect_kind(coolify, uuid, reporter):
    """A Docker Compose resource is an `application` when Coolify builds it from a git repository
    and a `service` when it was created as an empty compose; the env endpoints differ by that."""
    for kind in ("applications", "services"):
        status, payload = coolify.request("GET", f"/{kind}/{uuid}")
        if status == 200:
            return kind
        if status in (401, 403):
            reporter.problem(
                f"Coolify refused to read resource {uuid} (HTTP {status}: {error_message(payload)}). "
                "COOLIFY_TOKEN needs the `read` permission for report/check and `write` for sync; "
                "the `deploy` permission alone is not enough.")
            return None
        if status != 404:
            reporter.problem(f"GET /{kind}/{uuid} answered HTTP {status}: {error_message(payload)}")
            return None
    reporter.problem(f"No Coolify application or service has uuid {uuid}. Check the webhook secret.")
    return None


def compare(coolify, kind, uuid, desired, secret_keys, unmanaged_ok, reporter):
    """Reports every managed key that Coolify lacks or holds differently. Returns True when equal."""
    status, payload = coolify.request("GET", f"/{kind}/{uuid}/envs")
    if status != 200 or not isinstance(payload, list):
        reporter.problem(f"Could not list the environment of {uuid}: HTTP {status}, {error_message(payload)}")
        return False

    actual = {entry.get("key"): entry for entry in payload if not entry.get("is_preview")}
    values_visible = any("value" in entry for entry in actual.values())
    if actual and not values_visible:
        reporter.warn("COOLIFY_TOKEN cannot read values (no `read:sensitive`), so only the presence of "
                      "each key was compared, not its value.")

    equal = True
    for key, value in desired.items():
        entry = actual.get(key)
        if entry is None:
            reporter.problem(f"{key} is not set in Coolify.")
            equal = False
        # `or ""`: Coolify may hand an empty value back as null.
        elif values_visible and (entry.get("value") or "") != value:
            if key in secret_keys:
                reporter.problem(f"{key} differs from the GitHub secret of the same name.")
            else:
                reporter.problem(f"{key}: Coolify has {entry.get('value')!r}, the env file says {value!r}.")
            equal = False

    for key in sorted(set(actual) - set(desired)):
        if key.startswith(COOLIFY_MAGIC_PREFIXES):
            continue
        if key in unmanaged_ok:
            shown = f" = {actual[key].get('value')!r}" if values_visible else ""
            reporter.info(f"  left to Coolify / its compose default: {key}{shown}")
        else:
            reporter.warn(f"{key} is set in Coolify but managed by nothing in git. Remove it there, or "
                          "add it to the env file.")
    return equal


def main():
    mode = (os.environ.get("MODE") or "report").strip().lower()
    if mode not in MODES:
        print(f"::error::Unknown mode {mode!r}; expected one of {', '.join(MODES)}.")
        return 1
    if mode == "off":
        print("Coolify environment sync is off for this environment.")
        return 0
    reporter = Reporter(strict=mode in ("check", "sync"))

    env_file = os.environ.get("ENV_FILE", "")
    compose_file = os.environ.get("COMPOSE_FILE", "")
    secret_keys = words(os.environ.get("SECRET_KEYS"))
    defaults_allowed = set(words(os.environ.get("DEFAULTS_ALLOWED")))

    if not env_file or not os.path.isfile(env_file):
        reporter.problem(f"Env file {env_file!r} does not exist. Each deployed environment needs its "
                         "committed tools/docker/env/<stack>/.env.<environment>.")
        return 1 if reporter.failed else 0
    desired = parse_env_file(env_file, reporter)

    try:
        secrets = json.loads(os.environ.get("SECRETS_JSON") or "{}")
    except ValueError:
        print("::error::The `secrets` input is not JSON. Pass `${{ toJSON(secrets) }}`.")
        return 1
    missing = [key for key in secret_keys if not secrets.get(key)]
    if missing:
        reporter.problem(f"Missing GitHub secrets in this environment: {' '.join(missing)}. Nothing is "
                         "pushed until every one exists (Settings -> Environments).")
    for key in secret_keys:
        if key in desired:
            reporter.problem(f"{key} is both in {env_file} and a secret. Keep it in one place.")
        if secrets.get(key):
            print(f"::add-mask::{secrets[key]}")
            desired[key] = secrets[key]

    if compose_file:
        used = compose_variables(compose_file)
        uncovered = sorted(used - set(desired) - set(secret_keys) - defaults_allowed)
        if uncovered:
            reporter.problem(
                f"{compose_file} reads {' '.join(uncovered)}, which neither {env_file} nor the secret list "
                "provides. Coolify would silently store the compose file's CI default for each. Add them "
                "to the env file, or to `defaults-allowed` if that default is right here.")
        for key in sorted(set(desired) - used):
            reporter.warn(f"{key} is managed but {compose_file} never reads it.")

    if reporter.failed:
        print("::error::Stopped before contacting Coolify; nothing was changed.")
        return 1

    token = os.environ.get("COOLIFY_TOKEN", "")
    base, uuid = resource_from_webhook(os.environ.get("COOLIFY_WEBHOOK", ""))
    if not token or not uuid:
        reporter.problem("COOLIFY_TOKEN or a webhook URL containing `?uuid=` is missing.")
        return 1 if reporter.failed else 0
    coolify = Coolify(base, token)

    kind = detect_kind(coolify, uuid, reporter)
    if kind is None:
        return 1 if reporter.failed else 0
    print(f"Coolify {kind[:-1]} {uuid}: {len(desired)} managed keys ({len(secret_keys)} from secrets), mode {mode}.")

    if mode == "sync":
        data = [{
            "key": key,
            "value": value,
            "is_preview": False,
            # Literal, so Coolify does not interpolate a `$` inside a password.
            "is_literal": True,
            "comment": f"Managed from GitHub secret {key}; edit it there" if key in secret_keys
            else f"Managed from {env_file}; edit it in git, not here",
        } for key, value in desired.items()]
        status, payload = coolify.request("PATCH", f"/{kind}/{uuid}/envs/bulk", {"data": data})
        if status not in (200, 201):
            print(f"::error::Bulk update answered HTTP {status}: {error_message(payload)}")
            return 1
        print(f"Upserted {len(data)} keys: {' '.join(desired)}")

    equal = compare(coolify, kind, uuid, desired, set(secret_keys), defaults_allowed, reporter)
    if equal:
        print("Coolify matches git.")
    elif mode == "sync":
        print("::error::Coolify still differs from git after the update; see above.")
    return 1 if reporter.failed else 0


if __name__ == "__main__":
    sys.exit(main())
