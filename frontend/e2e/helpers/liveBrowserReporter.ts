import type { Reporter } from '@playwright/test/reporter'

/** The live project writes only its allowlisted JSON result; this reporter emits no test details. */
export default class LiveBrowserReporter implements Reporter {
  onBegin() { void 0 }
  onTestEnd() { void 0 }
  onEnd() { void 0 }
}
