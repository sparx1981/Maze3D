# Marblix: surface and hazard ideas

Research notes drawn from web searches on Zool, Sackboy: A Big Adventure, Walkabout Mini Golf, Marble Blast Gold and Super Monkey Ball, plus general design knowledge of the genre. Where a game's detail came from a search it is marked (src); the rest is design judgement, not a fact about those games.

## Sources
- Zool: [Giant Bomb](https://giantbomb.com/wiki/Games/Zool), [TV Tropes](https://www.tvtropes.org/pmwiki/pmwiki.php/VideoGame/Zool), [Sega Retro](https://www.segaretro.org/Zool). Worlds named across sources: Sweet, Music, Tool, Fruit, Fairground, Toy, Sand. Tool World: hammers, drills, greased slippery platforms. Sweet World: jellies, bees, rafts on rivers. Music World: giant keyboard that plays notes underfoot, instruments that are hazards.
- Sackboy: [Game Developer Q&A](https://www.gamedeveloper.com/design/q-a-exploring-the-triumphant-level-design-of-i-sackboy-a-big-adventure-i-). Craft-material worlds (cardboard, felt, cloth), teleporters, rising and swaying tides, gauntlets of spinning sponges, grapple hook and boomerang levels.
- Walkabout Mini Golf: [Meta blog](https://www.meta.com/blog/walkabout-mini-golf-ice-lair-puffins-evil/). Courses include Sweetopia (candy), 20,000 Leagues (undersea), Ice Lair, Raptor Cliffs (dinosaurs), Meow Wolf (portals); hard modes.
- Marble Blast Gold: [wiki](https://marbleblast.fandom.com/wiki/Levels). Bumpers, fans, mines, oil slick, tornadoes, trap doors, narrow catwalks, pistons, gravity modifiers.

## New surfaces (what the floor is and how it feels)
| Surface | Look | Gameplay twist | Effort |
|---|---|---|---|
| Ice / rink | glossy blue-white, reflections | very low friction, ball keeps sliding; walls bounce | low: friction value + shader |
| Candy-jelly (Sweet World) | translucent jelly floor, gummy walls | bouncy floor: ball hops after drops | medium |
| Toy blocks (Toy World) | primary-colour plastic bricks, studs | stud bumps give gentle rumble; brick-road paths | low |
| Craft felt / cardboard (Sackboy) | stitched fabric, corrugated card | purely visual plus soft sound | low |
| Music keyboard | black/white keys floor | each cell plays a note as you roll: you make music | medium (audio) |
| Tool shop / greased metal | oil sheen, bolts | oil patches: near-zero friction zones | low |
| Underwater / coral | caustics, bubbles | buoyancy: ball floats up slightly, slow-motion roll | medium |
| Volcano / lava | cracked basalt, glowing lava seams | lava pits instead of holes: fall = respawn | medium |
| Lunar / low gravity | grey regolith, craters | floaty bounces, long arcs after ramps | medium |
| Pinball table | lights, rails, plastic | bumpers kick the ball | medium |
| Dinosaur / jungle swamp | mud, ferns, bones | sticky tar pits | low (mud variant) |

## New hazards
| Hazard | Source of idea | How it would work here |
|---|---|---|
| Bumpers | Marble Blast, pinball | round posts that kick the ball away at speed |
| Fans / tornado | Marble Blast | zones that push the ball sideways or lift it slightly |
| Oil slick | Zool Tool World, Marble Blast | circular patch with almost no friction |
| Trap doors | Marble Blast | floor panel drops open for a moment after you step on it |
| Spinning sponge / paddle sweeper | Sackboy | rotating arm that sweeps across a corridor, shoves you |
| Pistons / crushers | Marble Blast | wall blocks that thrust across the corridor on a timer |
| Teleporters | Sackboy, Walkabout | paired pads; one level mixes them to reroute you |
| Rising and falling tide | Sackboy | water level rises in a region so cells become slow; falls again |
| Conveyor belts | genre classic | floor strips that move the ball along or against you |
| Gravity switches | Marble Blast gravity modifier | pad that flips gravity toward the maze's inside for a while |
| Mines | Marble Blast | proximity blasts that send you to the last checkpoint |
| Rafts / ferries | Zool | platforms on a water channel, like the swing but linear |
| Seesaw | classic | plank that tips as the ball rolls across; hold the balance |
| Spike strips / thorn vines | platformers | touch and respawn at checkpoint |
| Keys and gates | platformers | colour-coded key pickups open matching gates |
| Collectibles | Sackboy, Monkey Ball | optional gems/bananas for bonus time medals |

## Modes and systems
- Time-attack medals (bronze/silver/gold) per level, like Switchball.
- Daily seeded maze with a shared leaderboard.
- Mirror and rotate variants of existing levels.
- Marble abilities that cost a charge: brief magnet, brief weight (stick to the floor), brief float.
- Co-op or pass-the-phone races on the same seed.

## Suggested next picks (best value for effort)
1. Ice rink surface and oil slicks (friction is already in the physics).
2. Bumpers and conveyor belts (small physics additions, big variety).
3. Teleporters and trap doors.
4. Lava surface (reuses the pit code with a new shader).
5. Music keyboard surface (audio synth already exists).
