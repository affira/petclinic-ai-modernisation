# Robot Framework suite

A small [Robot Framework](https://robotframework.org/) suite that checks PetClinic over plain HTTP with
[RequestsLibrary](https://github.com/MarketSquare/robotframework-requests) (no browser). It runs against
the packaged WAR in Jetty, like the Java smoke tests (`PetclinicSmokeIT`), and its scenarios are ported
from those smoke tests and the web characterization tests.

| File | What it is |
|---|---|
| `petclinic.robot` | The test cases (Given/When/Then style) |
| `petclinic.resource` | Reusable keywords: session setup, requests, checks |
| `variables.py` | Variables file with `BASE_URL` |
| `requirements.txt` | Pinned Python packages |

It is not part of the Maven build: `./mvnw clean verify` does not run it.

## Run it locally

Needs Python 3.9 or newer. One-time setup, from the repository root:

```bash
python3 -m venv robot/.venv
robot/.venv/bin/pip install -r robot/requirements.txt
```

Terminal 1: build the WAR and start it in Jetty on port 9966 (`jetty:run-war` runs the `package`
phase itself; `-DskipTests` skips the Java tests during that build):

```bash
./mvnw jetty:run-war -DskipTests -Djetty.http.port=9966
```

Terminal 2, once http://localhost:9966/ answers:

```bash
robot/.venv/bin/robot --outputdir robot/results robot/
```

Open `robot/results/report.html` (summary) or `robot/results/log.html` (every step). The `results/`
folder and `.venv/` are git-ignored.

Useful options:

- Other URL: `--variable BASE_URL:http://localhost:9967`, or set `PETCLINIC_BASE_URL`.
- By tag: `--include search`, `--exclude known-bug`.

The database is in memory and keeps what earlier runs added until Jetty is restarted; the
create-owner test uses a random last name, so the suite can be run repeatedly.

On the macOS system Python a `NotOpenSSLWarning` from urllib3 may be printed; it is harmless here
(plain HTTP). Hide it with `PYTHONWARNINGS=ignore`.
