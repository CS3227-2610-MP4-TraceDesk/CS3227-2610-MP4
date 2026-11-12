import { useState } from 'react'
import { QaTestTemplates } from '../preview/qa/QaTestTemplates'
import type { TestTemplate } from '../preview/qa/qaTemplateFixtures'
import { ReportApiError } from '../reports/reportApi'
import { createQaTemplate, deleteQaTemplate, type QaTemplateRecord, updateQaTemplate } from './qaTemplatesApi'

type QaTemplateLibraryProps = {
  projectId: string
  templates: QaTemplateRecord[]
  onTemplatesChange: (templates: QaTemplateRecord[]) => void
  selectedId: string
  onSelect: (id: string) => void
  onSessionExpired: () => void
}

export function QaTemplateLibrary({ projectId, templates, onTemplatesChange, selectedId, onSelect, onSessionExpired }: QaTemplateLibraryProps) {
  const [error, setError] = useState('')

  function reportError(cause: unknown): never {
    if (cause instanceof ReportApiError && cause.status === 401) onSessionExpired()
    setError(cause instanceof ReportApiError && cause.code === 'STALE_VERSION'
      ? 'This template changed elsewhere. Refresh the template list and try again.'
      : 'The template could not be saved. Your changes are still here; try again.')
    throw cause
  }

  async function saveTemplate(template: TestTemplate): Promise<void> {
    setError('')
    try {
      const existing = templates.find(item => item.id === template.id)
      const saved = existing
        ? await updateQaTemplate(projectId, existing.id, existing.version, template)
        : await createQaTemplate(projectId, template)
      onTemplatesChange(existing
        ? templates.map(item => item.id === saved.id ? saved : item)
        : [...templates, saved])
    } catch (cause) { reportError(cause) }
  }

  async function removeTemplate(id: string): Promise<void> {
    setError('')
    try {
      const existing = templates.find(item => item.id === id)
      if (!existing) throw new ReportApiError(404, 'NOT_FOUND')
      await deleteQaTemplate(projectId, existing.id, existing.version)
      onTemplatesChange(templates.filter(item => item.id !== id))
    } catch (cause) { reportError(cause) }
  }

  return <div className="qa-template-library">
    {error && <p role="alert">{error}</p>}
    <QaTestTemplates
      items={templates}
      selectedId={selectedId}
      onSelect={onSelect}
      onSaveTemplate={saveTemplate}
      onDeleteTemplate={removeTemplate}
    />
  </div>
}
