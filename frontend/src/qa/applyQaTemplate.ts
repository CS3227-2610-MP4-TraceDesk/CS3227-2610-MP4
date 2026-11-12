import type { TestTemplate } from '../preview/qa/qaTemplateFixtures'
import type { Verification } from './verificationApi'

type Application = { draft: Verification; added: number } | { error: string }

export function applyQaTemplate(draft: Verification, template: TestTemplate): Application {
  const known = new Set(draft.checks.map(check => check.title.trim().toLowerCase()))
  const additions = (template.testCases ?? []).filter(testCase => {
    const key = testCase.name.trim().toLowerCase()
    if (known.has(key)) return false
    known.add(key)
    return true
  })
  if (!additions.length) return { error: 'All checks in this template are already in Verification.' }
  if (draft.checks.length + additions.length > 30) return { error: 'Verification allows up to 30 checks.' }

  const guidance = [
    template.whenToUse && 'When to use: ' + template.whenToUse,
    template.preconditions && 'Preconditions: ' + template.preconditions,
    ...additions.flatMap(testCase => {
      const details = [
        testCase.steps && 'Steps: ' + testCase.steps,
        testCase.expectedResult && 'Expected result: ' + testCase.expectedResult,
      ].filter(Boolean)
      return details.length ? [testCase.name + '\n' + details.join('\n')] : []
    }),
  ].filter(Boolean)
  const notes = guidance.length
    ? [draft.notes, template.name + '\n' + guidance.join('\n\n')].filter(Boolean).join('\n\n')
    : draft.notes
  if (Array.from(notes).length > 2000) return { error: 'Template guidance would exceed the verification notes limit.' }

  return {
    added: additions.length,
    draft: { ...draft, notes, checks: [
      ...draft.checks,
      ...additions.map(testCase => ({ title: testCase.name, result: 'Pending' as const })),
    ] },
  }
}
