import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE_PATH = Path(__file__).resolve().parents[1] / 'scripts' / 'ui_research.py'
spec = importlib.util.spec_from_file_location('ui_research', MODULE_PATH)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class WorkflowTests(unittest.TestCase):
    def test_runs_stages_in_order_and_hands_off_results(self):
        with tempfile.TemporaryDirectory() as directory:
            calls = []
            def agent(role, prompt, timeout, model):
                calls.append((role, prompt))
                return f'{role} output'
            with patch.object(runner, 'invoke_agent', side_effect=agent):
                result = runner.run_workflow('Improve sidebar', Path(directory), 30)
            self.assertEqual([c[0] for c in calls], ['researcher', 'designer', 'reviewer', 'coordinator'])
            self.assertIn('researcher output', calls[1][1])
            self.assertIn('designer output', calls[2][1])
            self.assertIn('reviewer output', calls[3][1])
            self.assertEqual(result['status'], 'completed')
            self.assertTrue((Path(directory) / 'report.md').exists())

    def test_failure_stops_following_stages_and_records_it(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(runner, 'invoke_agent', side_effect=runner.StageError('unavailable')) as agent:
                result = runner.run_workflow('Improve sidebar', Path(directory), 30)
            self.assertEqual(agent.call_count, 1)
            self.assertEqual(result['status'], 'failed')
            self.assertEqual(result['failed_stage'], 'researcher')
            self.assertTrue((Path(directory) / 'run.json').exists())
            self.assertFalse((Path(directory) / 'report.md').exists())

    def test_empty_task_and_invalid_timeout_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            for task, timeout in [('', 30), ('x', 0)]:
                with self.assertRaises(ValueError):
                    runner.run_workflow(task, Path(directory), timeout)

    def test_rejects_nonempty_output_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / 'existing.txt').write_text('keep', encoding='utf-8')
            with self.assertRaises(ValueError):
                runner.run_workflow('test', Path(directory), 30)

    def test_cli_failure_and_timeout_do_not_expose_stderr(self):
        import subprocess
        with patch.object(runner.shutil, 'which', return_value='codex'):
            failed = subprocess.CompletedProcess([], 1, '', 'private error detail')
            with patch.object(runner.subprocess, 'run', return_value=failed):
                with self.assertRaises(runner.StageError) as error:
                    runner.invoke_agent('researcher', 'task', 1)
                self.assertNotIn('private error detail', str(error.exception))
            with patch.object(runner.subprocess, 'run', side_effect=subprocess.TimeoutExpired('codex', 1)):
                with self.assertRaises(runner.StageError):
                    runner.invoke_agent('researcher', 'task', 1)

    def test_explicit_context_is_bounded_and_included(self):
        self.assertIn('UI research', runner.load_context(['workflow/ui-research/README.md']))
        for paths in [['../secret.md'], ['.env'], ['workflow/ui-research/README.md'] * 6]:
            with self.assertRaises(ValueError):
                runner.load_context(paths)

    def test_selected_context_reaches_each_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            calls = []
            def agent(role, prompt, timeout, model):
                calls.append(prompt)
                return 'output'
            with patch.object(runner, 'invoke_agent', side_effect=agent):
                result = runner.run_workflow('test', Path(directory), 30,
                                             context_paths=['workflow/ui-research/README.md'])
            self.assertEqual(result['status'], 'completed')
            self.assertTrue(all('SELECTED REPOSITORY CONTEXT' in prompt and 'UI research' in prompt for prompt in calls))


if __name__ == '__main__':
    unittest.main()
