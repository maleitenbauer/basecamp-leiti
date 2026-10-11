# Weekly report and welcome messages

`/report` shows one week (Monday to Sunday) across every module: a headline and a few numbers per module, a per-day
bar chart where it makes sense, a handful of highlights, and an overall score when modules can give one. The home
dashboard has a small "Weekly report" tile that links to it.

## How a week is built

`GET /api/reports/weekly?weekStart=2026-03-02&today=2026-03-04` (both optional, ISO dates).

- `weekStart` is moved back to that week's Monday. It can't be in the future or before 2000-01-03 (400).
- `today` comes from the browser, because the browser knows the user's local date. The server accepts it only within
  two days of its own date (400 otherwise). If omitted, the server's date in the user's zone is used.
- A week is *in progress* while `today` is on or before Sunday. Only days up to `today` count: Wednesday evening is
  not "missed" Thursday's routines. Later days show as dashed, empty bars.
- The overall score is the average of the sections that gave a score; if none did, there is no score.

## Adding a module to the report

Implement `WeeklyReportContributor` (package `com.markus.basecamp.core`) as a Spring bean. The service collects every
bean, so no registration and **no frontend change** are needed; the page renders sections generically.

```kotlin
@Component
class MyReportContributor(private val things: ThingRepository) : WeeklyReportContributor {
    override val order = 50                        // sections are sorted by this; leave gaps (10, 20, 30, ...)
    override fun section(userId: Long, week: ReportWeek): ReportSection? {
        // return null when the module has nothing to say this week; the section is then left out
    }
}
```

- `ReportWeek` gives `start`, `end`, `today`, `days` (days up to today), `zone`, and `startInstant`/`endInstant` for
  timestamp queries. Always use the user's zone, not the server's.
- A section has: `headline`, optional `score` (0..1), `stats`, optional `bars` (one value per day, an optional goal
  line, `future = true` for days that haven't happened), and `highlights`. `ReportSupport` has helpers for day labels,
  percentages, durations (`45 min`, `2 h 5 min`), per-day bars and capping a list of highlights.
- Only give a `score` when "more is better up to a goal" is natural (routines done, practice done, staying under a
  calorie limit). Counts with no target, like todos, have no score; they would only make the average meaningless.
- Keep the maths in a pure function (see `RoutineReport.build`, `TodoReport`, `NutritionReport`,
  `Cs2PracticeReport`) so it's unit-testable without a database; the contributor just loads the data and calls it.
- A contributor that throws is logged and skipped; it never breaks the whole report.

Current sections: Routines (10), Todos (20), Nutrition (30), CS2 practice (40). Shopping isn't in the report yet.

## Welcome messages

`web/src/lib/greetings.ts` writes the warm lines on the dashboard and at the top of the report.

- **Rotation is deterministic**: the line is picked from a pool by `(day number + hash(salt)) % pool size`, so it
  changes every day (every week for the report) but doesn't flicker on reload, and two consecutive days never repeat.
  Different parts of the greeting use different salts so headline and line don't change in lock-step.
- **It follows the situation**: the headline depends on the time of day (morning, afternoon, evening, night). The
  line depends on today's progress (nothing planned, not started, in progress, one left, all done); a late-evening
  "not started" is gentle rather than pushy. While tile data is still loading the line stays neutral instead of
  claiming "nothing planned".
- **Weekly messages** depend on the score tier (great ≥ 85%, solid ≥ 60%, mixed ≥ 30%, quiet, or no score) and on
  whether the week is finished or still going. A quiet week is never blamed.

To add wording, append to the pools in the file. An entry is a plain string, or a small function when the line needs
numbers (see `IN_PROGRESS`). Adding to a pool reshuffles which day shows which line, which is fine.
