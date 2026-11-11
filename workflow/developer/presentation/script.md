# Presentation script: "Less context, same code?"

About 12 minutes, 13 slides. Generated from the deck's speaker notes, so the two always match.
Slides 5–7 are detail slides; skip them if short on time. Every number is from
[runs.md](../experiments/agentic-se/runs.md) and [measurements.jsonl](../experiments/agentic-se/measurements.jsonl).
How each step was done: [method report](../experiments/agentic-se/method-report.md).

## 1. Less context, same code? (40 s)

In my last update I only looked back at how I had used tokens, so nothing was really
tested. This time I ran an experiment. I wanted to know two things. If I give the agent
fewer files to read, do I get worse code or just cheaper code? And is it worth paying
for a second agent to review the code? I did this on real work, the core of my DEV-AI
feature, and the code I kept from these runs is now the feature code.

## 2. The feature: AI suggestions for the developer (50 s)

First, the feature I was building. TraceDesk is a bug tracker with three roles:
reporter, developer and QA. My part is the developer side. This feature lets the
developer who is assigned to a bug ask an AI for possible causes and next steps. The
developer chooses which parts of the report go to the AI, for example the description,
the logs or their own notes. The AI's answer is only advice: it cannot change the report
or its status. If the developer likes a suggestion, they can edit it and save it as a
private note. The three blue boxes are the core pieces I built during this experiment:
the permission checks, building what is sent, and checking the AI's reply. The
permission checks actually run three times: before anything is selected, before sending,
and before showing the result, so if someone is removed from the bug mid-request, they
don't get the answer. The web endpoint and the screen are the next step.

## 3. Two questions (60 s)

The first question is about context. My project rules tell the agent to read only what
is relevant. Before, I measured that the files DEV-AI needs are about 14 times smaller
than everything linked from the rules. But a smaller set only helps if the code is still
correct. The second question is about the checker, which is the generator and verifier
idea from Lecture 8. A reviewer agent costs tokens, so I wanted to see if it finds
anything the tests miss. For both, I looked at results, not just token counts, because
Lecture 9 says token counts alone don't tell you if it was worth it.

## 4. How I ran it (80 s)

I used Claude Code to plan the tests and launch the runs, and Codex wrote all the code.
First, the tests. I turned the spec into 55 test cases before any code existed. The
agents could see 41 of them. I kept 14 in a folder outside the repository, to check
whether the code follows the spec and not just the tests it can see. Then I split the
feature into three small jobs. For each job, two Codex agents started from the same
commit, at the same time, each in its own git worktree so they could not see each
other's work. Agent A was only allowed to read seven set documents plus the job's own
files. Agent B could read anything. Their prompts were the same except for that one
line. Finally a third agent, which could not edit anything, reviewed both versions
labelled X and Y, so it did not know which was which. I ran everything from the Codex
command line, which reports exact token counts.

## 5. What A was allowed to read (30 s)

This is the exact list agent A was given. These seven documents are the set I picked by
hand for DEV-AI in my earlier measurement, about ten thousand tokens. I reused it as it
was, without checking it again, because I wanted to test the kind of list I would really
use. For each job, A could also open that job's starter class and test file, and any
Java type it needed so the code compiles. The important thing is what is missing. Our
project defines what counts as blank text inside the Reporter spec, not in a shared
document, so it is not on this list. That is where job 1's bug came from.

## 6. The 14 hidden tests (40 s)

These are the 14 tests the agents never saw. They check the small rules in the spec that
are easy to miss, like counting emoji correctly, or refusing the wrong developer before
reading any notes. I kept them in a folder outside the repository, and only copied them
in after an agent had finished, then removed them again. I also searched every agent's
command log afterwards, and none of them looked in that folder. In the end every version
passed all 14, so the hidden tests did not separate A and B. The one bug that did, in
job 1, was something none of my tests checked.

## 7. The prompts were the same except one line (30 s)

To keep it fair, the two prompts were the same apart from one line. Both said what to
build, which test file defines it, to follow the spec and not just the tests, not to
edit tests, to stay in their own folder, not to commit, and the exact command to run the
tests. The only difference is the context line you can see here. For the rerun of job 3
I added one more instruction to A's line, to read the whole list in one command.
Everything ran through the Codex command line with the same model and effort.

## 8. Results: A and B passed the same tests (60 s)

Here are all three jobs in one table. In every job, both versions passed the same tests,
including the 14 hidden ones. So the tests could not show which version was better. What
I didn't expect was the total. A, the focused agent, used 31 percent more input tokens
overall, and almost all of that came from job 3. Also, most of these input tokens are
cache reads, which means the agent re-reading its own conversation, the same thing I
found in my last update.

## 9. A missed a rule that was written in a file not on its list (70 s)

Job 1 shows where a short list can go wrong. The DEV-AI spec says text in the AI's reply
cannot be blank, but it doesn't say what counts as blank. To be honest, that's a gap in
our own documents, not just in A's list. Every feature uses the same idea of blank, but
we never put it in one shared place. The Reporter spec defines it, the investigation
spec repeats it, and the DEV-AI spec, which we wrote, left it out. A's list was the
files I picked by hand for DEV-AI, so the Reporter spec wasn't on it, and A used Java's
normal isBlank check, which lets a non-breaking space through. B went looking, found the
Reporter spec and the existing helper code, and used the same rule. None of my tests
covered this case either. The checker found it, and I confirmed it against the spec. The
real fix is to move shared rules like this into one shared document, so a short list is
enough.

## 10. More steps, more tokens: reading one file at a time was half of A’s extra cost (80 s)

Job 3 surprised me, because the focused agent used almost twice as many tokens as the
broad one. The reason is the number of steps. Every time an agent runs a command, the
whole conversation so far is sent to the model again, so more steps means more tokens. A
read its files one at a time, which took 40 steps. To check if that was the cause, I
reran A with the same list but told it to read everything in one go. It dropped to 12
steps and 1.13 million tokens, so the reading pattern explains about half the gap. It
was still 40 percent more than B, because it loaded the whole list at the start and
carried it in every step, even the parts this job did not need.

## 11. The checker found 2 problems the tests missed (50 s)

For the second question, the three reviews together used about 680 thousand input
tokens. On average, one review cost about a third of one writer run. They found two real
problems that no test caught. One was the blank-text bug from job 1. The other was in
job 3: if the database fails while usage is being recorded, the user sees "AI
unavailable" instead of "service unavailable", which the spec does not allow. I checked
each finding myself before counting it.

## 12. How careful to be with these results (45 s)

A few things limit what these results can show. First, I only ran three pairs, once
each, so another run might come out differently. Second, I chose A's file list myself,
and the rule A missed was kept in a Reporter document instead of a shared one. With that
rule in the right place, A might have got job 1 right, so job 1 partly tests our
documents and not just the idea of a short list. Third, the checker is the same model as
the writers, so it might miss the same things. That's why I checked every finding
myself. Fourth, A and B ran at the same time on my laptop, so the times are only rough,
but that does not change the token counts.

## 13. What I will do differently (60 s)

So what did I learn about context? I expected the short list to give cheaper code. It
didn't. It was only a bit cheaper when the spec covered everything, it missed a rule
once, and in job 3 it cost more. When the agent could choose, it did not read
everything. It read between 17 and 28 files per job, not the 98 documents that are
linked from my rules. What really drove the token count was the number of steps, because
each step sends the whole conversation again. So I'll stop giving a fixed list by
default, and instead ask agents to read in batches and avoid running the tests over and
over. I'll keep the blind checker for spec-heavy work, and add tests for the two
problems it found. The feature code I kept passes all 55 tests.

## If asked

**"Why does a Developer feature need a Reporter document?"**
It shouldn't. Every feature uses the same rule for what counts as blank text, but we
only wrote it down inside each role's own spec. The Reporter spec defines it, the
investigation spec repeats it, and the DEV-AI spec left it out. That's a gap in our
documentation, not a reason for DEV-AI to depend on Reporter. The fix is to move shared
rules like this into one shared contract (for example the shared role contracts
decision) and have each feature spec point to it.

**"So wasn't job 1 unfair to A?"**
Partly, and I say so on the limits slide. But it's also a realistic result: in a real
project, rules end up in the wrong place, and a fixed short list can't know that. The
broad agent found the rule because it was allowed to look.

**"Then why not just give A a better list?"**
I could, but I'd only know what to add after seeing the bug. That's the point: a short
list is only as good as the documents behind it.
