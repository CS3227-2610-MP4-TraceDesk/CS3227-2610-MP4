# QA review history

Issue #64. Completed QA decisions remain attached to their resolution revisions.

- **TD-QAH-01** Active QA reviewers can page through their own completed decisions in the selected project. Use a descending audit ID cursor, a fixed page of 25, and server-resolved reviewer identity.
- **TD-QAH-02** Active QA project members can read an individual completed review by report and resolution ID, including its checks, notes, outcome, reviewer and decision time. The result is read-only and stays with the decided revision.
- **TD-QAH-03** In-project non-QA roles receive 403. Missing membership or a resolution outside the requested report and project receives 404. History reads add no lifecycle transitions and expose no draft checks.
