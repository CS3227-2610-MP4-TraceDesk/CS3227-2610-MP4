import { execFileSync, spawn, type ChildProcess } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'

export interface ReporterFixtureMetadata {
  projectId: string
  deniedProjectId: string
  reporterId: string
  login: string
  nonmemberLogin: string
  developerLogin: string
  qaLogin: string
  developerId: string
  assignedReportId: string
  unassignedReportId: string
}

const fixturePassword = 'Synthetic-browser-password-2026!'
const frontendDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const repositoryDirectory = path.resolve(frontendDirectory, '..')

export class ReportRealBackend {
  private directory?: string
  private child?: ChildProcess
  private metadata?: ReporterFixtureMetadata
  private stopFile = ''
  private commandFile = ''
  private ackFile = ''
  private startupError?: Error

  async start(): Promise<ReporterFixtureMetadata> {
    if (this.child) throw new Error('Reporter fixture is already running')
    for (const name of [
      'TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED',
      'TRACEDESK_TEST_JDBC_URL',
      'TRACEDESK_TEST_DATABASE_USER',
      'TRACEDESK_TEST_DATABASE_PASSWORD',
    ]) {
      if (!process.env[name]) throw new Error(`Real backend test requires ${name}`)
    }
    if (process.env.TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED !== 'true') {
      throw new Error('Real backend test requires explicit isolated-database confirmation')
    }

    this.directory = await mkdtemp(path.join(os.tmpdir(), 'tracedesk-reporter-browser-'))
    const metadataFile = path.join(this.directory, 'fixture.json')
    this.stopFile = path.join(this.directory, 'stop')
    this.commandFile = path.join(this.directory, 'command')
    this.ackFile = path.join(this.directory, 'ack')
    try {
      const gradleArgs = ['--no-daemon', '--console=plain', ':backend:auth-app:reporterBrowserFixture']
      const executable = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : './gradlew'
      const args = process.platform === 'win32' ? ['/d', '/s', '/c', 'gradlew.bat', ...gradleArgs] : gradleArgs
      this.child = spawn(executable, args, {
        cwd: repositoryDirectory,
        detached: process.platform !== 'win32',
        stdio: 'ignore',
        env: {
          ...process.env,
          TRACEDESK_BROWSER_FIXTURE_METADATA: metadataFile,
          TRACEDESK_BROWSER_FIXTURE_STOP_FILE: this.stopFile,
          TRACEDESK_BROWSER_FIXTURE_COMMAND_FILE: this.commandFile,
          TRACEDESK_BROWSER_FIXTURE_ACK_FILE: this.ackFile,
        },
      })
      this.child.once('error', (error) => { this.startupError = error })

      const deadline = Date.now() + 90_000
      while (Date.now() < deadline) {
        if (this.startupError) throw new Error('Unable to launch the owned Reporter fixture process')
        if (this.child.exitCode !== null || this.child.signalCode !== null) {
          throw new Error(`Reporter fixture exited before readiness (code ${this.child.exitCode ?? this.child.signalCode})`)
        }
        try {
          this.metadata = JSON.parse(await readFile(metadataFile, 'utf8')) as ReporterFixtureMetadata
          if (!this.metadata.projectId || !this.metadata.deniedProjectId || !this.metadata.login ||
              !this.metadata.qaLogin || !this.metadata.developerId || !this.metadata.assignedReportId || !this.metadata.unassignedReportId) {
            throw new Error('Reporter fixture metadata is incomplete')
          }
          return this.metadata
        } catch (error) {
          if (error instanceof SyntaxError) throw error
        }
        await new Promise((resolve) => setTimeout(resolve, 250))
      }
      throw new Error('Reporter fixture did not become ready within 90 seconds')
    } catch (error) {
      await this.stop()
      throw error
    }
  }

  get credentials(): { password: string } {
    if (!this.metadata) throw new Error('Reporter fixture has not started')
    return { password: fixturePassword }
  }

  async invalidateReporterSessions(): Promise<void> {
    await this.sendFixtureCommand('invalidate-reporter-session')
  }

  async invalidateDeveloperSessions(): Promise<void> {
    await this.sendFixtureCommand('invalidate-developer-session')
  }

  async advanceDeveloperReportVersion(): Promise<void> {
    await this.sendFixtureCommand('advance-developer-report-version')
  }

  async reassignDeveloperReport(): Promise<void> {
    await this.sendFixtureCommand('reassign-developer-report')
  }

  async setAuditAvailable(available: boolean): Promise<void> {
    await this.sendFixtureCommand(available ? 'audit-on' : 'audit-off')
  }

  async setAiAvailable(available: boolean): Promise<void> {
    await this.sendFixtureCommand(available ? 'ai-on' : 'ai-off')
  }

  private async sendFixtureCommand(command: string): Promise<void> {
    if (!this.metadata || !this.child) throw new Error('Reporter fixture is not running')
    await rm(this.ackFile, { force: true })
    await writeFile(this.commandFile, command)
    const deadline = Date.now() + 10_000
    while (Date.now() < deadline) {
      const result = await readFile(this.ackFile, 'utf8').catch(() => '')
      if (result === 'done') return
      if (result === 'invalid') throw new Error('Reporter fixture rejected a local control command')
      if (this.child.exitCode !== null || this.child.signalCode !== null) {
        throw new Error('Reporter fixture exited during its local control command')
      }
      await new Promise((resolve) => setTimeout(resolve, 100))
    }
    throw new Error('Reporter fixture did not acknowledge local session invalidation')
  }

  async stop(): Promise<void> {
    if (this.directory) {
      const child = this.child
      if (child && child.exitCode === null && child.signalCode === null) await writeFile(this.stopFile, 'stop')
      if (child) {
        const stopped = child.exitCode !== null || child.signalCode !== null || await Promise.race([
          new Promise<boolean>((resolve) => child.once('exit', () => resolve(true))),
          new Promise<boolean>((resolve) => setTimeout(() => resolve(false), 12_000)),
        ])
        if (!stopped && child.pid) {
          try {
            if (process.platform === 'win32') child.kill('SIGTERM')
            else process.kill(-child.pid, 'SIGTERM')
          } catch { /* The owned process group may already have exited. */ }
          if (child.exitCode === null && child.signalCode === null) {
            await Promise.race([
              new Promise<void>((resolve) => child.once('exit', () => resolve())),
              new Promise<void>((resolve) => setTimeout(resolve, 3_000)),
            ])
            if (child.exitCode === null && child.signalCode === null && child.pid) {
              try {
                if (process.platform === 'win32') child.kill('SIGKILL')
                else process.kill(-child.pid, 'SIGKILL')
              } catch { /* The owned process group may already have exited. */ }
            }
          }
        }
      }
      this.child = undefined
      await rm(this.directory, { recursive: true, force: true })
      this.directory = undefined
      this.metadata = undefined
    }
  }
}

export async function captureReporterEvidence(page: Page, browserVersion: string, name: string) {
  return captureRealBackendEvidence(page, browserVersion, name, 'reporter-real')
}

export async function captureDeveloperEvidence(page: Page, browserVersion: string, name: string) {
  return captureRealBackendEvidence(page, browserVersion, name, 'developer-real')
}

async function captureRealBackendEvidence(page: Page, browserVersion: string, name: string, directory: string) {
  const evidenceDirectory = path.resolve(frontendDirectory, `e2e-results/${directory}`)
  await mkdir(evidenceDirectory, { recursive: true })
  await page.evaluate(() => {
    window.scrollTo(0, 0)
    document.documentElement.scrollTop = 0
    document.body.scrollTop = 0
    const main = document.querySelector('main')
    if (main) main.scrollTop = 0
  })
  await page.evaluate(async () => {
    const animations = document.querySelector('.app-frame')?.getAnimations({ subtree: true }) ?? []
    await Promise.all(animations.map((animation) => animation.finished.catch(() => undefined)))
  })
  const layout = await page.evaluate(() => {
    const root = document.documentElement
    const shell = document.querySelector('main') ?? document.body
    const form = document.querySelector('form')
    const main = document.querySelector('main')
    const formHeading = document.querySelector('.report-form-heading h1')
    const formGroup = document.querySelector('.report-form-group')
    const firstLabel = document.querySelector('.report-field > label')
    const bounds = (element: Element | null) => {
      if (!element) return null
      const rect = element.getBoundingClientRect()
      const style = getComputedStyle(element)
      return {
        x: Math.round(rect.x), y: Math.round(rect.y), width: Math.round(rect.width),
        height: Math.round(rect.height), maxWidth: style.maxWidth,
      }
    }
    return {
      viewport: { width: innerWidth, height: innerHeight },
      document: { width: root.scrollWidth, height: root.scrollHeight },
      scrollTop: { document: document.scrollingElement?.scrollTop ?? 0, main: main?.scrollTop ?? 0 },
      horizontalOverflow: root.scrollWidth > innerWidth,
      main: bounds(shell),
      appFrame: bounds(document.querySelector('.app-frame')),
      rail: bounds(document.querySelector('.icon-tray')),
      sidebar: bounds(document.querySelector('.shell-sidebar')),
      shellHeader: bounds(document.querySelector('.shell-header')),
      form: bounds(form),
      submit: bounds(form?.querySelector('button[type="submit"]') ?? null),
      formPadding: form ? getComputedStyle(form).padding.split(' ').map((value) => Number.parseFloat(value)) : null,
      formHeadingFontSize: formHeading ? Number.parseFloat(getComputedStyle(formHeading).fontSize) : null,
      fieldLabelFontWeight: firstLabel ? Number.parseFloat(getComputedStyle(firstLabel).fontWeight) : null,
      formGroupBorderWidth: formGroup ? Number.parseFloat(getComputedStyle(formGroup).borderTopWidth) : null,
      formGroupRadius: formGroup ? Number.parseFloat(getComputedStyle(formGroup).borderTopLeftRadius) : null,
      theme: root.dataset.theme ?? getComputedStyle(root).colorScheme,
      reducedMotion: matchMedia('(prefers-reduced-motion: reduce)').matches,
      shellTransition: getComputedStyle(document.querySelector('.app-frame') ?? shell).transition,
    }
  })
  const revision = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: repositoryDirectory, encoding: 'utf8' }).trim()
  const candidatePathspec = ['.', ':(exclude)workflow/**']
  const trackedDiff = execFileSync(
    'git',
    ['diff', '--binary', 'HEAD', '--', ...candidatePathspec],
    { cwd: repositoryDirectory, encoding: 'buffer' },
  )
  const untrackedPaths = execFileSync(
    'git',
    ['ls-files', '--others', '--exclude-standard', '-z', '--', ...candidatePathspec],
    { cwd: repositoryDirectory, encoding: 'buffer' },
  ).toString('utf8').split('\0').filter(Boolean).sort()
  const candidateManifest = {
    version: 1,
    trackedDiffSha256: createHash('sha256').update(trackedDiff).digest('hex'),
    untrackedFiles: await Promise.all(untrackedPaths.map(async (relativePath) => ({
      path: relativePath,
      sha256: createHash('sha256')
        .update(await readFile(path.resolve(repositoryDirectory, relativePath)))
        .digest('hex'),
    }))),
  }
  const candidateDigest = createHash('sha256')
    .update(JSON.stringify(candidateManifest))
    .digest('hex')
  const metadata = {
    capturedAtUtc: new Date().toISOString(),
    revision,
    candidateSha256: candidateDigest,
    candidateDigestScope: 'HEAD-relative staged/unstaged source/test/config diff plus untracked, non-ignored candidate files; workflow records excluded to permit recording the result',
    candidateManifest,
    browser: 'Chromium',
    browserVersion,
    platform: `${os.platform()} ${os.release()}`,
    font: 'system UI stack',
    ...layout,
  }
  const file = path.join(evidenceDirectory, `${name}.json`)
  await writeFile(file, `${JSON.stringify(metadata, null, 2)}\n`)
  await page.screenshot({ path: path.join(evidenceDirectory, `${name}.png`), fullPage: true })
  return metadata
}
