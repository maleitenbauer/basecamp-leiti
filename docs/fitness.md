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

`/lifestyle/fitness/training` is an empty page registered in the module registry, nothing more. No tables or
endpoints yet.
