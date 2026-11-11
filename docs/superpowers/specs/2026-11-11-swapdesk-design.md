# SwapDesk: Design

Status: draft for approval · Date: 2026-11-11

Decisions are marked **[owner]** when the project owner decided them and **[proposal]** when they were proposed during design and accepted by the owner without change. Anything without a mark is background.

---

## 1. Intent

A call center of about 60–70 agents gets its 14-day schedule from an external workforce-management department (WFM) as an Excel file. Today agents find swap partners on WhatsApp, supervisors check the rules by hand, and the swaps go back to WFM in another Excel file. SwapDesk governs that process from end to end.

**Why it exists [owner]:** this is a **portfolio project** aimed at **technology companies in Saudi Arabia**, and a vehicle for the owner to **learn Spring Boot 4 in depth** (the owner knows Java but is new to Spring). Real use at the owner's workplace is unlikely. The system still simulates the real workplace as closely as possible.

### Goals
- Implement the real rules and workflow faithfully, including the awkward cases (concurrency, re-imports, holds, deadlines).
- Show professional backend engineering: clear module boundaries, meaningful tests, CI, security done the Spring way, documented decisions.
- Give a reviewer a live demo they can try in five minutes, with one-click login per role.
- Teach Spring Boot 4 step by step (see §15).

### Non-goals
- Real integration with WFM. WFM stays outside: we import their file and export ours.
- Rest-hour or labor-law checks. Accepting a swap means accepting the new schedule.
- Multi-tenant or multi-site support.
- Mobile apps.

### Anti-goals (a failure even if it works)
- Over-engineering: microservices, Kubernetes, message brokers for 70 users.
- Hand-rolled security, such as custom JWT filters built on third-party JWT libraries.
- Real employee data anywhere in the repository.
- Tests that only exist for coverage numbers.

---

## 2. Process and timeline

1. WFM sends the next cycle's file, usually on the **Thursday of the last week** of the current cycle.
2. The **schedule coordinator** imports it: upload → preview with errors and warnings → confirm. Confirming opens the cycle for swaps.
3. Employees swap weeks until the **deadline: the Saturday before the cycle starts, 23:59 Asia/Riyadh [owner]**. The real window is about two and a half days.
4. Supervisors approve their own team members' side.
5. The coordinator exports approved swaps to Excel for WFM, then records WFM's decision. WFM rejections are rare [owner].

---

## 3. Roles

| Role | Can |
|---|---|
| `EMPLOYEE` | See own schedule; see eligible peers with their schedules; create direct requests and open offers; accept or reject incoming requests; cancel own requests; follow statuses |
| `SUPERVISOR` | Approve or reject (with reason) requests involving their team members |
| `SCHEDULE_COORDINATOR` **[owner]** | Import cycles; export approved swaps; record WFM decisions; withdraw approved-but-unsent requests; resolve reconciliation conflicts |
| `ADMIN` | Manage users and settings; link supervisor accounts to supervisor names from the file |

A user has exactly one role. **[proposal]**

---

## 4. Input file

- One sheet with all employees from all channels; row 1 holds the headers.
- Columns are located **by header name**, not position. **[owner]**
- `Name AR | Channel | Email | User Name | HRID | Supervisor | Language | Gender | Comment`, then 7 day columns (`Sun 08-Nov` … `Sat 14-Nov`), `Days OFF`, 7 day columns (`Sun 15-Nov` …), `Days OFF`.
- `Comment` is ignored. **[owner]**
- `Channel` is the department and `Language` is the skill; each employee has exactly one of each. Values come from the file and are never hardcoded. **[owner]**
- `Gender` is `Male` or `Female`, taken from the file on every import.
- **Identity:** `HRID`; if it is empty, `Email`. If an employee known by email later gets an HRID, the HRID is attached to the same employee. **[owner + proposal]**
- **Day cell:** a shift start time (`6:00` or `06:00`, stored as text or as an Excel time value), `OFF`, or `V` (vacation). Anything else is an error.
- **`Days OFF`** is a count. It is checked against the OFF cells, and a mismatch gives a warning rather than an error. **[proposal]**
- **Cycle start date:** taken from the first day header. The year is the one closest to the import date in which that date is a Sunday. The 14 day headers must be consecutive dates. **[owner + proposal]**
- **Supervisor** is a name only. Supervisors are not rows in the file. **[owner]**
- The import collects **all** problems, each with sheet row and column, rather than stopping at the first one. Errors block confirmation; warnings do not. **[proposal]**

---

## 5. Domain concepts

- **Cycle:** 14 days from a Sunday, made of week 1 and week 2. Created by the first confirmed import for that start date. A cycle can be re-imported (see §8). Only one cycle is open for swaps at a time; older cycles are read-only.
- **Employee:** a stable identity across cycles (HRID or email), plus account link.
- **Cycle membership:** per cycle, the employee's channel, language, gender, supervisor name, active flag, and the **baseline schedule**: 14 day values as imported. Employees missing from a later import become inactive for that cycle; their history is kept and their account is disabled. **[proposal]**
- **Week:** (employee, cycle, week 1 | 2): the unit of swapping.
- **Swap request:** one or two week pairs (see §7), a type, a creation mode, a status, and the parties.
- **Week lock:** marks a week as committed to one swap request (rule 5).
- **WFM batch:** one exported file and the requests it contains.
- **Effective schedule:** baseline plus `WFM_APPROVED` swaps applied on top. The baseline is never modified. **[proposal]**

---

## 6. Eligibility rules

A swap of week *w* between A and B is allowed only if all of these hold:

1. A and B have the same `Channel` **and** the same `Language` in that cycle. **[owner]**
2. If the receiver is female, **every working shift** in the week she would receive starts between **06:00 and 15:00 inclusive**. Both bounds are admin settings. **[owner]**
3. Neither A's nor B's week contains `V`. **[owner + proposal: "either side"]**
4. Now is before the cycle's deadline. **[owner]**
5. Neither week is locked by another request (see §7.4). **[owner]**
6. Both employees are active members of the open cycle.

Rest hours are not checked. **[owner]** Rules are re-checked on every important transition: creation, acceptance, each supervisor approval, revival from hold, and re-import. **[owner]**

The eligibility rules are **pure domain logic** with no Spring, web or database dependency. Each rule is its own class behind one interface, so each can be unit-tested in isolation. A failing rule returns a reason code that the API can translate. **[proposal]**

---

## 7. Swap workflow

### 7.1 Types (the unit is the week) **[owner]**
- `FULL_CYCLE`: both weeks with the same peer, in one request, approved or rejected together. Every rule must pass for both weeks.
- `SINGLE_WEEK`: week 1 or week 2.
- A different peer for each week means two independent `SINGLE_WEEK` requests.

### 7.2 Creation modes **[owner]**
- **Direct request:** the employee picks from a list showing **only eligible peers**, with their schedules, and sends to one person. Status `PENDING_PEER`.
- **Open offer:** the employee publishes a week (or both weeks for `FULL_CYCLE`). It is visible **only to eligible employees**. Status `OPEN`. The first to accept takes it.

### 7.3 Statuses

`OPEN` → `PENDING_PEER` → `PENDING_SUPERVISOR` → `APPROVED_BY_SUPERVISOR` → `SENT_TO_WFM` → `WFM_APPROVED` | `WFM_REJECTED` **[owner]**

Also: `REJECTED_BY_PEER`, `REJECTED_BY_SUPERVISOR` (reason required), `CANCELLED` (reason recorded), `EXPIRED` **[owner]**, and `ON_HOLD` **[owner, option b]**.

| From | Event | To |
|---|---|---|
| (new) | direct request created | `PENDING_PEER` |
| (new) | open offer published | `OPEN` |
| `OPEN` | an eligible employee accepts | `PENDING_SUPERVISOR` (the accepter is the peer; accepting is the peer's agreement) |
| `PENDING_PEER` | peer accepts | `PENDING_SUPERVISOR` |
| `PENDING_PEER` | peer rejects | `REJECTED_BY_PEER` |
| `OPEN`, `PENDING_PEER`, `PENDING_SUPERVISOR` | requester or peer cancels | `CANCELLED` |
| `PENDING_SUPERVISOR` | all required supervisors approve | `APPROVED_BY_SUPERVISOR` |
| `PENDING_SUPERVISOR` | any required supervisor rejects | `REJECTED_BY_SUPERVISOR` |
| `APPROVED_BY_SUPERVISOR` | coordinator withdraws | `CANCELLED` |
| `APPROVED_BY_SUPERVISOR` | included in an exported batch | `SENT_TO_WFM` |
| `SENT_TO_WFM` | coordinator records approval | `WFM_APPROVED` (applied to the effective schedule) |
| `SENT_TO_WFM` | coordinator records rejection | `WFM_REJECTED` |
| `OPEN`, `PENDING_PEER` | another request locks the same week | `ON_HOLD` |
| `ON_HOLD` | the locking request fails and re-check passes | previous status (`OPEN` or `PENDING_PEER`) |
| `ON_HOLD` | the locking request fails and re-check fails | `CANCELLED` (with reason) |
| `ON_HOLD` | the locking request reaches `WFM_APPROVED` | `CANCELLED` |
| `ON_HOLD` | its owner or peer withdraws | `CANCELLED` |
| any non-terminal except `SENT_TO_WFM` | deadline passes | `EXPIRED` |
| non-terminal | re-import makes it ineligible | `CANCELLED` (with reason) |

"The locking request fails" means it ends as `REJECTED_BY_SUPERVISOR`, `CANCELLED`, `WFM_REJECTED` or `EXPIRED`.

### 7.4 Locking and holds **[owner + proposal]**
- Before acceptance, a week may appear in many `OPEN` or `PENDING_PEER` requests.
- On acceptance (entering `PENDING_SUPERVISOR`), **both parties' weeks** are locked to that request. Every other `OPEN` or `PENDING_PEER` request touching either locked week moves to `ON_HOLD`, remembering its previous status.
- The rule "one locked request per week" is enforced **in the database** with a unique constraint on active week locks, not only in Java code. **[proposal]**
- Locks are released when the request fails, which revives held requests after a full re-check. They become permanent when it reaches `WFM_APPROVED`, which cancels the held requests.
- Everyone affected is notified when a request is held, revived or cancelled, with the reason.

### 7.5 Supervisor approval **[owner + proposal]**
- The required approvers are the supervisor of each party. One approval if they share a supervisor; two otherwise. **[owner]**
- Two supervisors approve **in parallel**, not in sequence. **[proposal]**
- A supervisor sees only requests involving their team members.
- If a supervisor name from the file is not linked to any account, the request waits in `PENDING_SUPERVISOR`, and the coordinator and admin see a warning. **[proposal]**

### 7.6 Cancellation **[proposal]**
- The requester or the peer can cancel until supervisor approval completes.
- After `APPROVED_BY_SUPERVISOR`, only the coordinator can withdraw a request, and only before it is sent to WFM.

### 7.7 Concurrency **[proposal]**
- "First to accept wins" for open offers is protected by optimistic locking (`@Version`) plus the database week-lock constraint.
- When two people accept at once, exactly one succeeds; the other gets a clear "already taken" error. There is a dedicated concurrency integration test.

### 7.8 Deadline
- A scheduled job expires eligible requests at the deadline. Creating and accepting requests is refused after it.
- **[proposal]:** requests already `SENT_TO_WFM` are not expired, so the coordinator can still record WFM's decision afterwards.

---

## 8. Re-import and reconciliation **[owner + proposal]**

- Re-importing a file with the same cycle start date creates a **new baseline version** of the cycle. Earlier versions are kept for audit.
- Non-terminal requests are re-validated against the new baseline. Those that no longer pass become `CANCELLED` with a reason. **[owner]**
- `WFM_APPROVED` swaps are **reconciled** with the new file:
  - If the file already shows the swapped schedules, the swap is marked *confirmed in file*.
  - Otherwise the coordinator sees a **conflict** and chooses "keep the swap on top" or "accept the file as it is".

---

## 9. WFM export **[owner]**

- An Excel file with **one row per employee per week** in each approved swap. Columns: request ID, type, employee, supervisor, channel, swapped with, and the new schedule (7 days).
- **Batches [proposal]:** the coordinator can export at any time. Each file contains only `APPROVED_BY_SUPERVISOR` requests not sent before, and exporting moves them to `SENT_TO_WFM`.
- **Recording WFM's decision:** per request, plus an "approve whole batch" action, since rejections are rare.

---

## 10. Accounts and authentication **[owner: JWT]**

- **Spring Security's built-in JWT support** (OAuth2 Resource Server with a locally held signing key). No custom JWT filters. **[owner + proposal]**
- **Tokens [proposal]:** short-lived access token (about 15 minutes) kept in memory by the frontend. Refresh token (about 7 days) in an `HttpOnly` cookie; it is rotated on every use, stored hashed, and revoked on logout.
- **Employee accounts [proposal]:** created automatically when an import is confirmed, from Email and User Name, with status `PENDING_ACTIVATION`. An activation email carries a one-time link (expires after 72 hours) where the employee sets a password. The coordinator can resend it.
- **Other accounts:** supervisor, coordinator and admin accounts are created by the admin. The admin links each supervisor account to the supervisor name as written in the file. After an import, the system lists supervisor names that have no linked account.
- Login with email or username, plus password. Passwords are hashed with Spring Security's default encoder.
- Swagger UI supports the bearer token, so a reviewer can try the API there.

---

## 11. Notifications **[proposal]**

- **In-app and by email** for every change that concerns a user: incoming request, acceptance, hold, revival, approval, rejection, expiry, and WFM decision.
- Locally, emails go to **Mailpit**, a fake mail server with a web inbox.

---

## 12. Audit log **[owner]**

- Every action is recorded: who (actor), what (action and target), when, and relevant details. This covers state transitions, imports, exports, WFM decisions, settings changes and account actions.
- Driven by domain events, so business code does not call the audit module directly. **[proposal]**
- Visible to admin and coordinator.

---

## 13. Internationalization **[owner]**

- UI in **Arabic and English with a toggle**. Arabic uses right-to-left layout.
- **[proposal]:** the API returns a stable **error code** plus a message localized from `Accept-Language` (Spring `MessageSource`). Eligibility reasons use the same codes.

---

## 14. Demo mode **[proposal]**

- A `demo` profile that seeds a realistic fake cycle (generated, never real data) and offers **one-click login per role** on the login page.
- Demo data resets every night.
- A fake-file generator also produces Excel files for tests and for trying the import.

---

## 15. Approaches considered **[owner chose A]**

| | Approach | Trade-off |
|---|---|---|
| **A** ✅ | **Modular monolith**: one Spring Boot app split by feature; **Spring Modulith** verifies module boundaries in a test; modules talk through domain events; eligibility and the status machine are pure Java | Simple to run and learn in; shows boundary thinking. Needs some discipline about what each module exposes |
| B | Layered monolith (`controller/`, `service/`, `repository/` for the whole app) | Easiest start, but concerns tangle as it grows, and it looks like a tutorial project |
| C | Microservices | Rejected: needless complexity for 70 users; eligibility needs schedule and request data in one transaction; time goes into Spring Cloud instead of Spring fundamentals; reviewers read it as over-engineering |

**Frontend [owner, after market check]:** **React + TypeScript** (Vite). Angular is common with Spring in Saudi banks and government; React is more common at the technology companies this portfolio targets.

**Modules:** `employee`, `schedule` (cycles, import, effective schedule, reconciliation), `eligibility`, `swap`, `wfm`, `notification`, `audit`, `security`, `common`.

---

## 16. Technical constraints

- **Stack [owner]:** Java 21, Spring Boot 4, Spring Web MVC (REST), Spring Data JPA, PostgreSQL, Flyway, Spring Security, Bean Validation, Apache POI, springdoc-openapi, JUnit 5, Mockito, Testcontainers. Plus Spring Modulith. **[owner, approach A]**
- **Repository [proposal]:** monorepo with `backend/` and `frontend/`, and one CI pipeline that tests both. The earlier skeleton at the repository root is replaced by one the owner creates inside `backend/`.
- **Database:** Flyway owns the schema (`ddl-auto: validate`). Tests run on real PostgreSQL through Testcontainers, never H2. `open-in-view` is off.
- **Time:** the business timezone is `Asia/Riyadh`. Timestamps are stored in UTC. A `Clock` is injected so the deadline logic is testable.
- **Data:** no real data in the repository; Excel and CSV files are git-ignored. **[owner]**
- **Deployment [owner]:** Docker, then Render (app) and Supabase (database), or an internal server.
- **Documentation [proposal]:** architecture decision records (ADRs) in `docs/adr/`, and a README that leads with the problem, includes an architecture diagram and a link to the live demo.

---

## 17. Learning process and phases **[owner]**

**How we work:**
- Explain each Spring concept before writing its code.
- One small phase at a time, with owner confirmation between phases.
- The owner writes key parts or answers comprehension questions.
- One clear commit per phase.
- Explanations in Arabic; code and names in English.

**Phases [proposal, revised]:**

| # | Phase |
|---|---|
| 0 | Local environment on Windows: JDK 21, Docker Desktop, VS Code extensions *(in progress)* |
| 1 | Create the project from VS Code (Spring Initializr) in `backend/`; structure; first controller written by the owner; CI |
| 2 | Data model, Flyway migrations, error handling with error codes |
| 3 | Excel import (preview → confirm) and the fake-file generator |
| 4 | Eligibility rules with thorough unit tests |
| 5 | Swap workflow: status machine, locks, holds, concurrency, deadline job |
| 6 | Security: accounts, activation, JWT, roles |
| 7 | Supervisor approvals, WFM export and decisions, reconciliation |
| 8 | Events with Spring Modulith: audit log and notifications |
| 9 | React frontend (in sub-phases), Arabic/English |
| 10 | Docker, deployment, demo mode, README, ADRs |

---

## 18. Edge cases settled after review **[proposal]**

An independent review of this design raised the following. They are settled here as design calls.

- **Swaps are same-week:** A's week *w* is always swapped for B's week *w*.
- **Female rule, precisely:** applied to **each** female party for the week she would receive. A "working shift" is any cell that is not `OFF`. Only the start time counts, and 15:00 passes. Re-checks use the settings in force at that moment. An open offer is shown to an employee only if both directions pass (for both weeks when the offer is `FULL_CYCLE`).
- **A held request can be blocked by several locks** (a `FULL_CYCLE` direct request touches up to 4 weeks):
  - every blocking lock is tracked;
  - the request revives only when all of them are released;
  - if any blocking request reaches `WFM_APPROVED`, the held request is cancelled.
- **Revival meets a new lock:** if the only failing rule is rule 5 (another lock appeared meanwhile), the request stays `ON_HOLD`. Any other failure cancels it.
- **Failed re-checks during transitions:**
  - On acceptance, the action is refused with the reason code and the request stays unchanged.
  - On a supervisor approval, a deadline failure gives `EXPIRED` and any other failure gives `CANCELLED` with the reason. Both release locks.
- **Re-import:**
  - It re-checks only the data rules (1, 2, 3, 6), never the deadline.
  - Requests in `SENT_TO_WFM` that fail are flagged as a conflict for the coordinator, not cancelled.
  - Validation runs against the new baseline plus confirmed swaps.
  - Only the open cycle can be re-imported.
  - If a party's supervisor changed, approvals from supervisors who no longer apply are dropped, and the required approvers are recomputed.
- **Reconciliation:**
  - The comparison covers all 7 days of both parties' weeks. A partial match is a conflict.
  - "Accept the file as it is" marks the swap *superseded by file*, stops applying it, and releases its locks. Held requests are not revived, because they were already cancelled.
- **Accounts from the file:**
  - A row without an email gets an import warning and no account.
  - Duplicate emails, HRIDs or user names in one file are errors.
  - Email and user name update from the latest import.
  - A returning employee's account is re-enabled.
  - "Forgot password" reuses the one-time-link mechanism.
- **Supervisor name matching:** names are compared after trimming and normalising spaces, ignoring case. One account may be linked to several names; each name links to at most one account.
- **Export details:**
  - Employees are identified by HRID (or email when it is missing) plus `Name AR`.
  - Each row shows its own employee's supervisor.
  - Day headers use the input format (`Sun 08-Nov`), and times are `HH:mm`, `OFF` or `V`.
  - An empty export is refused.
- **Cycle lifecycle:**
  - WFM decisions can still be recorded on a read-only cycle.
  - A start date earlier than the open cycle is refused.
  - A file that arrives after its deadline can be imported with a warning, but it opens no swap window.
- **Hosting sleeps** (Render and Supabase free tiers): the deadline is enforced inside every transition, not only by the scheduled job, and the expiry job also runs at startup. The demo cycle's dates are generated relative to "now", so the demo always has an open window.
- `docs/domain-rules.md` is superseded by this document and will be removed.

## 19. Left to the builder

- Table and column names, endpoint paths, DTO shapes.
- The exact rule-class breakdown, as long as each rule is testable alone.
- Library choices not listed above, such as a fake-data library or an Excel test helper.
- Exact token lifetimes, within the ranges above.
