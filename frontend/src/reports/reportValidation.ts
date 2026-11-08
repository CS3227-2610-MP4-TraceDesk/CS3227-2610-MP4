export const reportFields = [
  'title',
  'description',
  'environment',
  'reproductionSteps',
  'expectedBehavior',
  'actualBehavior',
  'textLogs',
] as const

export type ReportField = typeof reportFields[number]
export type ReportValues = Record<ReportField, string>
export type FieldErrors = Partial<Record<ReportField, string>>

export const emptyReport: ReportValues = {
  title: '',
  description: '',
  environment: '',
  reproductionSteps: '',
  expectedBehavior: '',
  actualBehavior: '',
  textLogs: '',
}

export const reportFieldRules: Record<ReportField, { label: string; maxCodePoints: number; maxBytes: number; required: boolean }> = {
  title: { label: 'Title', maxCodePoints: 160, maxBytes: 640, required: true },
  description: { label: 'Description', maxCodePoints: 10_000, maxBytes: 40_000, required: true },
  environment: { label: 'Environment', maxCodePoints: 4_000, maxBytes: 16_000, required: true },
  reproductionSteps: { label: 'Steps to reproduce', maxCodePoints: 8_000, maxBytes: 32_000, required: true },
  expectedBehavior: { label: 'Expected behavior', maxCodePoints: 4_000, maxBytes: 16_000, required: true },
  actualBehavior: { label: 'Actual behavior', maxCodePoints: 4_000, maxBytes: 16_000, required: true },
  textLogs: { label: 'Logs or error text', maxCodePoints: 16_000, maxBytes: 64_000, required: false },
}

function containsForbiddenControls(value: string): boolean {
  for (const character of value) {
    const codePoint = character.codePointAt(0) ?? 0
    if ((codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f)) &&
      codePoint !== 0x09 && codePoint !== 0x0a && codePoint !== 0x0d) return true
  }
  return false
}

function hasUnpairedSurrogate(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const unit = value.charCodeAt(index)
    if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = value.charCodeAt(index + 1)
      if (!(next >= 0xdc00 && next <= 0xdfff)) return true
      index += 1
    } else if (unit >= 0xdc00 && unit <= 0xdfff) {
      return true
    }
  }
  return false
}

function isJavaWhitespaceOrSpace(codePoint: number): boolean {
  return (codePoint >= 0x09 && codePoint <= 0x0d) ||
    (codePoint >= 0x1c && codePoint <= 0x20) || codePoint === 0x00a0 || codePoint === 0x1680 ||
    (codePoint >= 0x2000 && codePoint <= 0x200a) || codePoint === 0x2028 || codePoint === 0x2029 ||
    codePoint === 0x202f || codePoint === 0x205f || codePoint === 0x3000
}

function hasVisibleCodePoint(value: string): boolean {
  return Array.from(value).some(character => !isJavaWhitespaceOrSpace(character.codePointAt(0) ?? 0))
}

export function validateReport(values: ReportValues): FieldErrors {
  const errors: FieldErrors = {}
  let totalBytes = 0
  for (const field of reportFields) {
    const value = values[field]
    const rule = reportFieldRules[field]
    const codePoints = Array.from(value).length
    const bytes = new TextEncoder().encode(value).length
    totalBytes += bytes
    if (hasUnpairedSurrogate(value)) {
      errors[field] = `${rule.label} contains invalid Unicode.`
    } else if (codePoints > rule.maxCodePoints) {
      errors[field] = `${rule.label} must be ${rule.maxCodePoints.toLocaleString()} characters or fewer.`
    } else if (bytes > rule.maxBytes) {
      errors[field] = `${rule.label} must use ${rule.maxBytes.toLocaleString()} UTF-8 bytes or fewer.`
    } else if (containsForbiddenControls(value)) {
      errors[field] = `${rule.label} contains an unsupported control character.`
    } else if (rule.required && !hasVisibleCodePoint(value)) {
      errors[field] = `${rule.label} is required.`
    }
  }
  if (totalBytes > 184_640 || new TextEncoder().encode(JSON.stringify(values)).length > 256 * 1024) {
    const field: ReportField = 'textLogs'
    errors[field] ??= 'The report exceeds the maximum request size.'
  }
  return errors
}

export function reportValuesFromDetail(detail: Record<string, unknown>): ReportValues {
  return Object.fromEntries(reportFields.map(field => [field, detail[field] as string])) as ReportValues
}
