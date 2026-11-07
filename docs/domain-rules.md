# Domain rules

Agreed business rules. This file is the reference for the eligibility and workflow code.

## Process

1. WFM (external) sends the 14-day schedule as an Excel file.
2. A supervisor imports it, which creates a **schedule cycle**.
3. Employees create swap requests that must satisfy the rules below.
4. The supervisor of each party approves.
5. Approved swaps are exported to Excel for WFM; the supervisor records WFM's decision.

## Input file

- One sheet with all employees from all channels; row 1 holds the headers.
- Columns are read **by header name**, not by position.
- `Name AR | Channel | Email | User Name | HRID | Supervisor | Language | Gender | Comment`,
  then 7 day columns (`Sun 08-Nov` … `Sat 14-Nov`), `Days OFF`, 7 day columns, `Days OFF`.
- `Comment` is ignored.
- `Channel` is the department and `Language` is the skill (one value each). Values are read from the file and never hardcoded.
- Employee identity: `HRID`, falling back to `Email` when HRID is empty.
- Day cell: shift start time (`6:00` / `06:00`, stored as text or as an Excel time), `OFF`, or `V` (vacation).
- `Days OFF` is a count. It is checked against the OFF cells, and a mismatch gives a warning rather than rejecting the file.
- Cycle start = first day header. The year is inferred as the one closest to the import date in which that date is a Sunday. The 14 day columns must be consecutive.

## Eligibility rules

1. Both parties have the same `Channel` **and** the same `Language`.
2. A female employee may only receive a week where every shift starts between **06:00 and 15:00 inclusive** (both bounds configurable).
3. A week containing `V` on either side cannot be swapped.
4. Deadline: the Saturday before the cycle starts at **23:59 Asia/Riyadh**. The swap must reach **WFM approval** by then; requests still in progress are `EXPIRED`.
5. A week can be in only one active request. It is locked when the peer accepts, and other offers or requests on the same week of the same person are cancelled automatically.
6. Rest hours are not checked: accepting a swap means accepting the new schedule.
7. If the parties have different supervisors, both must approve.

Rules are re-checked on every important transition.

## Swap types (unit = one week)

- `FULL_CYCLE`: both weeks with the same peer, approved or rejected together.
- `SINGLE_WEEK`: week 1 or week 2.
- A different peer for each week means two independent requests.

## Statuses

`OPEN` → `PENDING_PEER` → `PENDING_SUPERVISOR` → `APPROVED_BY_SUPERVISOR` → `SENT_TO_WFM` → `WFM_APPROVED` / `WFM_REJECTED`

Also: `REJECTED_BY_PEER`, `REJECTED_BY_SUPERVISOR` (with reason), `CANCELLED`, `EXPIRED`.
On `WFM_APPROVED` the swap is applied to the schedules.

## Re-import and supervisors

- **Re-importing the same cycle** updates the schedules and re-validates active requests. Requests that no longer pass are cancelled with a reason.
- An employee missing from a new file becomes inactive for that cycle. History is kept.
- **Supervisors** appear in the file as names only. An admin creates supervisor accounts and links each one to the name as written in the file. After an import, the system lists supervisor names that are not linked to an account.
