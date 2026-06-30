/**
 * FE-7 Registry Compliance Lint
 * Verifies every UI/styling/charting/form package in package.json
 * appears in Doc 15 §3 (15-ui-design-system.md).
 */
import { readFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))

const APPROVED_REGISTRY = new Set([
  'tailwindcss',
  'tailwindcss-animate',
  '@radix-ui',          // prefix: all @radix-ui/* are approved (pulled by shadcn)
  'lucide-react',
  'react-hook-form',
  'zod',
  '@hookform/resolvers',
  '@tanstack/react-query',
  '@tanstack/react-table',
  'date-fns',
  // shadcn internal utilities (part of the shadcn/ui source-copy ecosystem)
  'class-variance-authority',
  'clsx',
  'tailwind-merge',
])

// Packages that are infrastructure (routing, HTTP, build) — not UI/form/chart
const INFRASTRUCTURE = new Set([
  'react', 'react-dom', 'axios', 'react-router-dom',
  'autoprefixer', 'postcss', 'vite', '@vitejs/plugin-react',
  'typescript', '@types/react', '@types/react-dom', '@types/node',
  'vitest', '@testing-library/react', '@testing-library/jest-dom',
  '@testing-library/user-event', 'msw', 'jsdom',
  'axe-core',
])

const pkg = JSON.parse(
  readFileSync(resolve(__dirname, '../package.json'), 'utf8')
)

const allDeps = {
  ...pkg.dependencies,
  ...pkg.devDependencies,
}

const violations = []

for (const dep of Object.keys(allDeps)) {
  if (INFRASTRUCTURE.has(dep)) continue

  const isApproved =
    APPROVED_REGISTRY.has(dep) ||
    [...APPROVED_REGISTRY].some(
      (prefix) => prefix.endsWith('/') || dep.startsWith(prefix + '/')
        ? dep.startsWith(prefix)
        : false
    ) ||
    dep.startsWith('@radix-ui/')

  if (!isApproved) {
    violations.push(dep)
  }
}

if (violations.length > 0) {
  console.error('FE-7 VIOLATION — unregistered UI/form/chart packages:')
  violations.forEach((v) => console.error(`  ✗ ${v}`))
  process.exit(1)
} else {
  console.log('FE-7 registry lint: ✓ all packages approved')
  process.exit(0)
}
