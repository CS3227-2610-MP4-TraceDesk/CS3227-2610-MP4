import { beforeEach, describe, expect, it } from 'vitest'
import { readCsrfToken } from './csrf'

describe('readCsrfToken', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
  })

  it('returns the decoded token among unrelated cookies', () => {
    document.cookie = 'theme=dark; Path=/'
    document.cookie = 'XSRF-TOKEN=alpha%2Fbeta%3D; Path=/'
    expect(readCsrfToken()).toBe('alpha/beta=')
  })

  it('returns undefined when no token exists', () => {
    expect(readCsrfToken()).toBeUndefined()
  })
})
