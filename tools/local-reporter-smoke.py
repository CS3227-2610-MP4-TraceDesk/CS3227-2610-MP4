#!/usr/bin/env python3
"""Run the local setup journey against an explicitly owned disposable dev database."""
import json
import os
from pathlib import Path
import signal
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def child_environment():
    return {key: os.environ[key] for key in ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'LC_ALL', 'TMPDIR', 'TZ')
            if key in os.environ}


def run(command, environment, directory=ROOT, timeout=180):
    result = subprocess.run(command, cwd=directory, env=environment, timeout=timeout,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if result.returncode != 0:
        raise RuntimeError('LOCAL_SMOKE_STEP_FAILED')


def main():
    # This verification helper never uses the user's normal Compose database.
    if (os.environ.get('TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE') != 'true'
            or os.environ.get('TRACEDESK_LOCAL_CONFIRM') != 'true'
            or os.environ.get('TRACEDESK_LOCAL_JDBC_URL') != 'jdbc:postgresql://127.0.0.1:55419/tracedesk_dev'
            or os.environ.get('TRACEDESK_LOCAL_DATABASE_USER') != 'tracedesk_dev'
            or not os.environ.get('TRACEDESK_LOCAL_DATABASE_PASSWORD')):
        raise RuntimeError('EXPLICIT_DISPOSABLE_DATABASE_REQUIRED')
    for port in (18089, 4177, 4178):
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', port))
    setup_env = child_environment()
    for key in ('TRACEDESK_LOCAL_CONFIRM', 'TRACEDESK_LOCAL_JDBC_URL',
                'TRACEDESK_LOCAL_DATABASE_USER', 'TRACEDESK_LOCAL_DATABASE_PASSWORD'):
        setup_env[key] = os.environ[key]
    setup_env['AI_ENABLED'] = 'false'
    setup_env['TRACEDESK_LOCAL_SMOKE_CONFIRM_DISPOSABLE'] = 'true'
    run(['./gradlew', ':backend:auth-app:localReporterSetup', '--args=provision', '--no-daemon', '--console=plain'], setup_env)
    run(['./gradlew', ':backend:auth-app:localReporterSetup', '--args=provision', '--no-daemon', '--console=plain'], setup_env)
    run(['npm', 'run', 'build'], child_environment(), ROOT / 'frontend')
    with tempfile.TemporaryDirectory(prefix='tracedesk-local-setup-smoke-') as temp:
        store = Path(temp) / 'localhost.p12'
        password = uuid.uuid4().hex
        keytool = str(Path(os.environ['JAVA_HOME']) / 'bin/keytool') if os.environ.get('JAVA_HOME') else 'keytool'
        key_env = child_environment()
        key_env['TRACEDESK_SMOKE_KEYSTORE_PASSWORD'] = password
        run([keytool, '-genkeypair', '-alias', 'local-smoke', '-keyalg', 'RSA', '-keysize', '2048',
             '-validity', '2', '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1',
             '-storetype', 'PKCS12', '-keystore', str(store),
             '-storepass:env', 'TRACEDESK_SMOKE_KEYSTORE_PASSWORD'], key_env)
        backend_env = child_environment()
        backend_env.update({
            'AI_ENABLED': 'false', 'SPRING_PROFILES_ACTIVE': 'local',
            'TRACEDESK_DATABASE_URL': setup_env['TRACEDESK_LOCAL_JDBC_URL'],
            'TRACEDESK_DATABASE_USER': setup_env['TRACEDESK_LOCAL_DATABASE_USER'],
            'TRACEDESK_DATABASE_PASSWORD': setup_env['TRACEDESK_LOCAL_DATABASE_PASSWORD'],
            'SERVER_ADDRESS': '127.0.0.1', 'SERVER_PORT': '18089',
            'SERVER_SSL_ENABLED': 'true', 'SERVER_SSL_KEY_STORE': str(store),
            'SERVER_SSL_KEY_STORE_PASSWORD': password, 'SERVER_SSL_KEY_STORE_TYPE': 'PKCS12',
            'SERVER_SSL_KEY_ALIAS': 'local-smoke', 'LOGGING_LEVEL_ROOT': 'WARN',
        })
        foreign_project = str(uuid.uuid4())
        run(['./gradlew', ':backend:auth-app:localReporterBrowserProbe', '--args=create ' + foreign_project, '--no-daemon', '--console=plain'], setup_env)
        backend = None
        try:
            backend = subprocess.Popen(['./gradlew', ':backend:auth-app:bootRun', '--no-daemon', '--console=plain'],
                                       cwd=ROOT, env=backend_env, start_new_session=True,
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            context = ssl.create_default_context()
            context.check_hostname = False
            context.verify_mode = ssl.CERT_NONE  # Generated localhost-only verification certificate.
            deadline = time.monotonic() + 120
            while True:
                if backend.poll() is not None:
                    raise RuntimeError('LOCAL_BACKEND_STOPPED')
                try:
                    with urllib.request.urlopen('https://127.0.0.1:18089/health/ready', context=context, timeout=2) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                if time.monotonic() > deadline:
                    raise RuntimeError('LOCAL_BACKEND_READY_TIMEOUT')
                time.sleep(0.25)
            browser_env = child_environment()
            browser_env.update({
                'TRACEDESK_REAL_BACKEND_TEST': 'true', 'TRACEDESK_LOCAL_SETUP_TEST': 'true',
                'TRACEDESK_LOCAL_SETUP_FOREIGN_PROJECT': foreign_project,
                'TRACEDESK_LOCAL_SETUP_CREDENTIAL_FILE': str(ROOT / '.local/local-reporter.json'),
                'TRACEDESK_LOCAL_SETUP_RESULT': str(ROOT / 'frontend/e2e-results/local-setup-result.json'),
            })
            result_path = Path(browser_env['TRACEDESK_LOCAL_SETUP_RESULT'])
            result_path.unlink(missing_ok=True)
            try:
                run(['npm', 'run', 'test:e2e', '--', '--project=local-reporter-setup'], browser_env, ROOT / 'frontend')
            except RuntimeError:
                if result_path.is_file():
                    result = json.loads(result_path.read_text())
                    phase = result.get('phase')
                    if phase in {'LOGIN', 'DISCOVERY', 'DRAFT', 'REPORT', 'DENIAL', 'COMPLETE'}:
                        print('Local browser stopped at phase:', phase, file=sys.stderr)
                raise
        finally:
            if backend is not None and backend.poll() is None:
                os.killpg(backend.pid, signal.SIGTERM)
                try:
                    backend.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    os.killpg(backend.pid, signal.SIGKILL)
                    backend.wait(timeout=5)
            run(['./gradlew', ':backend:auth-app:localReporterBrowserProbe', '--args=remove ' + foreign_project, '--no-daemon', '--console=plain'], setup_env)
    print('Local Reporter setup browser journey: PASSED')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (Exception, KeyboardInterrupt) as error:
        safe = {'EXPLICIT_DISPOSABLE_DATABASE_REQUIRED', 'LOCAL_SMOKE_STEP_FAILED',
                'LOCAL_BACKEND_STOPPED', 'LOCAL_BACKEND_READY_TIMEOUT'}
        category = str(error) if isinstance(error, RuntimeError) and str(error) in safe else 'SAFE_STOP'
        print('Local Reporter setup browser journey:', category, file=sys.stderr)
        sys.exit(1)
