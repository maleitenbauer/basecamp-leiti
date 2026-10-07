# Lifestyle > Routine

Recurring things to do — scheduled like a calendar event or "N times a week", timed or just checked off — with one daily
reminder of what's still open. Backend package and schema stay `logbook`, like the other Lifestyle modules
(`com.markus.basecamp.logbook.routine`).

## Two kinds of routine

- **On a schedule** — due on specific days, the way a calendar repeat works (below). Meditation every day, trash every
  other Tuesday, rent on the 1st, a yearly check-up.
- **Times a week** — "3 times a week, on whichever days suit", counted in a Monday–Sunday week. It stays open until the
  week's target is met (a "3 times" routine needs three *different* days; a routine can only be done once per day).

## Schedules

The repeat menu follows the calendar model. Whatever you don't set is taken from the **start date**, as in a calendar.

| Repeat every… | Options |
| --- | --- |
| **N days** | — |
| **N weeks** | which weekdays (defaults to the start date's weekday) |
| **N months** | on day *D*, on the last day, on the *nth* weekday (e.g. second Tuesday), or on the last weekday (e.g. last Friday) |
| **N years** | on the start date's month and day |

Intervals count from the start: "every 2 weeks" is the start's week, then every second week after it.
**Ends:** never, on a date, or after N times.

Calendar edge cases are decided, not accidental:

- A day of the month that a shorter month doesn't have (the 31st) falls on that month's **last day**.
- A yearly Feb 29 falls on **Feb 28** in years without one.
- "After N times" is turned into the date of the Nth occurrence when saved (the count is kept so editing shows it), so
  everything else only deals with an end date.

### What counts as "open"

- **Scheduled:** open on a day it occurs and isn't done that day. On other days it's listed under *Not scheduled*, with
  its next date.
- **Keep reminding me until it's done** (per routine): an unfinished occurrence stays open — and in the reminder, marked
  `overdue since …` — until it's done, instead of lapsing at midnight. Meant for things like monthly or yearly tasks that
  you don't want to forget just because the day passed.
- **Times a week:** not done that day and the week's target not yet met.

## The page

- **Still open** first, **Done** below, **Not scheduled today** (dimmed) last. Checking something off moves it down.
- "Times a week" routines show the week as seven dots (Mon–Sun) and `done/target`.
- **Timed or not:** a routine can have a target time ("Meditation, 10 min") or none. Checking off a timed routine logs its
  target minutes; "change" lets you log a different amount. Routines without a time never store minutes.
- You can browse other days (◀ ▶ / date picker) to log something you forgot. "Today" is whatever day the browser says it
  is — the client sends the calendar day, so there is no server-side time zone guesswork for that.
- Editing replaces the whole routine (so a time can be removed, or a routine switched between the two kinds). Deleting
  removes its history.

## Reminder

One notification per day, at a time you choose (default **16:00**), in your time zone (saved from the browser when you
save the reminder settings). It lists what's still open, e.g. `Routines: 3 still open` with lines like
`Meditation · 10 min`, `Gym · 1/3 this week` and `Pay rent · overdue since 1 Mar` (first four, then `+ N more`). If
nothing is open at that time, nothing is sent — a monthly routine on a day it isn't due stays out of it. It uses the
shared notification system, so it appears in the bell and, if enabled, as a push notification (Settings →
Notifications).

It runs on the same once-a-minute scheduler pattern as the todo reminder: sent at or after the chosen time, at most once
per local day, even if the server was down at exactly that minute. Changing the time re-arms it for today. "Send a
reminder now" in the settings sends the current digest immediately for testing.

"Times a week" routines are included every day they are still open, so a "once a week" routine nudges daily until done.

## How it's stored

`logbook.routine` holds the schedule flat (`recur_unit`, `recur_interval`, `recur_weekdays` as a Monday-first bitmask,
`month_mode`/`month_day`/`month_nth`/`month_weekday`, `year_month`, `start_date`, `end_type`/`end_on`/`end_count`,
`carry_over`); the rule itself is the pure `RoutineRules.Recurrence`, which has no Spring in it and is covered by
`RecurrenceTest`. Migration `V12` converts V11's daily/weekly routines: daily becomes "every day", weekly becomes
"N times a week", exactly as they behaved before.
