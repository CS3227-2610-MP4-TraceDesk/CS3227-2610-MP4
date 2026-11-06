#!/usr/bin/env python3
"""Validate sanitized experiment metadata and render an offline evidence report."""
import argparse
import hashlib
import os
import tempfile
from datetime import datetime
from html import escape
import json
import math
from pathlib import Path
import re
import statistics
import stat

REQUIRED = {'kind', 'source', 'run_id', 'case_id', 'variant', 'revision', 'model',
            'prompt_version', 'started_at', 'ended_at', 'outcome', 'context_refs',
            'agent_role', 'security_result', 'evidence_ref', 'token_source', 'repetition'}
NUMBERS = {'input_tokens', 'output_tokens', 'quality_score', 'calls', 'retries', 'context_bytes'}
ENUMS = {'kind': {'application', 'development'}, 'source': {'observed', 'fixture'},
         'outcome': {'success', 'rejected', 'timeout', 'error', 'incomplete'},
         'security_result': {'pass', 'fail', 'unknown'},
         'token_source': {'provider', 'host', 'unknown'}}
IDENTIFIERS = {'run_id', 'case_id', 'variant', 'revision', 'model', 'prompt_version', 'agent_role'}


def timestamp(value):
    if not isinstance(value, str) or not re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?Z', value):
        raise ValueError('timestamps must be UTC ISO strings ending in Z')
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def reference(value):
    return (isinstance(value, str) and 0 < len(value) <= 256
            and re.fullmatch(r'[A-Za-z0-9_. /#-]+', value) is not None
            and not value.startswith('/') and '..' not in value.split('/'))


def validate(row):
    if not isinstance(row, dict) or set(row) - REQUIRED - NUMBERS or REQUIRED - set(row):
        raise ValueError('record has missing or unsupported fields')
    for key, choices in ENUMS.items():
        if not isinstance(row[key], str) or row[key] not in choices:
            raise ValueError(f'invalid {key}')
    for key in IDENTIFIERS:
        value = row[key]
        if value is None and key in {'model', 'prompt_version', 'agent_role'}:
            continue
        if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_.:/-]{1,256}', value):
            raise ValueError(f'invalid {key}')
    if type(row['repetition']) is not int or not 1 <= row['repetition'] <= 10000:
        raise ValueError('invalid repetition')
    if not isinstance(row['context_refs'], list) or len(row['context_refs']) > 128 or not all(reference(v) for v in row['context_refs']):
        raise ValueError('invalid context_refs')
    if not reference(row['evidence_ref']):
        raise ValueError('invalid evidence_ref')
    if row['kind'] == 'application' and (row['prompt_version'] is None or row['agent_role'] is not None):
        raise ValueError('application requires prompt version and null agent role')
    if row['kind'] == 'development' and row['agent_role'] is None:
        raise ValueError('development requires agent role')
    if timestamp(row['ended_at']) < timestamp(row['started_at']):
        raise ValueError('end precedes start')
    for key in NUMBERS:
        value = row.get(key)
        if value is None:
            continue
        if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 10**12:
            raise ValueError(f'invalid {key}')
        if key != 'quality_score' and type(value) is not int:
            raise ValueError(f'{key} must be an integer')
    if row.get('quality_score') is not None and (row['quality_score'] > 6 or row['outcome'] != 'success'):
        raise ValueError('quality requires success and a 0–6 score')
    if row['token_source'] == 'unknown' and any(row.get(k) is not None for k in ('input_tokens', 'output_tokens')):
        raise ValueError('tokens require a known measurement source')
    expected = 'provider' if row['kind'] == 'application' else 'host'
    if row['token_source'] not in {'unknown', expected}:
        raise ValueError('token source does not match record kind')
    return row


def load(path):
    descriptor = os.open(path, os.O_RDONLY | getattr(os, 'O_NONBLOCK', 0))
    with os.fdopen(descriptor, 'rb') as handle:
        if not stat.S_ISREG(os.fstat(handle.fileno()).st_mode):
            raise ValueError('input must be a regular file')
        raw = handle.read(10 * 1024 * 1024 + 1)
    if len(raw) > 10 * 1024 * 1024:
        raise ValueError('input exceeds 10 MiB')
    try:
        content = raw.decode('utf-8')
    except UnicodeDecodeError:
        raise ValueError('input is not valid UTF-8') from None
    rows, seen = [], set()
    for number, line in enumerate(content.splitlines(), 1):
        if not line.strip():
            continue
        try:
            if len(line.encode('utf-8')) > 16384:
                raise ValueError('record exceeds 16 KiB')
            def unique_object(pairs):
                result = {}
                for key, value in pairs:
                    if key in result:
                        raise ValueError('duplicate JSON field')
                    result[key] = value
                return result
            row = validate(json.loads(line, object_pairs_hook=unique_object))
            identity = (row['kind'], row['run_id'], row['case_id'], row['variant'], row['repetition'])
            if identity in seen:
                raise ValueError('duplicate observation')
            if len(rows) >= 10000:
                raise ValueError('more than 10000 records')
            seen.add(identity)
            rows.append(row)
        except (ValueError, TypeError, OverflowError) as exc:
            # Do not echo raw malformed input or JSON parser excerpts.
            reason = 'invalid JSON' if isinstance(exc, json.JSONDecodeError) else str(exc)
            raise ValueError(f'line {number}: {reason}') from None
    return rows, hashlib.sha256(raw).hexdigest()


def duration(row):
    return (timestamp(row['ended_at']) - timestamp(row['started_at'])).total_seconds() * 1000


def median_text(values):
    return 'unknown (n=0)' if not values else f'{statistics.median(values):,.1f} (n={len(values)})'


def render(rows, digest="unknown"):
    e = lambda v: escape(str(v), quote=True)
    parts = ['<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">',
             '<title>TraceDesk measurement evidence</title><style>body{font:16px system-ui;margin:40px auto;max-width:1100px;padding:0 20px;color:#172b40;background:#f5f7fb}h1,h2{color:#163b58}table{border-collapse:collapse;width:100%;background:white}th,td{text-align:left;padding:10px;border:1px solid #d6dfe8}svg{width:100%;background:white}p{line-height:1.6}.note{padding:16px;background:#fff0ca}code{overflow-wrap:anywhere}</style>',
             '<h1>TraceDesk · Context and resource evidence</h1>',
             '<p>Offline report from validated metadata. Unknown values are excluded from medians, never treated as zero. Durations are measured intervals, not inferred billing or productivity. No causal or savings claim is generated.</p>']
    parts.append('<p>Schema/report version: 1. Input SHA-256: <code>' + e(digest) + '</code>. Records: ' + str(len(rows)) + '.</p>')
    if not any(r['kind'] == 'application' and r['source'] == 'observed' for r in rows):
        parts.append('<p class="note">Application experiments have not run in this dataset.</p>')
    if not rows:
        parts.append('<p class="note">No observations collected. Charts are pending; no experiment results are claimed.</p>')
    for source in ('observed', 'fixture'):
        subset = [r for r in rows if r['source'] == source]
        if not subset:
            continue
        parts.append(f'<h2>{"Observed measurements" if source == "observed" else "TEST FIXTURES — NOT PROJECT RESULTS"}</h2>')
        parts.append('<p>Groups retain kind, run, model, prompt version and revision. Different groups are not controlled comparisons. See the linked-by-ID evidence manifest for settings, scoring and limitations.</p>')
        groups = {}
        for row in subset:
            key = tuple(row[k] for k in ('kind', 'run_id', 'model', 'prompt_version', 'revision', 'evidence_ref', 'variant'))
            groups.setdefault(key, []).append(row)
        parts.append('<table><tr><th>Group / variant</th><th>Outcomes</th><th>Context bytes: median</th><th>Input tokens: median</th><th>Quality /6: median</th><th>Duration ms: median</th></tr>')
        for key, group in groups.items():
            outcomes = ', '.join(f'{s}: {sum(r["outcome"] == s for r in group)}' for s in sorted({r['outcome'] for r in group}))
            parts.append('<tr><td>' + e(' / '.join(str(k) for k in key)) + '</td><td>' + e(outcomes) + '</td><td>' + median_text([r['context_bytes'] for r in group if r.get('context_bytes') is not None]) + '</td><td>' + median_text([r['input_tokens'] for r in group if r.get('input_tokens') is not None]) + '</td><td>' + median_text([r['quality_score'] for r in group if r.get('quality_score') is not None]) + '</td><td>' + median_text([duration(r) for r in group]) + '</td></tr>')
        parts.append('</table>')
        for key, group in groups.items():
            parts.append('<p>Missing metrics: ' + ', '.join(e(k) + ': ' + str(sum(r.get(k) is None for r in group)) + '/' + str(len(group)) for k in sorted(NUMBERS)) + '</p>')
            parts.append('<h3>' + e(' / '.join(str(k) for k in key)) + '</h3>')
            points = [r for r in group if r.get('input_tokens') is not None and r.get('quality_score') is not None]
            parts.append(f'<p>Token–quality plot: {len(points)} plotted; {len(group)-len(points)} missing a required metric. Each dot is an observation, not an average.</p>')
            if points:
                maximum = max(1, max(r['input_tokens'] for r in points))
                parts.append('<svg role="img" aria-label="Input tokens versus quality" viewBox="0 0 800 290"><path d="M65 20V240H760" fill="none" stroke="#567"/><text x="65" y="265">0 input tokens</text><text x="570" y="265">' + e(maximum) + ' tokens</text><text x="5" y="25">6 / 6</text><text x="5" y="240">0 / 6</text>')
                for r in points:
                    parts.append(f'<circle cx="{65+695*r["input_tokens"]/maximum}" cy="{240-220*r["quality_score"]/6}" r="5" fill="#176e94"><title>{e(r["case_id"])} repetition {r["repetition"]}: {r["input_tokens"]} tokens, {r["quality_score"]}/6</title></circle>')
                parts.append('</svg>')
        dev = [r for r in subset if r['kind'] == 'development']
        for run in sorted({r['run_id'] for r in dev}):
            intervals = [r for r in dev if r['run_id'] == run]
            start = min(timestamp(r['started_at']) for r in intervals)
            span = max((timestamp(r['ended_at']) - start).total_seconds() for r in intervals)
            parts.append('<h3>Agent intervals · ' + e(run) + '</h3><p>Timeline starts ' + e(start.isoformat()) + f'; full span {span:.1f} seconds. Overlap is retained; intervals are not summed as wall time.</p>')
            parts.append(f'<svg role="img" aria-label="Agent timeline" viewBox="0 0 1000 {len(intervals)*40+30}">')
            for i, r in enumerate(intervals):
                x = 300 + 650*(timestamp(r['started_at'])-start).total_seconds()/max(span, 1)
                width = 650*duration(r)/1000/max(span, 1)
                parts.append(f'<text x="10" y="{i*40+25}">{e(r["agent_role"][:35])}</text><rect x="{x}" y="{i*40+10}" width="{max(width,1)}" height="20" fill="#176e94"><title>{e(r["case_id"])}: {duration(r)/1000:.1f} seconds</title></rect>')
            parts.append('</svg>')
    parts.append('<h2>Observation register</h2><p>References are plain text, not executable links. Full numeric fields remain in the source JSONL.</p><table><tr><th>Observation</th><th>Source / security</th><th>Context references</th><th>Input / output tokens</th><th>Quality /6</th><th>Duration ms</th><th>Evidence</th></tr>')
    for r in rows:
        parts.append(f'<tr><td>{e(r["run_id"])} / {e(r["case_id"])} / {e(r["variant"])} / {r["repetition"]}</td><td>{e(r["source"])} / {e(r["security_result"])}</td><td>{e(", ".join(r["context_refs"]))}</td><td>{e(r.get("input_tokens") if r.get("input_tokens") is not None else "unknown")} / {e(r.get("output_tokens") if r.get("output_tokens") is not None else "unknown")}</td><td>{e(r.get("quality_score") if r.get("quality_score") is not None else "unknown")}</td><td>{duration(r):.1f}</td><td>{e(r["evidence_ref"])}</td></tr>')
    parts.append('</table></html>')
    return ''.join(parts)


def atomic_write(path, content):
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as handle:
            temporary = handle.name
            handle.write(content)
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            os.unlink(temporary)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, help='write static HTML; omit to validate only')
    args = parser.parse_args()
    try:
        rows, digest = load(args.input)
        if args.output:
            if args.input.resolve() == args.output.resolve():
                raise ValueError('output must not overwrite input')
            atomic_write(args.output, render(rows, digest))
        print(f'Validated {len(rows)} observations ({sum(r["source"] == "observed" for r in rows)} observed).')
    except (ValueError, OSError) as exc:
        parser.exit(1, f'Measurement check failed: {exc}\n')


if __name__ == '__main__':
    main()
