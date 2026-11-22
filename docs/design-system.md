# Plate design system: poster layer (2.1)

Plate keeps its colours and layout: a blush background, plum text, a pink accent and soft
pastels. Version 2.1 adds a **poster** layer. It comes from recent CSS Design Awards Websites
of the Day (October 2026):

- **Henrietta Said** (spicy snacks): huge condensed capitals and a food-label ribbon along the bottom.
- **Surya Aditya** (top UI score, 10 from one judge): one oversized condensed name.
- **Casa Momo**: a quiet, warm, minimal look. This is why we add type and keep the colours.
- Portfolio winners (**Patmos**, **Giordano Betti**): numbered index lists, 01, 02, …

## Tokens

| Token | Value | Use |
|---|---|---|
| `Poster` font | Bricolage Grotesque, width 75, optical size 96, ExtraBold (`res/font/bricolage_condensed_800.ttf`) | Same family as the display font, but squeezed |
| `PosterType.hero` | 64 sp | One per screen: your name on Today, the streak number on Progress (96 sp) |
| `PosterType.title` | 40 sp | kcal left inside the ring |
| `PosterType.section` | 26 sp | Card and screen titles: MEALS, ADD FOOD |
| `PosterType.ticker` / `index` | 15 / 14 sp | Ribbon text, 01-style row numbers |

Rules: poster type is always in capitals, used for at most one big thing per screen, and never
for sentences. Colours come only from `PlateColors`. The ribbon uses `dayOver` (peach) with
`onDay` text.

## Components

- **Ticker** (`ui/components/Poster.kt`): a slow sideways ribbon. Removed from Today in 2.3 at the
  user's request (too busy); kept for possible later use.
- **MenuLine**: name, dotted leader, number in the poster cut, all on one baseline. Calories,
  weights and targets read like prices on a menu board (2.3).
- **IndexLabel**: 01, 02… in the accent colour, in front of each logged food.
- **PortionHelper** (`ui/screens/AddFood.kt` and `data/Portions.kt`):
  - Kitchen measures: tsp, tbsp, ¼, ½ and 1 cup. Cups are Canada's 250 ml.
  - For drinks, these become a shot, wine glass, bottle, glass, can or ½ L bottle, shown in ml.
  - The hand guide: thumb (1 tbsp), cupped hand (½ cup), fist (1 cup) and palm (a piece, about 100 g).
  - Grams are exact when Health Canada lists the food by ml (about 2,500 foods). Otherwise they
    use a typical density and are marked with ≈.

## Selfie of the day (2.2)

- **SelfieCard** (Today): built like the Meals card, with a poster title, a line under it and one round camera button. Camera only; there's no gallery option.
  - A photo shows as a frosted cover: a 48 px copy box-blurred three times, so it looks the same on every Android version.
  - Tap it, or use the screen lock if that's on, to see the photo, with Compare, Retake and Delete.
- **SelfieCompare**: full screen. Drag across to wipe from the before photo to the after photo. Two rows of day chips pick the days.
- **Privacy**:
  - Photos are saved only in app storage (`files/selfies`), shrunk to 1440 px and re-encoded, which strips location and other metadata.
  - They're left out of cloud backup and device transfer.
  - `FLAG_SECURE` keeps a showing photo out of screenshots and the recent-apps preview.
  - Everything hides again when Plate leaves the screen, and Reset deletes the photos.

## Make-it-yourself: shake builder (2.6)

- **The engine** (`data/Mix.kt`): ingredients × grams over the per-100 g Health Canada numbers already in the app.
  - Nothing is stored per recipe, and no shake rows go in the menu CSV.
  - Mixed berries = the average of blueberry, raspberry and strawberry.
  - The base is converted from ml to grams using that food's own density.
- **The screen**: full-screen sheet with three cards.
  - 1, Fruit and extras: chips, a search for any food, grams with natural quick amounts ("3 medium · 354 g").
  - 2, Base: chips plus ml.
  - 3, The math: one menu-board line per ingredient, then the batch, then one glass (≈).
- **Saving**: the shake becomes one of your foods (brand "Homemade", counted per glass, marked ≈) and one glass is logged.
- **Requests**: a homemade shake request offers "Build it now". The daily worker treats any shake request without a restaurant as done, because the builder can make it.
- **Next**: the same engine with other templates, such as chai, lassi, protein oats, salad bowls and home-cooked dal or curry for the whole pot divided into bowls.
