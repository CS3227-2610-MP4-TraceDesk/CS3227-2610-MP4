export type TestTemplate = {
  id: string
  name: string
  checks: string[]
  whenToUse?: string
  preconditions?: string
  testCases?: { id: string; name: string; steps: string; expectedResult: string }[]
}

export const sampleTemplates: TestTemplate[] = [
  {
    id: 'export-checks',
    name: 'Export checks',
    checks: ['Export with no date filter', 'Export with a valid date range', 'Verify CSV columns'],
    whenToUse: 'A change affects CSV export or payment date filtering.',
    preconditions: 'A test account has payments across at least two dates.',
    testCases: [
      {
        id: 'export-no-filter',
        name: 'Export with no date filter',
        steps: 'Clear both date fields and export payments as CSV.',
        expectedResult: 'The download includes every accessible payment.',
      },
      {
        id: 'export-date-range',
        name: 'Export with a valid date range',
        steps: 'Choose a date range containing payments and export as CSV.',
        expectedResult: 'Only payments within the selected range are included.',
      },
      {
        id: 'export-columns',
        name: 'Verify CSV columns',
        steps: 'Open the downloaded CSV and inspect the header and a payment row.',
        expectedResult: 'Each row has the expected columns and values in matching order.',
      },
    ],
  },
  {
    id: 'search-checks',
    name: 'Search checks',
    checks: ['Query retained across pagination', 'Clearing the query restores results'],
    whenToUse: 'A change affects payment search or pagination.',
    preconditions: 'A test account has enough matching payments for multiple pages.',
    testCases: [
      {
        id: 'search-pagination',
        name: 'Query retained across pagination',
        steps: 'Search for a payment term and go to the next page of results.',
        expectedResult: 'The query stays active and all displayed results match it.',
      },
      {
        id: 'search-clear',
        name: 'Clearing the query restores results',
        steps: 'Search for a payment term, then clear the search field.',
        expectedResult: 'The full accessible payment list is restored.',
      },
    ],
  },
]
