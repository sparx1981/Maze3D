# Marblix: Google Play Console answers

## Create app (the form in your screenshot)
| Field | Value |
|---|---|
| App name | Marblix |
| Package name | com.marblix.game |
| Default language | English (United Kingdom) – en-GB |
| App or game | Game |
| Free or paid | Your choice. Free can never be switched to paid later. |
| Declarations | Tick both boxes |

## Upload
- **App bundle:** `dist/Marblix-1.0.1-release.aab` (version 1.0.1, version code 4). Upload it under Testing → Internal testing (or Closed testing) first.
- **Play App Signing:** accept Google's default ("Use Google-generated key"). Your upload key signs the files you upload, and Google re-signs the app for users.
- Each later upload needs a higher `versionCode` in `app/build.gradle.kts`.

## Main store listing
**App name** (30 max)
```
Marblix
```

**Short description** (80 max)
```
Tilt and roll a marble through twisting 3D mazes across 20 themed worlds.
```

**Full description** (4000 max)
```
Roll a marble through mazes wrapped around 3D shapes: spheres, cubes, tori, capsules, gems and even a trefoil knot. Tilt, steer and turn the world to find your way to the exit.

20 THEMED WORLDS
Neon city, candy land, motorsport, jungle ruins, Egyptian temple, medieval castles, lava, prehistoric, toy world, winter night, space and more, each with its own walls, props, lighting and animated scenery.

HAZARDS AND SURFACES
Dodge crushers, swinging hammers, sliding doors, sweepers, seesaws, conveyor belts, spring pads, rope bridges and castle drawbridges. Plough through sand, slide through mud, push through fog and water.

WAYS TO PLAY
• Quick play with Easy, Medium, Hard and Ultra maze sizes
• Daily maze: a fresh maze every day
• Race mode: beat the ghost of your best run
• Stats to track your progress

YOUR CONTROLS
Play with an on-screen joystick, tilt your phone, or use gravity steering. Gamepads work too. Switch between follow and chase cameras.

Fully offline. No ads, no accounts, no tracking.
```

**App category:** Game → Puzzle
**Tags (pick up to 5):** Maze, Puzzle, Arcade, Casual, Offline
**Contact email:** your email (required, shown publicly)
**Privacy policy URL:** the public link to `privacy-policy.html` once hosted (see below)

## Graphics (all in `dist/playstore/`)
| Asset | File | Play requirement |
|---|---|---|
| App icon | icon-512.png | 512×512 PNG |
| Feature graphic | feature-graphic-1024x500.png | 1024×500, no transparency |
| Phone screenshots | screenshot-1.png … screenshot-7.png | 2–8 images, 1080×1920 |

## App content (Policy → App content)
- **Privacy policy:** paste the hosted URL.
- **Ads:** No, my app does not contain ads.
- **App access:** All functionality is available without special access.
- **Content rating (IARC questionnaire):** Category "Game". Answer No to violence, blood, sexual content, profanity, drugs, gambling, user interaction/chat, sharing location, and purchases. Expected result: PEGI 3 / Everyone.
  - The Day of the Dead and Prehistoric worlds have cartoon skulls and skeletons as scenery. They aren't violent, so "No" for violence is correct.
- **Target audience:** choosing "13 and over" (13–15, 16–17, 18+) keeps things simple. If you include ages under 13, the app joins the Families programme, which adds extra rules and review.
- **Data safety:** "Does your app collect or share any of the required user data types?" → **No**. The app has no internet permission.
- **Government apps / Financial features / Health:** No / None / None.
- **News app:** No.

## Hosting the privacy policy (pick one)
1. **Google Sites (easiest):** create a free site, paste the policy text, publish, copy the link.
2. **GitHub Pages:** works if the repository is public, or from a separate small public repository.
3. Any website you already own.

Replace `YOUR-CONTACT-EMAIL` in the file before publishing it.

## Before production
New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before production access is granted. Check the Dashboard in Play Console for the current requirement on your account.
