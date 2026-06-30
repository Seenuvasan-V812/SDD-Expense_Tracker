# UI Design System — Daily Expense Application

| Field | Value |
|-------|-------|
| **Document** | 15 — UI Design System & Frontend Library Registry |
| **Feature** | Daily Expense Application |
| **Feature Directory** | `specs/001-daily-expense-tracker` |
| **Status** | ACTIVE |
| **Created** | 2026-06-28 |
| **Last Amended** | 2026-06-28 |
| **Governing Authority** | [Engineering Constitution v1.2.0](../../.specify/memory/constitution.md) — FE-7 (Approved UI library registry), FE-1–FE-6 |
| **Purpose** | Normative source for all frontend UI, styling, charting, and form dependencies. FE-7 makes this registry binding; no package may be added to `frontend/package.json` without an entry here. |

> **This file is the law for frontend dependencies.** Agents and contributors MUST check this registry
> before adding any UI/styling/charting/form package. Packages absent from this registry are **PROHIBITED**
> regardless of how widely used they are. Amendment requires an approving PR that updates this document;
> see Section 2.

---

## 1. Purpose & Scope

This document governs:

- **UI components** — interactive elements (buttons, dialogs, tables, forms, navigation, progress bars)
- **Styling system** — utility classes, design tokens, responsive utilities
- **Data visualisation** — charts and graphs (activated Phase 2)
- **Form management** — state, validation, and submission
- **Icon set** — iconography
- **Date and number utilities** — locale-aware formatting (INR, en-IN)
- **Server-state management** — data-fetching, caching, loading/error/empty lifecycle

**Out of scope:** Backend Java dependencies, test utilities (Vitest / MSW / React Testing Library are governed
by Doc 14 — Test Strategy), CI tooling, and Vite/PostCSS build plugins (which are dev-only and carry
no runtime API surface).

**Phase authority.** The Phase Scope column controls WHEN a library is activated. Phase-2-scoped
libraries are registered here in advance so their eventual addition requires no amendment; they MUST NOT
be imported in any Phase-1 component.

---

## 2. Amendment Process

Adding or removing a library from this registry requires:

1. Open a pull request that:
   - Adds / removes the entry in Section 3 (Approved Library Registry).
   - States the purpose, the FE law(s) it aligns with, and any constraint.
   - Confirms no Constitution principle (P1–P7) is violated.
   - Notes migration or cleanup if removing.
2. Update `Engineering Constitution` `Last Amended` date (PATCH bump — no new law required for registry changes).
3. Update `plan.md` Technical Context if the library affects a plan milestone or Phase-5 gate.
4. Label the PR `design-system-amendment`.

**Violation:** Merging a `frontend/package.json` UI/styling/form/chart addition without a corresponding
approved registry entry is a **FE-7 violation** — the PR is reverted on sight.

---

## 3. Approved Library Registry

> All libraries below are approved for use in `frontend/`. Libraries NOT in this table are PROHIBITED.
> Version constraints are minimum-required; patch updates are always permitted without amendment.
> Install commands are informational; use your team's package manager consistently.

| Library | Min Version | Purpose | Phase Scope | Key FE-Law | Key Constraints |
|---------|-------------|---------|-------------|------------|-----------------|
| **tailwindcss** | `≥ 3.4` | Utility-first CSS; design-token system via `tailwind.config.ts`; responsive utilities | Phase 1 | FE-3, FE-6 | `tailwind.config.ts` is the single source of design tokens; no inline `style={{}}` for values that belong in tokens; no raw hex/px literals in component files |
| **tailwindcss-animate** | `≥ 1.0` | Keyframe animation utilities required by shadcn/ui | Phase 1 | FE-3 | Used only via Tailwind utilities in `globals.css`; no direct JS import |
| **shadcn/ui** (source-copied components) | Latest CLI | Accessible component set built on Radix UI + Tailwind; `npx shadcn@latest init` copies component source into `frontend/src/components/ui/` | Phase 1 | FE-4, FE-7 | Source is copied into the repo, NOT imported from npm; components live in `src/components/ui/`; customise by editing copied source, not by CSS overrides from outside |
| **@radix-ui/react-\*** | Latest (pulled by shadcn) | Headless accessible primitives underlying shadcn/ui: focus management, ARIA, keyboard navigation | Phase 1 | FE-4 (a11y out-of-box) | Never consumed directly in feature code; access only through shadcn wrapper components; do not mix Radix and shadcn APIs for the same primitive |
| **lucide-react** | `≥ 0.400` | Icon set (shipped with shadcn/ui) | Phase 1 | FE-3 (typed props) | All icons sourced from `lucide-react` only; no embedded SVGs, no alternative icon fonts, no icon CDN |
| **react-hook-form** | `≥ 7.50` | Form state management; integrates with zod resolver; minimises re-renders | Phase 1 | FE-5 (client-side validation required), FE-3 | Every form uses `react-hook-form`; no manual `useState` per field; `useForm<z.infer<typeof Schema>>` to preserve TS strict — no `any` cast |
| **zod** | `≥ 3.22` | Runtime schema validation; TypeScript type inference; client-side validation schemas | Phase 1 | FE-5, P2 (Type Safety — schemas generate strict TS types, eliminating `any`) | Validation schemas defined once per form; reused for both client-side validation and type inference; never cast schema output to `any` |
| **@hookform/resolvers** | `≥ 3.3` | Adapter that wires a zod schema into `react-hook-form` via `zodResolver()` | Phase 1 | FE-5, FE-3 | Install alongside `react-hook-form`; use `zodResolver(schema)` in `useForm` config |
| **@tanstack/react-query** | `≥ 5.0` | Server-state management — fetching, caching, background sync; provides `isLoading` / `isError` / `data` state that satisfies FE-4 | Phase 1 | FE-1 (all network calls go through the single `axiosClient`, never a second HTTP client), FE-4 | `queryFn` MUST call `axiosClient`; never pass `fetch` or a second Axios instance as `queryFn`; `defaultOptions.queries.retry` limited to 1; `staleTime` set globally in `QueryClient` config |
| **@tanstack/react-table** | `≥ 8.17` | Headless table with sorting, pagination, filtering; powers the spec'd `PaginatedTable` component | Phase 1 | FE-4 (explicit loading/empty states on the wrapper), FE-3 (generic `ColumnDef<T>` preserves strict types) | Used ONLY inside `src/components/PaginatedTable.tsx`; feature pages consume `PaginatedTable`, not `@tanstack/react-table` APIs directly |
| **date-fns** | `≥ 3.0` | Date formatting and arithmetic; `en-IN` locale support for `DateDisplay` | Phase 1 | P2 (typed date operations), FE-3 | `format(date, pattern, { locale: enIN })` with imported `en-IN` locale; no `moment.js`, no `dayjs` — these are prohibited alternatives |
| **recharts** | `≥ 2.12` | Composable SVG charts — line, bar, pie, area — for Reporting & Dashboard (Phase 2) | **Phase 2 only** | FE-4 (loading/empty wrappers required on every chart), FE-3 (typed chart data) | **Activated Phase 2 only.** Do NOT install or import in any Phase-1 component. Registered here to prevent ad-hoc addition later |

> **Native browser APIs (no library required):**
> - `MoneyDisplay` → `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount)` — zero external dependency.
> - `traceId` generation → `crypto.randomUUID()` — zero external dependency.
> - Date parsing from ISO strings → `new Date(isoString)` before passing to `date-fns`.

---

## 4. Design Tokens

All visual tokens are defined in `tailwind.config.ts` and consumed via Tailwind utility classes.
Hardcoding hex values, px values, or named colours directly in component files is **PROHIBITED** —
the same philosophy as FE-6 (no hardcoded config) applied to styling.

### 4.1 Colour Palette

Tokens follow the shadcn/ui CSS-variable pattern: HSL values set in `src/globals.css` under `:root { }`,
then referenced in `tailwind.config.ts` as `hsl(var(--token))`.

| Token name | CSS variable | Semantic usage |
|-----------|-------------|---------------|
| `primary` | `--primary` | Primary actions, CTAs, active navigation indicator |
| `primary-foreground` | `--primary-foreground` | Text on primary background |
| `secondary` | `--secondary` | Secondary actions, badge backgrounds |
| `secondary-foreground` | `--secondary-foreground` | Text on secondary background |
| `destructive` | `--destructive` | Errors, delete confirmations, exceeded budget |
| `destructive-foreground` | `--destructive-foreground` | Text on destructive background |
| `muted` | `--muted` | Disabled states, placeholder text, empty state surfaces |
| `muted-foreground` | `--muted-foreground` | Subdued labels, captions, helper text |
| `accent` | `--accent` | Hover state backgrounds, highlighted rows |
| `background` | `--background` | Page background |
| `foreground` | `--foreground` | Default body text |
| `card` | `--card` | Card and panel surface colour |
| `card-foreground` | `--card-foreground` | Text on card surfaces |
| `border` | `--border` | Dividers, input borders, table borders |
| `success` | `--success` | Goal completed; budget within safe range (≤ 60% used) |
| `warning` | `--warning` | Budget approaching threshold (60–80% used) |
| `danger` | `--danger` | Budget exceeded (> 100% used); critical alerts |

> **Finance-domain semantic tokens.** `success`, `warning`, and `danger` map directly to
> BUD-INV-5 threshold states and Savings Goal progress. Components use `text-success` / `text-warning`
> / `text-danger` Tailwind classes — never raw hex values. The `Progress` component for budgets and goals
> applies the correct semantic class based on the percentage threshold.

### 4.2 Typography

| Role | Tailwind classes | Usage context |
|------|-----------------|--------------|
| Page title | `text-2xl font-bold tracking-tight` | H1 on each feature page |
| Section heading | `text-xl font-semibold` | Card headers, section labels |
| Card heading | `text-lg font-medium` | Individual card titles |
| Body | `text-sm` | Default body copy |
| Caption | `text-xs text-muted-foreground` | Field labels, helper text, timestamps |
| Money amount | `text-base font-semibold tabular-nums` | All monetary values (MoneyDisplay) |

> `tabular-nums` is **mandatory** on every monetary display so ₹ amounts align vertically in table columns.

### 4.3 Responsive Breakpoints

Breakpoints follow Tailwind defaults and align with Doc 12 Phase 5 gate (T110 acceptance criteria):

| Breakpoint | Min-width | Target context |
|------------|-----------|---------------|
| (default) | 320 px | Minimum supported mobile width (spec gate) |
| `sm` | 640 px | Large mobile (landscape) |
| `md` | 768 px | Tablet — spec gate requirement |
| `lg` | 1024 px | Desktop — spec gate requirement |
| `xl` | 1280 px | Wide desktop |

**Breakpoint rule:** Feature pages must be fully functional at 320 px, 768 px, and 1024 px.
Below `md`, navigation collapses to a `Sheet` (drawer); tables collapse to card-per-row layout.

---

## 5. Component Inventory

This table maps every shared and feature-level component named in `plan.md` §Project Structure and
`12-implementation-plan.md` Phase 5 tasks to its concrete library implementation. Agents and
contributors building Phase 5 tasks **MUST** follow this mapping exactly.

### 5.1 Shared Components (`frontend/src/components/`)

| Spec Component | Task | shadcn/ui Primitive | Additional Library | Implementation Notes |
|----------------|------|--------------------|--------------------|---------------------|
| `LoadingState` | T105 | `Skeleton` | — | Multiple `Skeleton` blocks matching content layout; wrap root `<div>` with `aria-busy="true"` and `aria-label="Loading"` |
| `ErrorState` | T105 | `Alert` (destructive variant) | — | `AlertTitle` + `AlertDescription`; include a retry `Button` where the caller supports retry; icon via `lucide-react` `AlertCircle` |
| `EmptyState` | T105 | `Card` | `lucide-react` | Custom layout inside a `Card`; use a domain-appropriate Lucide icon (e.g. `PackageOpen`, `PiggyBank`, `Wallet`) + short descriptive message + optional action `Button` |
| `PaginatedTable` | T105 | `Table` + `TableHeader` + `TableRow` + `TableCell` + `TableHead` | `@tanstack/react-table` | `useReactTable` for column definitions; `PageResponse<T>` drives pagination controls (Prev / Next + `page X of Y`); pagination `Button`s use shadcn `Button` variant `outline` |
| `MoneyDisplay` | T105 | — (no shadcn) | Native `Intl` API | `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount)` inside `<span className="tabular-nums font-semibold" />` |
| `DateDisplay` | T105 | — (no shadcn) | `date-fns` | `format(parseISO(dateStr), 'dd MMM yyyy', { locale: enIN })` where `enIN` is imported from `date-fns/locale` |

### 5.2 Form Pattern (all forms — T104, T106, T107, T108, T109)

Every form in the application follows this single pattern:

```typescript
// Pattern — not a literal file; apply per form
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Form, FormField, FormItem, FormLabel, FormControl, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'

const Schema = z.object({ /* field definitions */ })
type FormValues = z.infer<typeof Schema>   // TypeScript type derived — no `any`

export function FeatureForm() {
  const form = useForm<FormValues>({ resolver: zodResolver(Schema) })
  const onSubmit = (values: FormValues) => { /* call axiosClient via mutation */ }
  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <FormField control={form.control} name="fieldName" render={({ field }) => (
          <FormItem>
            <FormLabel>Label</FormLabel>
            <FormControl><Input {...field} /></FormControl>
            <FormMessage />   {/* renders zod validation error */}
          </FormItem>
        )} />
        <Button type="submit" disabled={form.formState.isSubmitting}>Submit</Button>
      </form>
    </Form>
  )
}
```

> `FormMessage` automatically renders the zod validation error for the field. This satisfies **FE-5**
> (client-side validation required before submit) without any manual state management.

### 5.3 Data-Fetching Pattern (all data views — T106, T107, T108, T109)

All data-fetching components use `@tanstack/react-query`. The `queryFn` MUST call `axiosClient` (FE-1):

```typescript
// Pattern — not a literal file; apply per data view
import { useQuery } from '@tanstack/react-query'
import { axiosClient } from '@/lib/axiosClient'

function useExpenses(params: ListExpensesParams) {
  return useQuery({
    queryKey: ['expenses', params],
    queryFn: () => axiosClient.get<PageResponse<ExpenseResponse>>('/api/v1/expenses', { params })
      .then(r => r.data),
  })
}

// Consuming component — satisfies FE-4 (explicit loading / error / empty)
function ExpenseList() {
  const { data, isLoading, isError } = useExpenses(params)
  if (isLoading) return <LoadingState />
  if (isError)   return <ErrorState />
  if (!data?.content.length) return <EmptyState />
  return <PaginatedTable ... />
}
```

### 5.4 Feature-Level UI Components

| Feature | Key shadcn Components | Phase |
|---------|-----------------------|-------|
| Auth pages (login / register / verify / forgot / reset) | `Card`, `Input`, `Button`, `Form`, `Label`, `Alert` | Phase 1 (T104) |
| Categories list & form | `Table` or card list, `Dialog`, `Form`, `Select` (type filter), `Badge` | Phase 1 (T106) |
| Expenses list, filters, form | `Table` (via `PaginatedTable`), `Dialog`, `Form`, `Select`, `DatePicker` (Input type=date), `Badge` (tags) | Phase 1 (T107) |
| Savings Goals list, detail, progress | `Card`, `Progress`, `Dialog`, `Form`, `Tabs` (Active / Completed split) | Phase 1 (T108) |
| Budgets list, status cards, toggles | `Card`, `Progress`, `Switch` (activation / rollover), `Dialog`, `Form` | Phase 1 (T109) |
| Navigation | `NavigationMenu` (desktop) + `Sheet` (mobile drawer) | Phase 1 (T104+) |
| Toast / feedback | `Toaster` + `toast()` (Sonner via shadcn) | Phase 1 (all write operations) |
| Charts — spending, budget, savings (Phase 2) | `recharts` `LineChart` / `BarChart` / `PieChart` with shadcn-styled wrappers | **Phase 2** |

---

## 6. Tailwind Configuration Structure

`frontend/tailwind.config.ts` **MUST** follow this canonical structure (exact HSL values defined during T100):

```typescript
// frontend/tailwind.config.ts
import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: ['class'],                          // enables shadcn class-based dark mode
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // shadcn CSS-variable-driven tokens
        primary:     { DEFAULT: 'hsl(var(--primary))',     foreground: 'hsl(var(--primary-foreground))' },
        secondary:   { DEFAULT: 'hsl(var(--secondary))',   foreground: 'hsl(var(--secondary-foreground))' },
        destructive: { DEFAULT: 'hsl(var(--destructive))', foreground: 'hsl(var(--destructive-foreground))' },
        muted:       { DEFAULT: 'hsl(var(--muted))',       foreground: 'hsl(var(--muted-foreground))' },
        accent:      { DEFAULT: 'hsl(var(--accent))',      foreground: 'hsl(var(--accent-foreground))' },
        card:        { DEFAULT: 'hsl(var(--card))',        foreground: 'hsl(var(--card-foreground))' },
        background:  'hsl(var(--background))',
        foreground:  'hsl(var(--foreground))',
        border:      'hsl(var(--border))',
        input:       'hsl(var(--input))',
        ring:        'hsl(var(--ring))',
        // Finance-domain semantic tokens (Section 4.1)
        success:     'hsl(var(--success))',
        warning:     'hsl(var(--warning))',
        danger:      'hsl(var(--danger))',
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
    },
  },
  plugins: [require('tailwindcss-animate')],     // required for shadcn animation utilities
}
export default config
```

> HSL token values are defined in `frontend/src/globals.css` under `:root { --primary: ...; }`.
> The config references them as `hsl(var(--token))`. This keeps theming in CSS, not in JavaScript,
> and enables dark-mode switching via a single class on `<html>`.

---

## 7. Accessibility Standards

All Phase-5 UI **MUST** achieve WCAG AA. The shadcn/ui + Radix combination provides the following
accessibility guarantees by default:

| Standard | Built-in mechanism | Contributor responsibility |
|----------|-------------------|-----------------------------|
| Keyboard navigation | Radix manages focus (Tab, Arrow keys, Escape, Enter / Space) for all interactive primitives | Verify with T110 axe-core test; ensure no custom JS traps focus |
| ARIA roles and labels | Radix supplies `role`, `aria-expanded`, `aria-haspopup`, `aria-labelledby`, `aria-describedby` | Add `aria-label` on icon-only `Button` elements (e.g. delete row, close dialog) |
| Colour contrast | Semantic token palette designed for WCAG AA (≥ 4.5:1 text, ≥ 3:1 UI components) | Verify with T110 axe-core; zero serious/critical violations required for G-14 release gate |
| Focus visible ring | `:focus-visible` ring via global `focus-visible:ring-2 focus-visible:ring-ring` | Applied in `globals.css`; **never suppress** `outline: none` without providing an alternative |
| Live regions | `aria-busy="true"` on `LoadingState`; `aria-live="polite"` on toast region | Applied in `LoadingState` wrapper and `Toaster` component |
| Form error announcement | `FormMessage` from shadcn/ui is associated with its field via `aria-describedby` | Provided automatically by the `Form` + `FormField` pattern (Section 5.2) |

---

## 8. Phase 5 Compliance Checklist (FE-7 gate, T111)

Before declaring Phase 5 complete, an agent or reviewer **MUST** verify all items below:

- [ ] Every package in `frontend/package.json` that provides UI / styling / charting / form functionality appears in Section 3 of this document
- [ ] No Phase-2-only library (`recharts`) is imported in any Phase-1 component or page
- [ ] `tailwind.config.ts` follows the canonical structure in Section 6; no raw hex / px literals in `*.tsx` component files
- [ ] Every shared component (`LoadingState`, `ErrorState`, `EmptyState`, `PaginatedTable`, `MoneyDisplay`, `DateDisplay`) maps to the implementation specified in Section 5.1
- [ ] Every form uses `react-hook-form` + `zodResolver` + shadcn `Form` primitives (Section 5.2 pattern)
- [ ] Every data-fetching component uses `@tanstack/react-query` with `axiosClient` as `queryFn` (Section 5.3 pattern)
- [ ] `MoneyDisplay` uses `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })` — no external library
- [ ] `DateDisplay` uses `date-fns` `format` with imported `enIN` locale — no `moment.js` or `dayjs`
- [ ] axe-core audit: **0 serious/critical violations** on all pages (T110, release gate G-14)
- [ ] WCAG AA colour contrast confirmed for primary, success, warning, and danger tokens
- [ ] All icon usages sourced from `lucide-react` only — no embedded SVGs, no alternative icon library

---

*End of `15-ui-design-system.md` — UI Design System & Frontend Library Registry for the Daily Expense Application.*
