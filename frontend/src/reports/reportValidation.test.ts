import { describe, expect, it } from 'vitest'
import { emptyReport, reportFieldRules, reportFields, validateReport, type ReportValues } from './reportValidation'

function validValues(): ReportValues {
  return {
    title: 'Bug title',
    description: 'A description',
    environment: 'Browser',
    reproductionSteps: 'Open the page',
    expectedBehavior: 'It works',
    actualBehavior: 'It fails',
    textLogs: '',
  }
}

describe('report field validation', () => {
  it('accepts the exact code-point and UTF-8 limits, including supplementary characters', () => {
    for (const field of reportFields) {
      const rule = reportFieldRules[field]
      const values = validValues()
      values[field] = '😀'.repeat(Math.floor(rule.maxBytes / 4))
      if (Array.from(values[field]).length < rule.maxCodePoints) {
        values[field] += 'a'.repeat(rule.maxCodePoints - Array.from(values[field]).length)
      }
      expect(validateReport(values), field).toEqual({})
      values[field] += 'a'
      expect(validateReport(values)[field], field).toMatch(/characters or fewer|request size/u)
    }
  })

  it('requires visible Unicode content in the six mandatory fields without trimming values', () => {
    for (const field of reportFields.slice(0, 6)) {
      const values = validValues()
      values[field] = '\u00a0\u2007\u202f'
      expect(validateReport(values)[field], field).toMatch(/required/u)
    }
    const values = validValues()
    values.title = '  Actual title  '
    values.textLogs = '\t\r\n'
    expect(validateReport(values)).toEqual({})
    expect(values.title).toBe('  Actual title  ')
  })

  it('allows TAB, LF and CR but rejects NUL, other C0/C1 controls and unpaired surrogates', () => {
    for (const value of ['step\tone\ntwo\rthree', '😀']) {
      const values = validValues()
      values.description = value
      expect(validateReport(values)).toEqual({})
    }
    for (const value of ['bad\u0000value', 'bad\u000bvalue', 'bad\u0085value', '\ud800']) {
      const values = validValues()
      values.description = value
      expect(validateReport(values).description).toMatch(/control|Unicode/u)
    }
  })

  it('keeps optional logs empty and accepts the combined per-field maxima', () => {
    expect(validateReport(validValues())).toEqual({})
    const values = { ...emptyReport }
    for (const field of reportFields) values[field] = 'x'.repeat(reportFieldRules[field].maxCodePoints)
    expect(validateReport(values)).toEqual({})
    expect(new TextEncoder().encode(JSON.stringify(values)).length).toBeLessThanOrEqual(256 * 1024)
    values.textLogs += 'x'
    expect(validateReport(values).textLogs).toMatch(/characters or fewer/u)
  })
})
