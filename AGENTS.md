# Stillframe contributor instructions

## Public repository and privacy

**Stillframe is a public GitHub project. Keep the maintainer's personal
information and private environment out of every public artifact.**

- Treat commits, branches, issues, pull requests, comments, release assets,
  screenshots, build output, and CI logs as public. Review the exact content
  and Git author and committer metadata before publishing.
- Never publish real names, personal email addresses, home or work addresses,
  precise location coordinates, local usernames or absolute machine paths,
  private network addresses or hostnames, device identifiers, account or profile
  details, viewing history, secrets, pairing codes, or raw ADB exports.
- Use fictional fixtures and generic relative paths in examples. Keep local
  settings, device captures, and troubleshooting evidence outside the repo.
  Only publish screenshots and assets checked for personal data and rights.
- Use an approved public identity and a GitHub no-reply address for commits.
  If private data appears in a staged change or commit, stop publication and
  remove it from all outgoing history before pushing.

Files stay under 300 lines normally; 500 is the hard ceiling and only when splitting
would genuinely hurt. If a file crosses 300, split it before adding more to it.

## 2. Test-driven, always
Code without tests is unproven and assumed to fail. Write the test first, watch it
fail, then make it pass. The test bar scales with the language: Python gets real
tests (pytest, bounded property checks for pure functions); shell gets a contract
check — a runnable assertion of the documented behavior. You are never penalized for
adding more testing; testing is how you prove your code.

## 3. Prove it by running it
No claim of "works" without an actual execution. A tool that has never been run is a
stub, not tooling. Run the test, run the tool, show the result. An earlier pass does
not verify a later patch — re-run after the last edit.

## 4. Atomic documentation, always required
Every project has a `docs/` folder with one topic per file. Each doc states: what the
thing is, why it exists, how to run it, and what it assumes. Docs must be readable by
an agent with grep — headings, plain language, no marketing prose. A project without
docs is incomplete.

## 5. Comments are bookmarks, not decoration
Comment the *intent*, not the mechanics: why this code is here, what problem it
prevents, how to use it. Comments are natural-language anchors meant to be found later
by search. If you find code missing documentation or comments, add it as you work.

## 6. Clean, elegant, obvious, boring
Single entry points, shared helpers, strict boundaries. Prefer refactoring an existing
helper to meet this standard over duplicating it. No spaghetti, no cleverness, no
second path to the same outcome. Names must make sense to a reader who finds the file
six months from now — that reader is you, or another agent.

## 9. Review gate
Run the code-reviewer skill against code you wrote at the end of your turn, unless the user specifically asks for the
multi-review. if your project has phases, run the multi-code-review skill until **3 consecutive reviews return no bugs, issues,
or red flags** between phases. Documentation drift does not reset the counter. Record the counter in
`docs/review-state.md` so it survives sessions.

## 10. Keep docs and comments in sync
At the end of any turn where you modified or wrote code, re-read the relevant docs and
comments and bring them in line with what the code now does. Stale docs are bugs.

## 11. Write down what surprises you
If something in this project perplexes you or contradicts your expectations, write it
to the bottom of this file under `## agent-notes` and bring it to the user. Resolve
the entry (move it to docs or delete it) once addressed — this section must not silt
up.

## agent-notes
