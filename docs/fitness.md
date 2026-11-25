# Lifestyle > Fitness

Two pages under one submodule: **Nutrition** (built) and **Training** (a placeholder, set up but not doing anything yet).
Backend package and schema stay `logbook`, like the other Lifestyle modules (`com.markus.basecamp.logbook.fitness`).

## Nutrition

- **Daily goal**: one calorie limit per user. The summary shows what is left for the selected day, and turns red
  with "over the limit by" once it's exceeded.
- **Meal log**: every entry has an amount and a time. "Today" is whatever day the browser says it is — the client
  sends the calendar day with each entry, so there is no server-side time zone guesswork.
- **Your food library** (`logbook.food`): kcal per 100 g, plus an optional "one portion" size (e.g. 1 slice = 35 g).
  Adding a meal is: pick a food, enter grams (prefilled with what you used last time), done. With nothing typed,
  the list shows what you ate most recently.
- **Open Food Facts lookup**: if your library has no match, or you type a barcode, the app searches
  [Open Food Facts](https://openfoodfacts.org) (open data, ODbL) and shows kcal per 100 g. Picking a result copies
  it into your own library once, so later use never depends on the online service. The data is crowd-sourced, so the
  UI says to check the numbers, and it shows the attribution. A "Search Open Food Facts" link lets you search
  explicitly when your library has partial matches.
- **Just calories**: log a plain kcal number with an optional name, without a food.

Meal entries store a snapshot of the name and kcal, so editing or deleting a food later never rewrites history.
Changing the grams on a food-based entry recalculates its kcal.

### The only external call

`GET https://search.openfoodfacts.org/search` (text) and `GET https://world.openfoodfacts.org/api/v2/product/{code}.json`
(barcode), made by the API with a descriptive User-Agent; nothing is sent from the browser. Configured under
`basecamp.openfoodfacts` in `application.yml`. If the service is down, the lookup shows an error and everything
else keeps working.

## Training

`/lifestyle/fitness/training`: workout plans you run at the gym.

- **Plans**: a named list of exercises ("Push day"), each with sets, reps, an optional weight and an optional rest time.
  A plan can be linked to one of your routines (for example "Gym, 3 times a week").
- **Running a workout**: press *Start workout* on a plan (or *Empty workout*). The plan's exercises are copied into the
  workout, so editing or deleting the plan later never changes history. For each exercise you log the sets you did; the
  fields are prefilled from your last set, else from last time, else from the plan, so a normal set is one tap. You can
  fix or remove sets, and add an exercise on the spot. *Last time* shows what you did in the previous workout.
- **Rest timer**: after each set a countdown starts (the exercise's rest time, 90 s by default; 0 turns it off) and the
  phone vibrates when it's over. The screen is kept awake while training, where the browser allows it.
- **Time tracking**: the clock starts when you press Start. *Finish workout* stops it; the time goes into the history.
- **Automatic finish**: a workout still running after 2 hours is ended for you (`basecamp.training.auto-finish-minutes`,
  env `BASECAMP_TRAINING_AUTO_FINISH_MINUTES`). It is saved as ending shortly (5 minutes) after your last logged set, not
  at the 2 hours, so a forgotten workout doesn't turn into a long one. If nothing was logged at all it is dropped. You get
  a notification either way. A timer checks every 5 minutes, and opening the page checks too.
- **Routine link**: finishing a workout (or the automatic finish) ticks the linked routine off for that day, with the
  minutes you trained, so a "Gym 3 times a week" routine fills itself. Discarding a workout doesn't undo that.
- **Motivation**: a warm message when you start (it knows if it's your first workout, a comeback after a break, the
  last one of the week), an encouraging line while you train, and one when you finish (personal bests, weekly goal).
  They live in `web/src/modules/lifestyle/fitness/training/motivation.ts` and rotate like the dashboard greetings.
- **Personal bests**: after a workout, any exercise where your best set beat everything you ever did in it (heavier, or
  more reps at the same weight) is called out. The first time you do an exercise doesn't count. Exercises are matched by
  name, ignoring case and extra spaces.
- **Dashboard and report**: a Training tile (running workout with its timer, or this week's count) and a Training section
  in the weekly report (workouts, time, sets, weight moved, minutes per day).

Backend: `com.markus.basecamp.logbook.fitness` (`Training*` files), tables from `V14__fitness_training.sql`
(`workout_plan`, `workout_plan_exercise`, `workout_session`, `workout_exercise`, `workout_set`). A partial unique index
allows only one running workout per user. The endpoints are under `/api/logbook/fitness/training`.
