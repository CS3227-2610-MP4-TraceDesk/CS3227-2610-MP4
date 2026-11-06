import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

MODULE = Path(__file__).resolve().parents[1] / 'agentic_measurements.py'
spec = importlib.util.spec_from_file_location('measurements', MODULE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def record():
    return dict(kind='application', source='fixture', run_id='test', case_id='R01',
                variant='concise', revision='test-revision', model='fake-model',
                prompt_version='v1', started_at='2026-11-06T00:00:00Z',
                ended_at='2026-11-06T00:00:01Z', outcome='success',
                context_refs=['title', 'description'], agent_role=None,
                security_result='unknown', evidence_ref='test-fixture',
                token_source='provider', repetition=1, input_tokens=0,
                output_tokens=None, quality_score=4)


class MeasurementsTest(unittest.TestCase):
    def load_text(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'data.jsonl'
            path.write_text(text)
            return m.load(path)

    def test_valid_zero_unknown_and_empty(self):
        rows, digest = self.load_text(json.dumps(record()))
        html = m.render(rows, digest)
        self.assertIn('0.0 (n=1)', html)
        self.assertIn('output_tokens: 1/1', html)
        self.assertIn('TEST FIXTURES', html)
        self.assertNotIn('<h2>Observed measurements', html)
        self.assertIn('Application experiments have not run', html)
        self.assertIn('No observations collected', m.render([]))

    def test_bad_fields_and_metrics(self):
        mutations = [{'prompt': 'CANARY-SECRET'}, {'input_tokens': True},
                     {'input_tokens': -1}, {'input_tokens': float('nan')},
                     {'input_tokens': float('inf')}, {'input_tokens': 1.2},
                     {'quality_score': 7}, {'outcome': 'timeout'},
                     {'token_source': 'unknown'}, {'context_refs': ['../secret']},
                     {'evidence_ref': 'https://example.test'}, {'model': '<script>CANARY</script>'},
                     {'context_refs': ['a'] * 129}, {'variant': 'a' * 257},
                     {'ended_at': '2026-11-05T00:00:00Z'}, {'ended_at': '2026-99-06T00:00:00Z'},
                     {'ended_at': '2026-11-06T00:00:00+01:00'}, {'repetition': False}]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                row = record(); row.update(mutation)
                with self.assertRaises(ValueError) as caught:
                    self.load_text(json.dumps(row))
                self.assertNotIn('CANARY', str(caught.exception))

    def test_duplicates_malformed_nonobject_and_line_limit(self):
        row = json.dumps(record())
        for text in [row+'\n'+row, '{"kind":"application","kind":"application"}',
                     '[1]', '{"secret":"CANARY', ' ' * 16385 + row]:
            with self.subTest(text=text[:30]):
                with self.assertRaises(ValueError) as caught:
                    self.load_text(text)
                self.assertNotIn('CANARY', str(caught.exception))

    def test_count_and_file_bounds_and_encoding(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'data'
            p.write_bytes(b'\xff')
            with self.assertRaisesRegex(ValueError, 'UTF-8'): m.load(p)
            p.write_bytes(b' ' * (10 * 1024 * 1024 + 1))
            with self.assertRaises(ValueError): m.load(p)
        rows = []
        for i in range(10001):
            r = record(); r['case_id'] = f'case-{i}'; rows.append(json.dumps(r))
        with self.assertRaisesRegex(ValueError, '10000'): self.load_text('\n'.join(rows))

    def test_read_is_bounded_even_when_file_metadata_is_stale(self):
        handle = MagicMock()
        handle.read.return_value = b' ' * (10 * 1024 * 1024 + 1)
        context = MagicMock()
        context.__enter__.return_value = handle
        with patch.object(m.os, 'open', return_value=99), patch.object(m.os, 'fdopen', return_value=context), patch.object(m.os, 'fstat') as info:
            info.return_value.st_mode = 0o100644
            info.return_value.st_size = 1
            with self.assertRaisesRegex(ValueError, '10 MiB'):
                m.load(Path('not-read-from-disk'))
        handle.read.assert_called_once_with(10 * 1024 * 1024 + 1)

    def test_escape_even_if_renderer_called_directly(self):
        row = record(); row['variant'] = '<script>alert(1)</script>'
        html = m.render([row])
        self.assertNotIn('<script>', html)
        self.assertIn('&lt;script&gt;', html)
        self.assertNotIn('href=', html)

    def test_development_timeline_null_model_and_mixed_groups(self):
        row = record()
        row.update(kind='development', model=None, agent_role='reviewer',
                   prompt_version=None, token_source='unknown', input_tokens=None)
        m.validate(row)
        other = copy.deepcopy(row); other['model'] = 'other-model'; other['case_id'] = 'other'
        html = m.render([row, other])
        self.assertIn('Agent intervals', html)
        self.assertIn('unknown (n=0)', html)
        self.assertIn('other-model', html)
        self.assertIn('not controlled comparisons', html)
        self.assertIn('0 plotted; 1 missing', html)

    def test_invalid_preserves_output_and_digest_tracks_input(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'data'; out = Path(directory) / 'report'
            p.write_text(json.dumps(record())); out.write_text('previous')
            _, digest = m.load(p)
            result = subprocess.run([sys.executable, str(MODULE), str(p), '--output', str(out)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0)
            self.assertIn(digest, out.read_text())
            prior = out.read_bytes()
            p.write_text('{"prompt":"CANARY')
            result = subprocess.run([sys.executable, str(MODULE), str(p), '--output', str(out)], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn('CANARY', result.stderr)
            self.assertEqual(prior, out.read_bytes())
            p.write_text(json.dumps(record())+'\n')
            self.assertNotEqual(digest, m.load(p)[1])
            result = subprocess.run([sys.executable, str(MODULE), str(p), '--output', str(p)], capture_output=True)
            self.assertNotEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main()
