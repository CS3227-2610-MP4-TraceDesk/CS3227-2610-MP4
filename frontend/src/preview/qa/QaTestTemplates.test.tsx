import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QaTestTemplates } from './QaTestTemplates'
import { sampleTemplates } from './qaTemplateFixtures'

afterEach(cleanup)

describe('QA test template persistence', () => {
  it('keeps the editor open and preserves text after a failed save, then closes after success', async () => {
    const onSaveTemplate = vi.fn()
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce(undefined)
    const onSelect = vi.fn()
    render(<QaTestTemplates
      items={sampleTemplates}
      onSaveTemplate={onSaveTemplate}
      selectedId="export-checks"
      onSelect={onSelect}
    />)

    const name = screen.getByLabelText('Name')
    fireEvent.change(name, { target: { value: 'Updated export checks' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save template' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Your changes are still here')
    expect(name).toHaveValue('Updated export checks')
    expect(onSelect).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Save template' }))
    await waitFor(() => expect(onSelect).toHaveBeenCalledWith(''))
    expect(onSaveTemplate).toHaveBeenCalledTimes(2)
    expect(onSaveTemplate).toHaveBeenCalledWith(expect.objectContaining({ name: 'Updated export checks' }))
  })
})
