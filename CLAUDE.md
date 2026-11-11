# CLAUDE.md

This repo is a fork of [spring-petclinic/spring-framework-petclinic](https://github.com/spring-petclinic/spring-framework-petclinic)
(itself derived from [spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic)).
The goal of the fork is to document the app, put tests around it, and modernise one part of it.

## Rules

1. **Don't change behaviour without a test that covers it.** If the behaviour you are about to
   change has no test, write one first that pins the current behaviour, see it pass, then make the change.
2. **Run the full test suite after every change** (`./mvnw clean verify`) and report the result.
   Don't call a change finished while any test is failing.
3. **Keep the original license and credit the original project.**
   - Don't modify or remove `LICENSE.txt` (Apache License 2.0).
   - Keep the existing Apache license headers and `the original author or authors` copyright lines
     in source files. New source files get the same Apache 2.0 header.
   - Keep the credit to the original project in `readme.md` when editing it.
4. **Explain the trade-offs before any large change, and wait for an OK.** "Large" covers things like
   new or upgraded dependencies or frameworks, changes to the build, schema or persistence layer,
   restructuring packages, or anything touching many files. Describe the options, the trade-offs and
   the recommended one, then stop until the user approves.

## Build and run

- Needs JDK 17 or newer (CI tests on 17 and 21). The enforcer plugin fails the build on older JDKs.
- No Maven install needed: use the wrapper `./mvnw` (it downloads Maven 3.9.16; the minimum is 3.8.4).
- Full build with tests: `./mvnw clean verify`. This runs the unit/MockMvc tests (surefire, `*Tests`)
  and then the smoke tests (failsafe, `*IT`) against the packaged WAR, which Maven starts in Jetty on
  port 9967 and stops afterwards. Port 9967 (and 9968, its stop port) must be free.
- Run locally (Jetty 12.1, in-memory H2 database, seeded at startup): `./mvnw jetty:run-war`, then open http://localhost:8080/
  - If port 8080 is already in use on your machine, run on another port:
    `./mvnw jetty:run-war -Djetty.http.port=9966`, then open http://localhost:9966/
- MySQL / PostgreSQL profiles: `-P MySQL` / `-P PostgreSQL` (see `readme.md`).

## Layout

- Classic 3-layer Spring Framework app (no Spring Boot), packaged as a WAR, JSP views.
- Code: `src/main/java/org/springframework/samples/petclinic/` — `model`, `repository`
  (JDBC, JPA and Spring Data JPA implementations), `service`, `web`, `util`.
  Web app bootstrapped by `PetclinicInitializer` (no `web.xml`). JSPs in `src/main/webapp/WEB-INF/jsp/`.
- Spring config: the MVC (servlet) context is Java config in `config/WebMvcConfig.java`; the root
  context (services, persistence, tools) is still XML in `src/main/resources/spring/`.
  Database scripts: `src/main/resources/db/`. Architecture notes: `docs/ARCHITECTURE.md`.
- Tests: `src/test/java/...`
  - upstream tests: controller (mocked service), model, and service tests per persistence profile;
  - characterization tests (`*CharacterizationTests`): web layer with the real config, and repositories
    on all three implementations. They pin current behaviour, including quirks and known bugs that are
    marked as such. Don't "fix" those assertions without deciding to change the behaviour;
  - smoke tests (`smoke/PetclinicSmokeIT`): real HTTP against the running WAR, JSPs really rendered.
