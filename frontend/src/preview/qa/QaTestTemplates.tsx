import { useState, type FormEvent } from 'react'
import { Button } from '@openai/apps-sdk-ui/components/Button'
import { Input } from '@openai/apps-sdk-ui/components/Input'
import { Textarea } from '@openai/apps-sdk-ui/components/Textarea'
import { FileDocument, Plus, Search } from '@openai/apps-sdk-ui/components/Icon'
import { Disclosure } from '../../shared/Disclosure'
import type { TestTemplate } from './qaTemplateFixtures'

type TestTemplatesProps = {
  items: TestTemplate[]
  onChange?: (items: TestTemplate[]) => void
  onSaveTemplate?: (template: TestTemplate) => Promise<void>
  onDeleteTemplate?: (id: string) => Promise<void>
  selectedId: string
  onSelect: (id: string) => void
}

const emptyTemplate: TestTemplate = { id: '', name: '', checks: [] }

function TemplateEditor({
  initial,
  onSave,
  onCancel,
  onDelete,
}: {
  initial: TestTemplate
  onSave: (template: TestTemplate) => Promise<void>
  onCancel: () => void
  onDelete: () => Promise<void>
}) {
  const [name, setName] = useState(initial.name)
  const [whenToUse, setWhenToUse] = useState(initial.whenToUse ?? '')
  const [preconditions, setPreconditions] = useState(initial.preconditions ?? '')
  const [testCases, setTestCases] = useState(() =>
    initial.testCases?.length
      ? initial.testCases.map((testCase) => ({ ...testCase }))
      : (initial.checks.length ? initial.checks : ['']).map((name) => ({
          id: crypto.randomUUID(),
          name,
          steps: '',
          expectedResult: '',
        })),
  )
  const [expandedCases, setExpandedCases] = useState(
    () => new Set(testCases.filter((testCase) => !testCase.name).map((testCase) => testCase.id)),
  )
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const cases = testCases.map((testCase) => ({
      ...testCase,
      name: testCase.name.trim(),
      steps: testCase.steps.trim(),
      expectedResult: testCase.expectedResult.trim(),
    }))
    if (!name.trim() || Array.from(name.trim()).length > 120) {
      setError('Enter a template name of up to 120 characters.')
      return
    }
    if (Array.from(whenToUse).length > 1_000 || Array.from(preconditions).length > 1_000) {
      setError('When to use and preconditions must each be 1,000 characters or fewer.')
      return
    }
    if (cases.length < 1 || cases.length > 30) {
      setError('Add between 1 and 30 test cases.')
      return
    }
    if (cases.some((testCase) => !testCase.name)) {
      setError('Give every test case a check name.')
      return
    }
    if (new Set(cases.map((testCase) => testCase.name.toLowerCase())).size !== cases.length) {
      setError('Use a different check name for each test case.')
      return
    }
    if (cases.some((testCase) => Array.from(testCase.name).length > 300 || Array.from(testCase.steps).length > 1_000 || Array.from(testCase.expectedResult).length > 1_000)) {
      setError('Check names must be at most 300 characters; steps and expected results must each be at most 1,000.')
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSave({
        id: initial.id || crypto.randomUUID(),
        name: name.trim(),
        whenToUse: whenToUse.trim(),
        preconditions: preconditions.trim(),
        testCases: cases,
        checks: cases.map((testCase) => testCase.name),
      })
    } catch {
      setError('Template could not be saved. Your changes are still here; try again.')
    } finally {
      setSaving(false)
    }
  }

  function updateCase(id: string, field: 'name' | 'steps' | 'expectedResult', value: string) {
    setTestCases((items) =>
      items.map((testCase) => (testCase.id === id ? { ...testCase, [field]: value } : testCase)),
    )
    setError('')
  }

  return (
    <div className="qa-report-editor">
      <h1>{initial.id ? 'Edit template' : 'New template'}</h1>
      {saving && <p role="status">{confirmDelete ? 'Deleting template…' : 'Saving template…'}</p>}
      <form className="qa-report-form" onSubmit={save}>
        <div className="qa-report-short-fields">
          <div className="qa-report-field-row">
            <label htmlFor="template-name">Name</label>
            <Input
              id="template-name"
              required
              maxLength={120}
              value={name}
              onChange={(event) => {
                setName(event.target.value)
                setError('')
              }}
            />
          </div>
          <div className="qa-report-field-row template-setting-row">
            <label htmlFor="template-when">When to use</label>
            <Textarea
              id="template-when"
              rows={2}
              maxLength={1_000}
              value={whenToUse}
              onChange={(event) => setWhenToUse(event.target.value)}
            />
          </div>
          <div className="qa-report-field-row template-setting-row">
            <label htmlFor="template-preconditions">Preconditions</label>
            <Textarea
              id="template-preconditions"
              rows={2}
              maxLength={1_000}
              value={preconditions}
              onChange={(event) => setPreconditions(event.target.value)}
            />
          </div>
        </div>
        <section className="template-test-cases" aria-labelledby="template-cases-heading">
          <div className="template-cases-heading">
            <h2 id="template-cases-heading">Test cases</h2>
            <Button
              color="secondary"
              variant="ghost"
              size="sm"
              type="button"
              disabled={saving || testCases.length >= 30}
              onClick={() => {
                const id = crypto.randomUUID()
                setTestCases((items) => [...items, { id, name: '', steps: '', expectedResult: '' }])
                setExpandedCases((items) => new Set([...items, id]))
                setError('')
              }}
            >
              <Plus />
              Add test case
            </Button>
          </div>
          <div className="template-case-group">
            {testCases.map((testCase, index) => (
              <Disclosure
                className="template-case"
                label={<span>{testCase.name.trim() || `Test case ${index + 1}`}</span>}
                key={testCase.id}
                open={expandedCases.has(testCase.id)}
                onToggle={(event) => {
                  const open = event.currentTarget.open
                  setExpandedCases((items) => {
                    if (items.has(testCase.id) === open) return items
                    const next = new Set(items)
                    if (open) next.add(testCase.id)
                    else next.delete(testCase.id)
                    return next
                  })
                }}
              >
                <div className="template-case-fields">
                  <label htmlFor={`case-name-${testCase.id}`}>Check name</label>
                  <Input
                    id={`case-name-${testCase.id}`}
                    value={testCase.name}
                    maxLength={300}
                    onChange={(event) => updateCase(testCase.id, 'name', event.target.value)}
                  />
                  <label htmlFor={`case-steps-${testCase.id}`}>Steps</label>
                  <Textarea
                    id={`case-steps-${testCase.id}`}
                    rows={3}
                    maxLength={1_000}
                    value={testCase.steps}
                    onChange={(event) => updateCase(testCase.id, 'steps', event.target.value)}
                  />
                  <label htmlFor={`case-result-${testCase.id}`}>Expected result</label>
                  <Textarea
                    id={`case-result-${testCase.id}`}
                    rows={2}
                    maxLength={1_000}
                    value={testCase.expectedResult}
                    onChange={(event) => updateCase(testCase.id, 'expectedResult', event.target.value)}
                  />
                  <Button
                    color="danger"
                    variant="ghost"
                    size="sm"
                    type="button"
                    disabled={saving}
                    onClick={() => {
                      setTestCases((items) => items.filter((item) => item.id !== testCase.id))
                      setError('')
                    }}
                  >
                    Remove test case
                  </Button>
                </div>
              </Disclosure>
            ))}
          </div>
        </section>
        {error && <p role="alert">{error}</p>}
        <div className="qa-report-actions">
          <Button color="secondary" variant="ghost" type="button" disabled={saving} onClick={onCancel}>
            Cancel
          </Button>
          <Button color="primary" type="submit" disabled={saving}>
            {saving ? 'Saving…' : 'Save template'}
          </Button>
        </div>
      </form>
      {initial.id && (
        <div>
          {confirmDelete ? (
            <div className="qa-empty-state">
              <h2>Delete this template?</h2>
              <p>Existing bug checklists keep their checks.</p>
              <div className="qa-report-actions">
                <Button color="secondary" variant="ghost" disabled={saving} onClick={() => setConfirmDelete(false)}>
                  Keep template
                </Button>
                <Button color="danger" disabled={saving} onClick={async () => {
                  setSaving(true)
                  setError('')
                  try { await onDelete() }
                  catch { setError('Template could not be deleted. Try again.') }
                  finally { setSaving(false) }
                }}>
                  {saving ? 'Deleting…' : 'Delete template'}
                </Button>
              </div>
            </div>
          ) : (
            <Button color="danger" variant="ghost" disabled={saving} onClick={() => setConfirmDelete(true)}>
              Delete template
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

export function QaTestTemplates({ items, onChange, onSaveTemplate, onDeleteTemplate, selectedId, onSelect }: TestTemplatesProps) {
  const [search, setSearch] = useState('')
  const selected = items.find((template) => template.id === selectedId)

  if (selected || selectedId === '__new')
    return (
      <TemplateEditor
        key={selectedId}
        initial={selected ?? emptyTemplate}
        onSave={async (template) => {
          if (onSaveTemplate) await onSaveTemplate(template)
          else if (onChange) onChange(
              selected
                ? items.map((item) => (item.id === selected.id ? template : item))
                : [...items, template],
            )
          else throw new Error('Template save is unavailable')
          onSelect('')
        }}
        onCancel={() => onSelect('')}
        onDelete={async () => {
          if (onDeleteTemplate) await onDeleteTemplate(selectedId)
          else if (onChange) onChange(items.filter((item) => item.id !== selectedId))
          else throw new Error('Template deletion is unavailable')
          onSelect('')
        }}
      />
    )

  const term = search.trim().toLowerCase()
  const visible = items.filter((template) =>
    [
      template.name,
      template.whenToUse,
      template.preconditions,
      ...template.checks,
      ...(template.testCases ?? []).flatMap((testCase) => [
        testCase.name,
        testCase.steps,
        testCase.expectedResult,
      ]),
    ]
      .join(' ')
      .toLowerCase()
      .includes(term),
  )

  return (
    <div className="qa-queue-view">
      <div className="qa-queue-heading">
        <h1>Test templates</h1>
        <div className="qa-queue-controls">
          <Input
            className="qa-library-search"
            type="search"
            pill
            startAdornment={<Search />}
            aria-label="Find template"
            placeholder="Search templates"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <Button color="primary" onClick={() => onSelect('__new')}>
            <Plus />
            New template
          </Button>
        </div>
      </div>
      <div className="qa-ticket-columns">
        <span>Name</span>
        <span>Checks</span>
      </div>
      <div className="qa-ticket-list">
        {visible.map((template) => (
          <button className="qa-ticket-row" key={template.id} onClick={() => onSelect(template.id)}>
            <span className="qa-library-item-icon" aria-hidden="true">
              <FileDocument />
            </span>
            <span>{template.name}</span>
            <span className="muted">{template.checks.length}</span>
          </button>
        ))}
      </div>
      {!visible.length && (
        <div className="qa-empty-state">
          <h2>{items.length ? 'No matching templates' : 'No saved templates'}</h2>
          <p>{items.length ? 'Try another search.' : 'Save a checklist to use when verifying a bug.'}</p>
        </div>
      )}
    </div>
  )
}
