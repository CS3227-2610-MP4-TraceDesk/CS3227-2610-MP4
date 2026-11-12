import { describe, expect, it } from 'vitest'
import type { TestTemplate } from '../preview/qa/qaTemplateFixtures'
import type { Verification } from './verificationApi'
import { applyQaTemplate } from './applyQaTemplate'

const draft: Verification = {
  version: 7,
  revision: 3,
  status: 'READY_FOR_QA',
  checks: [{ title: 'Existing check', result: 'Pass', notes: 'Observed once' }],
  notes: 'Existing verification notes',
  reason: '',
  decision: 'SAVE',
}

const template: TestTemplate = {
  id: 'refund-template',
  name: 'Refund checks',
  checks: ['Existing check', 'Unique refund IDs', 'Unique refund IDs'],
  whenToUse: 'For refund changes',
  preconditions: 'Two refund records exist',
  testCases: [
    { id: 'same', name: 'Existing check', steps: 'Already covered', expectedResult: 'Unchanged' },
    { id: 'new', name: 'Unique refund IDs', steps: 'Create two refunds', expectedResult: 'Each appears once' },
    { id: 'duplicate', name: 'Unique refund IDs', steps: 'Duplicate case', expectedResult: 'Ignored' },
  ],
}

describe('applyQaTemplate', () => {
  it('adds unique pending checks and appends guidance while preserving existing results and notes', () => {
    const applied = applyQaTemplate(draft, template)
    expect(applied).toMatchObject({ added: 1 })
    if ('error' in applied) throw new Error(applied.error)
    expect(applied.draft.checks).toEqual([
      { title: 'Existing check', result: 'Pass', notes: 'Observed once' },
      { title: 'Unique refund IDs', result: 'Pending' },
    ])
    expect(applied.draft.notes).toBe([
      'Existing verification notes',
      'Refund checks\nWhen to use: For refund changes\n\nPreconditions: Two refund records exist\n\nUnique refund IDs\nSteps: Create two refunds\nExpected result: Each appears once',
    ].join('\n\n'))
    expect(draft.checks).toEqual([{ title: 'Existing check', result: 'Pass', notes: 'Observed once' }])
    expect(draft.notes).toBe('Existing verification notes')
    expect(applied.draft.version).toBe(draft.version)
    expect(applied.draft.revision).toBe(draft.revision)
  })

  it('returns an unchanged draft when every template case is already present', () => {
    const result = applyQaTemplate({ ...draft, checks: [...draft.checks, { title: 'Unique refund IDs', result: 'Fail' }] }, template)
    expect(result).toEqual({ error: 'All checks in this template are already in Verification.' })
  })

  it('skips a template that would exceed the checklist limit without changing the draft', () => {
    const fullDraft: Verification = { ...draft, checks: Array.from({ length: 29 }, (_, index) => ({ title: `Existing ${index}`, result: 'Pending' as const })) }
    const before = structuredClone(fullDraft)
    const result = applyQaTemplate(fullDraft, { ...template, testCases: [
      { id: 'one', name: 'One new check', steps: '', expectedResult: '' },
      { id: 'two', name: 'Another new check', steps: '', expectedResult: '' },
    ] })
    expect(result).toEqual({ error: 'Verification allows up to 30 checks.' })
    expect(fullDraft).toEqual(before)
  })

  it('skips a template whose guidance would exceed the notes limit without changing the draft', () => {
    const longDraft: Verification = { ...draft, notes: 'x'.repeat(1_990) }
    const before = structuredClone(longDraft)
    const result = applyQaTemplate(longDraft, { ...template, whenToUse: '', preconditions: '', testCases: [
      { id: 'one', name: 'One new check', steps: 'x'.repeat(20), expectedResult: '' },
    ] })
    expect(result).toEqual({ error: 'Template guidance would exceed the verification notes limit.' })
    expect(longDraft).toEqual(before)
  })
})
