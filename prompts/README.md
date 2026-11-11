# Prompts

The prompts used with the AI assistant (Claude Code) for this modernisation, in the order they were
given, copied as written. See [MODERNIZATION.md](../MODERNIZATION.md) for what came out of them.

## 1. Get it running and set the working rules (CLAUDE.md)

```text
This repo is my fork of an open-source legacy Java app. I'm going to document
it, put tests around it and modernise one part of it with your help.
Start by getting it running, without changing any code:
- Check which JDK and Maven versions it needs and which I have installed.
Tell me exactly what to install if anything is missing.
- Build it, run it and tell me the URL to open.
Then create a CLAUDE.md with these rules:
- Don't change behaviour without a test that covers it.
- Run the full test suite after every change.
- Keep the original license and credit the original project.
- Explain the trade-offs before any large change, and wait for my OK.
```

## 2. Map the codebase (docs/ARCHITECTURE.md)

```text
Map this codebase and write docs/ARCHITECTURE.md:
- What the app does, and the tech stack with exact versions.
- The package structure, with one line per package.
- How one request flows end to end, for example adding a new owner: URL,
controller, service, repository, database and view.
- The data model as a Mermaid ER diagram.
- How configuration works (XML, Java config, properties files).
- Modernisation risks: outdated or end-of-life libraries, deprecated APIs and
missing tests.
Back every statement with file paths. Don't guess: if something is unclear,
say so.
```

## 3. Characterization tests

```text
Before we change anything, add characterization tests that lock in the
current behaviour of the owners, pets and visits features:
- Web-layer tests with MockMvc for the main pages and form submissions,
including validation errors.
- Data-access tests against the test database.
Don't change any production code. Run the tests and make sure they all pass
on the current code.
```

## 4. Modernisation plan: option A vs option B

```text
Propose a modernisation plan for one vertical slice: the owners feature.
Compare two options:
A) Move the owners feature to Spring Boot 3 on a current Java LTS and expose
it as a REST API, keeping the existing UI working.
B) Upgrade the whole app in place to current Spring and Java versions.
For each, give the steps, effort, risks and what the tests protect. Recommend
one and wait for my decision. Don't change any code yet.
```

Decision, after asking for the recommendation ("what is good?"):

```text
okay go with option b.
```

## 5. Implementation in small steps

```text
Implement the option I chose, in small steps. After each step, run the full
test suite. If a characterization test fails, stop and explain why instead of
changing the test. Commit after each green step with a clear message. At the
end, list everything that changed.
```

## 6. Write-up (MODERNIZATION.md, this folder, README)

```text
Write MODERNIZATION.md:
- The goal and the slice we modernised.
- Before and after: versions, structure, and how the feature is called.
- How the characterization tests protected the change, and the final test
results.
- What the AI assistance did well and where I had to correct it. Leave
headings for my own notes here; don't invent them.
- Next steps for modernising the rest.
Also create a prompts/ folder with a README where I'll paste the prompts I
used. Update the main README to link to ARCHITECTURE.md and MODERNIZATION.md
and to credit the original project.
```

## Follow-up questions along the way

Short questions asked between the main prompts, in order:

1. After the first run, with screenshots of localhost:8080 showing a different local site instead of
   PetClinic: "whats the issue?"
2. A follow-up asking why localhost:8080 still showed that other site and its icons. (Reworded here
   to leave out the names of unrelated local projects. Cause: another local web server already used
   port 8080, so PetClinic was run on another port.)
3. After step 1: "is all of this done?"
4. After the plan in step 4: "what is good?"
5. Before finishing: "Done when: the app runs, ARCHITECTURE.md explains it, the characterization
   tests pass, one slice is modernised with all tests green, and MODERNIZATION.md includes your own
   notes. has all of this been done?"
