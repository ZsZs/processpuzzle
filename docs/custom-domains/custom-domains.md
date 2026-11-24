# Custom Domains for ProcessPuzzle Applications

Goal: a customer app reachable at `custom.processpuzzle.com/ycn` should also be reachable at `ycn.de`, configurable at runtime, for many customers, without touching deployment config by hand.

## 1. Concept summary

1. **Mapping as metadata.** A `CustomDomain` entity is the single source of truth: `hostname`, `applicationId`, `status` (PENDING, VERIFIED, ACTIVE, FAILED), `verificationToken`, `certificateStatus`. A small state machine drives the status. The Nginx config is generated output and can be rebuilt from the DB at any time.
2. **DNS setup by the customer.**
   - `www.ycn.de`: CNAME to `custom.processpuzzle.com`.
   - Apex `ycn.de`: ALIAS/ANAME or an A record to the fixed proxy IP (a CNAME is not allowed on the apex).
   - Ownership proof: TXT record `_processpuzzle-verify.ycn.de` = token. A background job checks it. Without this, anyone could claim someone else's domain.
3. **TLS on demand.** With Nginx, a provisioner service obtains the certificate (ACME HTTP-01), writes a per-domain server file, validates with `nginx -t` and reloads gracefully. Alternatives for very many domains: OpenResty with `lua-resty-auto-ssl`, or Caddy with on-demand TLS and an `ask` endpoint in front.
4. **Tenant resolution in the app.** Nginx forwards `Host` / `X-Forwarded-Host`. The gateway (Spring filter) looks the host up in the mapping table (cached, evicted on change) and sets the application/tenant context. Path-based (`/ycn`) and host-based (`ycn.de`) access resolve to the same application.
5. **Things that usually bite.**
   - Angular must not hardcode `/ycn`: use `<base href>` and runtime-derived API URLs.
   - Keycloak: register redirect URIs and web origins per domain (Admin API) when the domain turns ACTIVE. Check CORS and cookie domains.
   - 301-redirect `custom.processpuzzle.com/ycn` to the custom domain once ACTIVE.
   - Domain-based default locale (i18n routing) should read the mapping.
   - Links in emails (invites, password reset) should use the tenant's primary domain.
   - Unknown hosts pointed at your IP must be rejected (`return 444;`).

**Flow:** customer adds the domain, UI shows DNS instructions, verification job passes (VERIFIED), provisioner issues certificate and writes Nginx config, reload, status ACTIVE, Keycloak updated, cache evicted, traffic flows.

**Recommendation:** pure Nginx with the provisioner (Option A) is fine up to hundreds of domains. Beyond that, or for zero reloads, use OpenResty auto-ssl or Caddy.

## 2. Static Nginx base config (written once)

Port 80 serves ACME challenges for every host, so certificates can be issued before a per-domain file exists. Unknown hosts are rejected.

```nginx
# /etc/nginx/conf.d/00-base.conf
upstream gateway { server 127.0.0.1:8080; }

server {
    listen 80 default_server;
    server_name _;
    location /.well-known/acme-challenge/ { root /var/www/acme; }
    location / { return 301 https://$host$request_uri; }
}

# Reject unknown hosts on 443 (needs any valid cert, e.g. the platform wildcard)
server {
    listen 443 ssl default_server;
    ssl_certificate     /etc/ssl/default/fullchain.pem;
    ssl_certificate_key /etc/ssl/default/privkey.pem;
    return 444;
}

include /etc/nginx/conf.d/domains/*.conf;
```

Per-domain template (generated, `ycn.de.conf`):

```nginx
server {
    listen 443 ssl http2;
    server_name {{SERVER_NAMES}};

    ssl_certificate     /etc/letsencrypt/live/{{CERT_NAME}}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/{{CERT_NAME}}/privkey.pem;

    location / {
        proxy_pass http://gateway;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 3. Provisioner sketch (Spring Boot, hexagonal)

Structure:

- Domain: `CustomDomain`, `DomainStatus`, events `DomainVerified`, `DomainActivated`, `DomainProvisioningFailed`.
- Port: `DomainProvisioningPort` (`provision(CustomDomain)`, `deprovision(String hostname)`).
- Adapter: `NginxCertbotProvisioner` (below).
- Listeners react to `DomainActivated`: update Keycloak, evict the tenant cache.

Dependencies on the host/container: `certbot`, `nginx`, shared volumes `/var/www/acme`, `/etc/letsencrypt`, `/etc/nginx/conf.d/domains`.

```java
public final class HostnameValidator {
    private static final Pattern FQDN = Pattern.compile(
        "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Set<String> FORBIDDEN_SUFFIXES = Set.of("processpuzzle.com", "localhost");

    public static String validate(String raw) {
        String h = raw.trim().toLowerCase(Locale.ROOT);
        if (!FQDN.matcher(h).matches()) throw new IllegalArgumentException("Invalid hostname");
        for (String s : FORBIDDEN_SUFFIXES)
            if (h.equals(s) || h.endsWith("." + s)) throw new IllegalArgumentException("Reserved domain");
        return h;
    }
}
```

```java
@Component
class NginxCertbotProvisioner implements DomainProvisioningPort {

    private final ProvisionerProperties props;          // paths, email, reload delay
    private final ApplicationEventPublisher events;
    private final NginxReloader reloader;

    @Override
    public void provision(CustomDomain domain) {
        // names = only hostnames whose DNS was verified (apex and/or www)
        List<String> names = domain.verifiedNames().stream()
            .map(HostnameValidator::validate).toList();
        String certName = names.get(0);

        try {
            requestCertificate(certName, names);
            Path conf = writeServerConfig(certName, names);
            if (!reloader.validate()) {
                Files.deleteIfExists(conf);
                throw new IllegalStateException("nginx -t failed");
            }
            reloader.scheduleReload();                   // debounced
            events.publishEvent(new DomainActivated(domain.id(), names));
        } catch (Exception e) {
            events.publishEvent(new DomainProvisioningFailed(domain.id(), e.getMessage()));
        }
    }

    private void requestCertificate(String certName, List<String> names) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
            "certbot", "certonly", "--webroot", "-w", props.acmeWebroot(),
            "--non-interactive", "--agree-tos", "-m", props.acmeEmail(),
            "--cert-name", certName, "--keep-until-expiring"));
        names.forEach(n -> { cmd.add("-d"); cmd.add(n); });
        run(cmd, Duration.ofMinutes(2));                 // args array, never a shell string
    }

    private Path writeServerConfig(String certName, List<String> names) throws IOException {
        String body = props.template()
            .replace("{{SERVER_NAMES}}", String.join(" ", names))
            .replace("{{CERT_NAME}}", certName);
        Path target = props.domainsDir().resolve(certName + ".conf");
        Path tmp = Files.createTempFile(props.domainsDir(), certName, ".tmp");
        Files.writeString(tmp, body);
        return Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                          StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void deprovision(String hostname) {
        String h = HostnameValidator.validate(hostname);
        try {
            Files.deleteIfExists(props.domainsDir().resolve(h + ".conf"));
            reloader.scheduleReload();
            // optional: certbot delete --cert-name h
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    static void run(List<String> cmd, Duration timeout) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(timeout.toSeconds(), TimeUnit.SECONDS) || p.exitValue() != 0)
            throw new IllegalStateException("Command failed: " + cmd.get(0) + "\n" + out);
    }
}
```

Debounced reload, so a burst of onboardings causes one reload:

```java
@Component
class NginxReloader {
    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean pending = new AtomicBoolean(false);

    boolean validate() {
        try { NginxCertbotProvisioner.run(List.of("nginx", "-t"), Duration.ofSeconds(10)); return true; }
        catch (Exception e) { return false; }
    }

    void scheduleReload() {
        if (pending.compareAndSet(false, true)) {
            ex.schedule(() -> {
                pending.set(false);
                try { NginxCertbotProvisioner.run(List.of("nginx", "-s", "reload"), Duration.ofSeconds(10)); }
                catch (Exception e) { /* log + alert; domains stay in config, retry on next reload */ }
            }, 30, TimeUnit.SECONDS);
        }
    }
}
```

Triggering and recovery:

```java
@Component
class DomainVerificationJob {
    @Scheduled(fixedDelay = 60_000)
    void checkPending() {
        // for each PENDING domain: resolve TXT _processpuzzle-verify.<host>,
        // compare with token, then mark VERIFIED and call provisioner.provision(domain)
    }

    @EventListener(ApplicationReadyEvent.class)
    void rebuildConfigOnStartup() {
        // regenerate Nginx files for all ACTIVE domains (config is derived data)
    }
}
```

## 4. Operational notes

- **Renewal:** certbot's systemd timer renews; add a deploy hook `nginx -s reload` (`/etc/letsencrypt/renewal-hooks/deploy/`).
- **Security:** the hostname goes into a config file, so validate strictly (above) and never build shell strings. Give the provisioner only write access to the domains directory plus a narrowly scoped way to run `nginx -t` / `nginx -s reload` (sudoers rule, or run both in one container with a shared volume).
- **Let's Encrypt limits:** rate-limit certificate requests per tenant and overall; use the staging CA in the staging environment.
- **Failure handling:** FAILED status with the reason shown in the Admin UI and a "retry" action. Typical causes: DNS not yet propagated, www not resolving, CAA record blocking Let's Encrypt.
- **Scaling beyond ~hundreds of domains:** move to OpenResty `lua-resty-auto-ssl` or Caddy on-demand TLS. The entity, verification and tenant resolution stay the same; only the provisioner adapter is replaced (that is the benefit of the port).
- **Testing:** unit-test the validator and template rendering; integration-test with certbot `--staging` and a throwaway Nginx container.
