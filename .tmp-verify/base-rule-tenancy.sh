#!/usr/bin/env bash
# Live verification of the orgKey scoping in base-rule. Expects processpuzzle-testbed-backend on :8080.
set -u
B=http://localhost:8080
pass=0; fail=0

show() { printf '\n--- %s\n' "$1"; }
check() { # check <label> <expected> <actual>
  if [ "$2" = "$3" ]; then printf 'PASS %-58s %s\n' "$1" "$3"; pass=$((pass+1));
  else printf 'FAIL %-58s expected %s, got %s\n' "$1" "$2" "$3"; fail=$((fail+1)); fi
}
code() { curl -s -o /tmp/body.$$ -w '%{http_code}' "$@"; }
body() { cat /tmp/body.$$; }

rule() { # rule <id> <expression>
  printf '{"id":"%s","name":"Max quantity","context":"Order","expression":"%s","severity":"ERROR","message":"too many"}' "$1" "$2"
}

show "create under demo"
c=$(code -X POST "$B/organizations/demo/rules" -H 'Content-Type: application/json' -d "$(rule max-quantity 'entity.quantity <= 1')")
check "POST /organizations/demo/rules -> 201" 201 "$c"
check "response echoes orgKey=demo" demo "$(body | grep -o '"orgKey":"[^"]*"' | cut -d'"' -f4)"

show "same rule id under a second organization"
c=$(code -X POST "$B/organizations/other/rules" -H 'Content-Type: application/json' -d "$(rule max-quantity 'entity.quantity <= 5')")
check "POST /organizations/other/rules (same id) -> 201 not 409" 201 "$c"
c=$(code -X POST "$B/organizations/other/rules" -H 'Content-Type: application/json' -d "$(rule max-quantity 'entity.quantity <= 5')")
check "POST duplicate within other -> 409" 409 "$c"
printf '     conflict message: %s\n' "$(body | head -c 200)"

show "reads are scoped"
c=$(code "$B/organizations/demo/rules/max-quantity")
check "GET demo rule -> 200" 200 "$c"
check "demo keeps its own expression" "entity.quantity <= 1" "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["expression"])' 2>/dev/null)"
c=$(code "$B/organizations/other/rules/max-quantity")
check "GET other rule -> 200" 200 "$c"
check "other keeps its own expression" "entity.quantity <= 5" "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["expression"])' 2>/dev/null)"

show "evaluate: same payload, different verdicts"
EV='{"context":"Order","entity":{"quantity":3}}'
code -X POST "$B/organizations/demo/rules/evaluate" -H 'Content-Type: application/json' -d "$EV" >/dev/null
check "demo evaluate passed=false" false "$(body | python -c 'import json,sys; print(str(json.load(sys.stdin)["passed"]).lower())' 2>/dev/null)"
code -X POST "$B/organizations/other/rules/evaluate" -H 'Content-Type: application/json' -d "$EV" >/dev/null
check "other evaluate passed=true" true "$(body | python -c 'import json,sys; print(str(json.load(sys.stdin)["passed"]).lower())' 2>/dev/null)"

show "list is filtered"
# Counts are relative, not absolute: SampleRuleLoader seeds its own rules into 'demo'.
code "$B/organizations/demo/rules?where=context==Order" >/dev/null
check "demo list returns only demo rows" 1 "$(body | python -c 'import json,sys; c=json.load(sys.stdin)["content"]; print(1 if c and all(r["orgKey"]=="demo" for r in c) else 0)' 2>/dev/null)"
check "demo list contains its own rule" 1 "$(body | python -c 'import json,sys; print(1 if any(r["id"]=="max-quantity" for r in json.load(sys.stdin)["content"]) else 0)' 2>/dev/null)"
code "$B/organizations/other/rules" >/dev/null
check "other list holds exactly its one rule" 1 "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["totalElements"])' 2>/dev/null)"
code "$B/organizations/other/rules?where=orgKey==demo" >/dev/null
check "RSQL cannot switch tenant (orgKey==demo under other)" 0 "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["totalElements"])' 2>/dev/null)"
code "$B/organizations/other/rules?where=context==Order,orgKey==demo" >/dev/null
check "top-level RSQL OR cannot widen the tenant filter" other "$(body | python -c 'import json,sys; print(",".join(sorted({r["orgKey"] for r in json.load(sys.stdin)["content"]})))' 2>/dev/null)"

show "export from demo, import into third"
curl -s -D /tmp/hdr.$$ "$B/organizations/demo/rules/export" -o /tmp/export.$$.yaml
printf '     content-disposition: %s' "$(grep -i content-disposition /tmp/hdr.$$)"
check "export carries no orgKey" 0 "$(grep -c orgKey /tmp/export.$$.yaml)"
exported=$(grep -cE '^ *- id:' /tmp/export.$$.yaml)
code -X POST "$B/organizations/third/rules/import" -F "file=@/tmp/export.$$.yaml" >/dev/null
check "import into third creates every exported rule" "$exported" "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["created"])' 2>/dev/null)"
code "$B/organizations/third/rules/max-quantity" >/dev/null
check "third owns the imported rule" third "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["orgKey"])' 2>/dev/null)"

show "update is scoped"
c=$(code -X PUT "$B/organizations/demo/rules/max-quantity" -H 'Content-Type: application/json' -d "$(rule max-quantity 'entity.quantity <= 99')")
check "PUT demo -> 200" 200 "$c"
code "$B/organizations/other/rules/max-quantity" >/dev/null
check "other untouched after demo PUT" "entity.quantity <= 5" "$(body | python -c 'import json,sys; print(json.load(sys.stdin)["expression"])' 2>/dev/null)"

show "delete is scoped"
c=$(code -X DELETE "$B/organizations/demo/rules/max-quantity")
check "DELETE demo -> 204" 204 "$c"
c=$(code "$B/organizations/demo/rules/max-quantity")
check "demo rule gone -> 404" 404 "$c"
printf '     404 body: %s\n' "$(body | head -c 200)"
c=$(code "$B/organizations/other/rules/max-quantity")
check "other rule survives -> 200" 200 "$c"

show "unknown rule names the organization"
code "$B/organizations/demo/rules/nope" >/dev/null
check "404 message names demo/nope" 1 "$(body | grep -c 'demo/nope')"

printf '\n==== %s passed, %s failed ====\n' "$pass" "$fail"
rm -f /tmp/body.$$ /tmp/hdr.$$ /tmp/export.$$.yaml
[ "$fail" -eq 0 ]
