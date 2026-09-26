# Generic Widget Embedding — Technical Spec

**Scope:** `base-app-frontend` — enable existing widgets (e.g. `mat-card-grid`) to be embedded into any route both **compile-time** (static config) and **runtime** (backend-driven config), through one generic mechanism.

---

## 1. Goals

- One host mechanism, regardless of whether a widget's configuration is compiled into the app or fetched from the backend at runtime.
- Same widget type usable on multiple routes with different configuration (card count, content, links, etc.).
- Adding a new widget type should not require touching a central switch statement or router config.
- Config coming from the backend should be validated before reaching a widget's inputs.

## 2. Core Abstractions

### 2.1 Widget contract

Every embeddable widget exposes a single required `config` input and nothing else mandatory.

```typescript
export interface WidgetConfig {
  [key: string]: unknown;
}

@Component({ /* ... */ })
export class MatCardGridWidgetComponent {
  config = input.required<MatCardGridConfig>();
}

export interface MatCardGridConfig extends WidgetConfig {
  cards: { title: string; body: string; link?: string; imageUrl?: string }[];
  columns?: number;
}
```

### 2.2 Widget registry (type → component)

Widgets self-register via a DI multi-token, so new widget types are additive.

```typescript
export const WIDGET_REGISTRY = new InjectionToken<WidgetRegistryEntry[]>('WIDGET_REGISTRY');

export interface WidgetRegistryEntry {
  type: string;                          // e.g. 'mat-card-grid'
  component: Type<unknown>;              // eager reference, or...
  loadComponent?: () => Promise<Type<unknown>>; // lazy reference (preferred at scale)
}

// per-feature module providers:
{
  provide: WIDGET_REGISTRY,
  useValue: {
    type: 'mat-card-grid',
    loadComponent: () => import('./mat-card-grid-widget.component').then(m => m.MatCardGridWidgetComponent),
  },
  multi: true,
}
```

```typescript
@Injectable({ providedIn: 'root' })
export class WidgetRegistryService {
  private entries = inject(WIDGET_REGISTRY, { optional: true }) ?? [];
  private byType = new Map(this.entries.map(e => [e.type, e]));

  entryFor(type: string): WidgetRegistryEntry | undefined {
    return this.byType.get(type);
  }

  async componentFor(type: string): Promise<Type<unknown> | undefined> {
    const entry = this.byType.get(type);
    if (!entry) return undefined;
    return entry.component ?? entry.loadComponent?.();
  }
}
```

### 2.3 Widget instance descriptor

The one shape that varies per route — identical whether it originates from a static config object or a backend entity.

```typescript
export interface WidgetInstance {
  id: string;
  type: string;          // key into WidgetRegistryService
  config: WidgetConfig;  // shape depends on `type`
  order?: number;
}
```

### 2.4 Config sources

```typescript
export abstract class WidgetConfigSource {
  abstract instancesForRoute(routeKey: string): Signal<WidgetInstance[]>;
}

@Injectable()
export class StaticWidgetConfigSource extends WidgetConfigSource {
  // reads from an imported const map, keyed by routeKey
  instancesForRoute(routeKey: string): Signal<WidgetInstance[]> {
    return signal(STATIC_WIDGETS_BY_ROUTE[routeKey] ?? []);
  }
}

@Injectable()
export class RemoteWidgetConfigSource extends WidgetConfigSource {
  private http = inject(HttpClient);

  instancesForRoute(routeKey: string): Signal<WidgetInstance[]> {
    const resource = httpResource<WidgetInstance[]>(() => `/api/route-widget-layout/${routeKey}`);
    return computed(() => resource.value() ?? []);
  }
}
```

A route declares which source it uses via route `data`:

```typescript
{ path: 'dashboard', data: { widgetSource: 'static', routeKey: 'dashboard' } }
{ path: 'landing/:pageKey', data: { widgetSource: 'remote' } }
```

```typescript
@Injectable({ providedIn: 'root' })
export class WidgetSourceResolverService {
  private staticSource = inject(StaticWidgetConfigSource);
  private remoteSource = inject(RemoteWidgetConfigSource);

  instancesForCurrentRoute(route: ActivatedRoute): Signal<WidgetInstance[]> {
    const data = route.snapshot.data;
    const routeKey = data['routeKey'] ?? route.snapshot.paramMap.get('pageKey') ?? route.snapshot.url.join('/');
    return data['widgetSource'] === 'remote'
      ? this.remoteSource.instancesForRoute(routeKey)
      : this.staticSource.instancesForRoute(routeKey);
  }
}
```

## 3. Generic Host Component

```typescript
@Component({
  selector: 'pp-widget-host',
  standalone: true,
  imports: [NgComponentOutlet],
  template: `
    @for (instance of instances(); track instance.id) {
      <ng-container
        *ngComponentOutlet="componentFor(instance) | async; inputs: { config: instance.config }"
      />
    }
  `,
})
export class WidgetHostComponent {
  instances = input.required<WidgetInstance[]>();
  private registry = inject(WidgetRegistryService);

  componentFor(instance: WidgetInstance) {
    return this.registry.componentFor(instance.type);
  }
}
```

Route usage — identical for static and remote:

```typescript
@Component({ template: `<pp-widget-host [instances]="widgetInstances()" />` })
export class SomeRouteComponent {
  private route = inject(ActivatedRoute);
  private sourceResolver = inject(WidgetSourceResolverService);
  widgetInstances = this.sourceResolver.instancesForCurrentRoute(this.route);
}
```

## 4. Backend Shape (EAV/JSONB)

Route widget layouts fit the existing `base-entity-backend` two-layer EAV/JSONB model without schema changes:

- Entity type: `route-widget-layout`
- Key attribute: `routeKey` (string, unique per layout)
- JSONB attribute: `instances` → `WidgetInstance[]`

```json
{
  "entityType": "route-widget-layout",
  "routeKey": "landing/spring-promo",
  "instances": [
    {
      "id": "hero-cards",
      "type": "mat-card-grid",
      "order": 1,
      "config": {
        "columns": 3,
        "cards": [
          { "title": "Feature A", "body": "...", "link": "/features/a" }
        ]
      }
    }
  ]
}
```

`GET /api/route-widget-layout/{routeKey}` returns `instances` directly.

## 5. Validation

Backend-sourced config is untyped JSON on the wire. Validate per `type` immediately after fetch, before it reaches a widget's `config.required()` input:

```typescript
const schemasByType: Record<string, ZodSchema> = {
  'mat-card-grid': matCardGridConfigSchema,
};

function validateInstance(instance: WidgetInstance): WidgetInstance | null {
  const schema = schemasByType[instance.type];
  if (!schema) return instance; // unknown type handled by fallback widget, not here
  const result = schema.safeParse(instance.config);
  return result.success ? instance : null; // drop or flag invalid instances
}
```

## 6. Open Items / Recommendations

- **Lazy loading**: prefer `loadComponent` over eager `component` in registry entries once there are more than a handful of widget types, so routes only download what they use.
- **Unknown type fallback**: render a placeholder widget when `registry.componentFor(type)` resolves to `undefined` (e.g. stale backend config referencing a removed widget type), rather than throwing.
- **Ordering**: sort `instances()` by `order` before rendering, or let the host component do it once, to keep config sources dumb.
- **Caching**: `RemoteWidgetConfigSource` can lean on Angular's `httpResource` (or an equivalent signal-based cache) to avoid refetching on repeated route entry within a session.
- **Editing UI**: since `route-widget-layout` is just another EAV entity, an admin UI for editing `instances` JSON could reuse whatever generic entity editor already exists for `base-entity-backend`.
