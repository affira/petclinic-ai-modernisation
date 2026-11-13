# Modernisation of Spring Framework PetClinic

This fork of [spring-petclinic/spring-framework-petclinic](https://github.com/spring-petclinic/spring-framework-petclinic)
was documented, put under tests and partly modernised with AI assistance (Claude Code). This file
records what was done and why, what protected the change, and what is left.

Related documents:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how the app is built, with file references, plus the
  risks and open questions found while mapping it.
- [CLAUDE.md](CLAUDE.md): the working rules the assistant followed.
- [prompts/](prompts/README.md): the prompts used, in order.
- [robot/](robot/README.md): a small Robot Framework suite of HTTP checks (section 6).

The upgrade was merged into `main` through pull request #1 (commits `078e399` to `e1e2c5b`, merge
commit `133128b`). The state described here was measured on 11 November 2026.

---

## 1. Goal and slice

**Goal:** bring a legacy Spring Framework app up to date without changing what it does, and prove
that with tests rather than assume it.

**Slice:** the **owners** feature: find owners by last name, list, show an owner with pets and
visits, create, and edit (`web/OwnerController.java`, `/owners/**`).

**Option chosen.** Two options were compared for this slice
([plan discussion in the prompts](prompts/README.md)):

- **A:** move owners to a separate Spring Boot app with a REST API. Rejected for now: Spring Boot 3
  is out of free support and runs on an older Spring (6.2) than the app already used (7.0). A
  second app would also mean two code paths writing the `owners` table.
- **B:** upgrade the app in place. Chosen, because the app was already on Spring Framework 7, so the
  real problems were the server (Jetty 11), the deprecated XML web configuration and build issues.

Because B upgrades the platform underneath, it changes the request path of **every** web feature,
not only owners. The owners slice was the one specified in depth. The tests cover owners, pets and
visits, since all three go through the same MVC configuration, service and repositories.

Out of scope for this round: Java 25 (waiting for a decision, see [section 5](#5-next-steps)), the
Docker image, and any change to business behaviour, including known bugs.

## 2. Before and after

### Versions

| Component | Before (`d980667`) | After (pull request #1, `133128b`) |
|---|---|---|
| Servlet container (dev server, smoke tests) | Jetty 11.0.26: Servlet 5.0, out of community support | **Jetty 12.1.14** (ee11): Servlet 6.1, Pages (JSP) 4.0 |
| Maven plugin | `org.eclipse.jetty:jetty-maven-plugin` | `org.eclipse.jetty.ee11:jetty-ee11-maven-plugin` |
| Spring Framework | 7.0.10 | 7.0.10 (unchanged) |
| Hibernate ORM | 7.4.14.Final, dialect set explicitly | 7.4.14.Final, dialect auto-detected |
| Java | 17 minimum, CI on 17 and 21 | unchanged (Java 25 pending) |
| MySQL driver | `mysql:mysql-connector-java` 8.1.0 (relocated coordinates) | `com.mysql:mysql-connector-j` 8.1.0 |
| Test libraries in the WAR | JUnit and Mockito jars packaged in `WEB-INF/lib` | test scope only |
| Docker image base (Jib) | `jetty:11.0-jdk17` | unchanged (not verifiable without Docker) |
| Warnings at build/startup | MVC XML namespace deprecated; `HHH90000025` dialect; JDK dynamic agent loading | none of these |

### Structure

| | Before | After |
|---|---|---|
| Servlet (web) context | XML: `src/main/resources/spring/mvc-core-config.xml` and `mvc-view-config.xml` | Java: [`config/WebMvcConfig.java`](src/main/java/org/springframework/samples/petclinic/config/WebMvcConfig.java). The XML files are deleted |
| Root context (services, persistence, tools) | XML in `src/main/resources/spring/` | unchanged (still XML) |
| Bootstrap | `PetclinicInitializer` creates two `XmlWebApplicationContext`s | root: `XmlWebApplicationContext`, servlet: `AnnotationConfigWebApplicationContext` with `WebMvcConfig` |
| Tests | 75 upstream tests (controller tests with a mocked service, model tests, service tests per persistence profile) | + 112 characterization tests, + 14 smoke tests against the running WAR (see [section 3](#3-how-the-tests-protected-the-change)) |
| Build | `mvn verify` = compile + unit tests | `mvn verify` = unit tests, then start the WAR in Jetty on port 9967, run smoke tests, stop Jetty |

### How the owners feature is called

**From the outside, nothing changed.** URLs, HTTP methods, form fields, redirects, validation
messages and pages are the same.

| Action | Request | Result (before and after) |
|---|---|---|
| Find form | `GET /owners/find` | `owners/findOwners.jsp` |
| Search | `GET /owners?lastName=Da` | one match: redirect to `/owners/{id}`; several: `owners/ownersList.jsp`; none: find form with "not found" |
| Show owner | `GET /owners/{id}` | `owners/ownerDetails.jsp` with pets and visits |
| Create | `GET` then `POST /owners/new` | redirect to `/owners/{newId}`, or the form with validation errors |
| Edit | `GET` then `POST /owners/{id}/edit` | redirect to `/owners/{id}`, or the form with validation errors |

**Inside, the path changed in two places:**

```
Before:  Jetty 11 → DispatcherServlet [MVC beans from mvc-core-config.xml + mvc-view-config.xml]
         → OwnerController → ClinicServiceImpl (@Transactional) → JpaOwnerRepositoryImpl → H2
         → view resolved by <mvc:jsp> → JSP rendered by Jetty 11

After:   Jetty 12.1 → DispatcherServlet [MVC beans from WebMvcConfig]
         → OwnerController → ClinicServiceImpl (@Transactional) → JpaOwnerRepositoryImpl → H2
         → view resolved by WebMvcConfig.configureViewResolvers → JSP rendered by Jetty 12.1 (WASP)
```

Controllers, services, repositories, entities and JSPs weren't modified.

## 3. How the tests protected the change

### Tests written before any change

The rule in [CLAUDE.md](CLAUDE.md) was: no behaviour change without a covering test. So tests that
pin the **current** behaviour came first (commit `078e399`), without touching production code:

| Suite | What it pins | Count |
|---|---|---|
| Web characterization (`web/*WebCharacterizationTests`) | MockMvc against the **real** Spring configuration and the H2 test database: every owners, pets and visits page and form, validation error codes, redirects, stored rows (checked with SQL), and that each forwarded JSP file exists | 45 |
| Repository characterization (`repository/*RepositoryCharacterizationTests`) | Owner, pet and visit repositories on **all three** persistence implementations (JPA, JDBC, Spring Data JPA), including where they differ today | 63 (21 × 3) |
| Smoke tests (`smoke/PetclinicSmokeIT`, commit `4dbc2d2`) | Real HTTP against the packaged WAR in Jetty: pages actually rendered, every referenced CSS/JS/image loads, forms submitted | 14 |
| Content negotiation (`web/ContentNegotiationCharacterizationTests`, commit `41fb5b6`) | How the `Accept` header selects a view or format; written against the XML config before replacing it | 4 |

Quirks and known bugs are pinned as they are and labelled in the test, not fixed. Examples: the
visit list returns 404 because `visitList.jsp` is missing; `01234567890` passes the 10-digit phone
rule because leading zeros don't count; `%` in the owner search acts as a wildcard.

### What each step relied on

| Step (commit) | Risk | What caught or would have caught it |
|---|---|---|
| Build cleanup (`1051d3f`) | Dialect or packaging change breaks persistence or startup | repository tests on all three implementations; smoke tests |
| Jetty 11 → 12.1 (`8e7cc52`) | JSP/JSTL incompatibility in the new container; MockMvc can't see this | smoke tests, which render every page in the real container |
| XML → Java MVC config (`b0792ff`) | Subtle differences in view resolution, content negotiation, error handling | web characterization tests, content-negotiation tests, and a before/after comparison of 56 HTTP responses |

### Things the tests caught

- **Test setup that changed the app's behaviour.** The first version of the web characterization
  tests wrapped each test in one database transaction, the usual Spring approach. That kept JPA
  entities managed across the request and produced failures that don't happen in production:
  "Detached entity passed to persist" when editing a pet, and an invalid visit getting saved. A
  check against the running app confirmed that pet edit works there. The tests were changed to
  commit for real and reseed the database after each test.
- **A behaviour change hidden in the config switch.** Before replacing the XML web config, 56 HTTP
  responses (status, content type, redirect, body hash) were recorded from the running app. After
  the switch, one differed: `GET /vets` with `Accept: application/xml` changed from 406 to an XML
  response. Java config registers default file-extension mappings that the XML setup didn't have
  in effect. `WebMvcConfig.configureContentNegotiation` now restores the old behaviour, and
  `ContentNegotiationCharacterizationTests` pins it. That test passes on the old XML config, and it
  fails if the fix is removed (checked). After the fix, all 56 responses were identical. Why
  Spring's XML namespace behaved differently internally was not investigated further.
- **A configuration constraint.** Loading the root and MVC configuration into one flat test context
  fails, because the JPA repositories then can't get their `EntityManager`. The web tests use the
  same parent/child context split as production.

**No characterization test failed during the upgrade, and no test assertion was changed.** The
only test edits after commit `078e399` were to context setup: which configuration a test loads,
once the XML MVC files were gone.

### Final results

`./mvnw clean verify` on 11 November 2026 (JDK 21.0.13, Jetty 12.1.14):

| | Before (`d980667`) | After |
|---|---|---|
| Unit and MockMvc tests (Surefire) | 75 passed | **187 passed**, 0 failures, 0 errors, 0 skipped |
| Smoke tests against the running WAR (Failsafe) | none | **14 passed**, 0 failures |
| Line coverage (JaCoCo, Surefire tests only) | 86% (449/524) | **92% (511/555)** |
| Branch coverage | 73% (83/114) | **86% (98/114)** |
| Full build time | not measured | about 27 s |

Every commit in pull request #1 was made only after the full suite passed at that exact state. The
suite also passed in random test order.

## 4. AI assistance: what worked and what needed correcting

*Written with the AI assistant from the session record. Every item refers to something that
happened during the work (see [prompts/](prompts/README.md) and the commit history).*

### What the AI assistance did well

- **Checked claims against the running app and the build, not only the code.** It traced the
  add-owner request live, confirmed the visit-list 404 on the server, measured coverage with
  JaCoCo, and ran `javac -Xlint:all` to look for deprecated APIs.
- **Followed the CLAUDE.md rules throughout.** Tests came before changes, and the full suite ran
  after every step. No characterization test assertion was changed during the upgrade.
- **Caught a behaviour change the tests didn't cover.** A before/after comparison of 56 HTTP
  responses found that `/vets` with `Accept: application/xml` would change from 406 to 200. The old
  behaviour was restored and pinned with a test, which was shown to fail without the fix.
- **Warned before risky or outward-facing steps.** Examples: Spring Boot 3 being out of free
  support and older than the app's Spring version (option A); the pull request page defaulting to
  the original repository; names of unrelated local projects in the prompts and CLAUDE.md, removed
  before pushing.
- **Said when it didn't know.** ARCHITECTURE.md lists open questions. The Docker image was left
  unchanged because it couldn't be verified, and external facts (end-of-life dates, the Spring 7
  baseline) were looked up and cited.

### Where I had to correct it

Corrections that came from the author:

- **Wrong diagnosis at first.** Asked why localhost:8080 showed another site's icons, the assistant
  first blamed the browser's icon cache. A later screenshot showed they came from the browser's
  bookmarks bar.
- **Long answers.** After the two-option comparison, the author asked "what is good?" to get a
  plain recommendation.
- **What gets published.** Asking why the prompts are pushed to GitHub led to rewording the
  references to unrelated local projects. That included amending a commit that hadn't been pushed.
- **Completion checks.** Asking "is all of this done?" showed that this section and the prompts
  were still empty.

Mistakes the assistant caught itself, through tests or checks:

- **Test setup that changed the app's behaviour.** Wrapping each web test in one transaction
  changed how JPA behaved. The tests were switched to real commits with the database reseeded.
- **Wrong first expectations, fixed after observing real behaviour:** ISO dates are accepted;
  `01234567890` passes the phone rule; a search for `%vis` also matches McTavish; an empty phone
  number gets two errors.
- **A smoke-test check that was too broad.** It treated any page containing "Exception" as an
  error, but the menu's tooltip has that word on every page.
- **A test context setup that didn't work.** A single flat Spring context failed, so the tests use
  the same parent/child split as production.
- **Small inaccuracies.** Some line references in ARCHITECTURE.md were a few lines off and were
  fixed after checking. The plan's "compile for 25, test on 21 and 25" contradicted itself and was
  flagged before step 1.
- **Commits made late.** Steps 0–4 were done before the "commit after each green step"
  instruction, so they were rebuilt as separate commits, each verified green.

### What I would do differently next time

- Set the commit rules at the start, so each step is committed as it's finished.
- Settle scope questions during planning: the target Java version, and whether the Docker image is
  included.
- Check for port conflicts on the machine before the first run.
- Ask for the recommendation first and the detail after, when comparing options.
- Decide early what may be published (prompts, local paths and names), before anything is pushed.

## 5. Next steps

In rough order. The upgrade left the app easy to change in small steps, with the safety net in
place.

1. **Java 25 (step 1 of the plan, decision pending).** Either (a) compile for 21 and run CI on 21
   and 25 (recommended), or (b) compile for 25. Needs JDK 25 installed locally to verify.
2. **Docker image on Jetty 12.1.** The Jib base image is still `jetty:11.0-jdk17` (`pom.xml`).
   Update it and check on a machine with Docker that the image deploys the WAR with the ee11
   modules enabled.
3. **Root context to Java config.** Move `business-config.xml`, `datasource-config.xml` and
   `tools-config.xml` to Java configuration, the same way as the web layer: one step per file, with
   the repository characterization tests as the guard. This also removes the last XML namespaces.
4. **Tests for what's still uncovered:** caching (`@Cacheable("vets")`) and `CallMonitoringAspect`,
   which no test loads, and the MySQL and PostgreSQL profiles, which only run on H2 today. For
   example, run the repository tests against real databases with Testcontainers. Note that owner
   search is case-insensitive only because of the H2 column type.
5. **Decide on the known bugs and quirks**, each with a test change first (see
   [ARCHITECTURE.md 7.4](docs/ARCHITECTURE.md#74-bugs-found-while-mapping)): the visit list 404, the
   error page returning 200, the leading-zero phone rule, the unchecked owner ID in visit URLs,
   unknown IDs ending in a generic error page.
6. **Choose one persistence implementation.** Three (JDBC, JPA, Spring Data JPA) triple the work
   for every data change. Keeping one, probably Spring Data JPA, is a product decision for the fork.
7. **Owners REST API inside the app** (`/api/owners`), reusing `ClinicService` and the same
   validation, so there's no second write path. The web characterization tests list the behaviours
   to mirror.
8. **Front end:** the JSPs use Bootstrap 3 class names on Bootstrap 5 CSS (validation errors aren't
   styled), Font Awesome 4.7, and WebJar versions hard-coded in JSPs. Fix these within JSP first.
   Replacing JSP itself (for example with Thymeleaf) is a larger decision.
9. **Later, if wanted: Spring Boot 4.** With configuration in Java and tests in place, moving to
   Spring Boot 4 (on Spring Framework 7) becomes a contained change, not a rewrite.

## 6. Robot Framework suite

Added after the upgrade, as a way to learn Robot Framework on this codebase. It is in
[robot/](robot/README.md) and doesn't change any production code or Java tests.

### What it covers

Seven scenarios, checked over plain HTTP with RequestsLibrary (no browser) against the packaged
WAR running in Jetty. Each one is ported from an existing Java test.

| Scenario | Ported from |
|---|---|
| Home page renders | `PetclinicSmokeIT.welcomePage` |
| Owner search, one match: 302 to `/owners/1`, which shows George Franklin | `OwnerWebCharacterizationTests` (search for Franklin) |
| Owner search, several matches: list with Betty and Harold Davis | `OwnerWebCharacterizationTests.searchMatchingSeveralOwnersShowsList` |
| Owner search, no match: form again with "has not been found" | `OwnerWebCharacterizationTests` (search for Nobody) |
| Creating an owner: validation errors first, then valid data with a 302 to the new owner | `PetclinicSmokeIT.invalidOwnerRedisplaysFormWithMessages`, `createOwnerThenShowIt` |
| Visit list returns 404 (known bug, pinned as it is today, tag `known-bug`) | `PetclinicSmokeIT.visitListReturns404BecauseItsJspIsMissing` |
| Veterinarians page lists the vets, including James Carter (tag `vets`) | `PetclinicSmokeIT.vetsPageAndFeeds` (the HTML page part) |

The scenarios are in `petclinic.robot`, written with Given/When/Then keywords from
`petclinic.resource`. The base URL is in the variables file `variables.py`, and the Python packages
are pinned in `requirements.txt` (Robot Framework 7.5.1, RequestsLibrary 0.9.7).

### How to run it

```bash
# one-time setup (Python 3.9 or newer)
python3 -m venv robot/.venv
robot/.venv/bin/pip install -r robot/requirements.txt

# terminal 1: build the WAR and start it in Jetty
./mvnw jetty:run-war -DskipTests -Djetty.http.port=9966

# terminal 2: once http://localhost:9966/ answers
robot/.venv/bin/robot --outputdir robot/results robot/
```

The results are written to `robot/results/` (`report.html`, `log.html`, `output.xml`), which is
git-ignored. In CI, the workflow `.github/workflows/robot-tests.yml` runs the same steps on pull
requests and pushes to `main`, and uploads the results as the `robot-report` artifact.

### How it relates to the Java tests

- **It adds no new coverage.** Every scenario already has a Java test. The suite is a second, more
  readable view of behaviour that's already pinned, in the style a tester or business reader would
  use.
- **The closest Java equivalent is `PetclinicSmokeIT`.** Both send real HTTP to the WAR in Jetty.
  The web characterization tests go further: they also check model attributes, error codes and
  stored rows, which a black-box HTTP suite can't see.
- **It runs outside Maven.** `./mvnw clean verify` doesn't run it. The Java tests remain the safety
  net from [CLAUDE.md](CLAUDE.md). The Robot suite runs in its own workflow and check.
- **The known bug is pinned the same way in both.** If the visit list is fixed, the Robot scenario
  and the Java smoke test both fail. That forces a deliberate decision, not a silent change.

### Results

- **Locally, 12 November 2026** (macOS, Python 3.9.6, JDK 21): 6 tests, 6 passed, 0 failed. A
  throwaway copy with deliberately wrong expectations failed as intended, which shows the checks
  aren't vacuous.
- **Locally, 13 November 2026**, after adding the vets scenario: 7 tests, 7 passed, 0 failed.
- **CI, on pull request #3:** the `robot` job passed on the first run (6 passed, 0 failed) and
  again after the vets scenario was added (7 passed, 0 failed), and uploaded the `robot-report`
  artifact. `./mvnw clean verify` still passes with 187 + 14 tests.
