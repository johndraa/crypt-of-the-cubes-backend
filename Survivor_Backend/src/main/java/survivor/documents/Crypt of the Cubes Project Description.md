# **📜 Crypt of the Cubes — Feature Description** 

## **Game Mode / Core Experience**

Real-time 4-player match over WebSockets (STOMP).  
 Server authoritative simulation at 20 Hz (Spring Boot service).

### **Server Responsibilities**

* Integrate latest player inputs (clamped).

* Compute per-player visibility using fog radii (below).

* Run enemy AI only for ACTIVE enemies; move and bounce off static obstacles on the shared map.

* Resolve attacks → hits → damage → deaths → drops (XP always, coins occasionally).

* Advance the world level and spawn pacing.

* Send visibility-filtered snapshots plus events (level-ups, deaths, drops).

  ### **Client Responsibilities**

* Send inputs at \~10 Hz (optionally delta-compressed).

* Render server snapshots and interpolate between last two for smooth motion.

* Show upgrade UI and send `upgradeCode` back to server.

* Implement fog-of-war rendering per radii below.

  ---

  ## **Fog of War (Server-Driven)**

* **R\_light** — visible radius (lit area)

* **R\_wake** — activation distance; enemies become ACTIVE when within this range

* **R\_sleep** — deactivation distance; enemies go dormant beyond this range

* Defaults: `R_light=10`, `R_wake=12`, `R_sleep=14` (no flicker or pop-in).  
   Snapshots include only entities with `dist ≤ R_light` (teammates can be exempt).

The map is **shared and static** — one dungeon layout for all matches, with obstacles defined in code or a static resource file.  
 Players and enemies bounce on collision with walls or obstacles.  
**Controls:** players move only — attacks auto-fire based on effective stats.  
**Match Start Sync:** server broadcasts

```json
{
  "matchId": 42,
  "fog": {"light": 10, "wake": 12, "sleep": 14},
  "countdown": 3
}
```

    

All clients load the same shared map and begin simultaneously after the countdown.  
**Level-Up Immunity:** briefly invulnerable during upgrade choice.  
---

## **Enemy Design**

* **Collision melee (bumper):** contact damage on overlap.  
   ✅ *High priority for Demo 3*

* **Arc melee (cone swipe):** short forward arc, shares CONE math with players.  
   ✅ *High priority for Demo 3*

* **Bosses:** tougher enemies with guaranteed coin drops and bonus XP.

* **No enemy projectiles** in core scope (optional later single straight-line boss projectile or delayed AOE).

  * Example: display an alert circle, apply delayed area damage 1 s later.

    ### **AI State Machine (Server)**

11. `ACTIVE   if minDistToAnyPlayer ≤ R_wake`  
12. `DORMANT  if minDistToAnyPlayer ≥ R_sleep`

    

Only ACTIVE enemies are simulated.  
 **Fairness guard:** enemies may start an attack only if target within `R_light + ε`.  
---

## **Scaling Difficulty — World Level**

Server tracks `worldLevel` as time increases:

* Higher levels → shorter spawn delay \+ stronger enemies.

* Each level has a defined spawn count; after cap, stats continue scaling.

* Boss appears every 5 levels (5, 10, 15, …).

  ---

  ## **Progression Systems**

  ### **Temporary (In-Match Upgrades)**

* Server tracks XP and emits `LEVEL_UP { accountId, options:[...] }` when threshold reached.

* **Items:** stackable small stat mods; do not change attack style.

* **Weapons:** one active; defines attackStyle \+ larger stat mods.  
   Picking a new weapon replaces the old one’s mods/style.

* All upgrades are in-memory only (reset on match end).

Flow:

1. Client shows 3 options.

2. Sends `UPGRADE_CHOICE { accountId, upgradeCode }`.

3. Server validates, applies, and broadcasts `UPGRADE_APPLIED`.

   ### **Permanent (Coins & Score)**

* **XP System:** Every enemy kill awards XP (guaranteed) to the killer for character progression
* **Coin System:** Enemies have a chance to drop coins (not guaranteed) to the killer - bosses always drop coins
* **Character Costs:** Premium characters require significant coin investment to encourage meaningful progression
* **Performance:** Drops are automatically awarded to killers (no position tracking) to maintain lightweight server design
* Server awards XP/coins on authoritative kills
* At match end, backend updates `user_progress` and writes summaries

  ---

  ## **Leaderboard**

`GET /progress/leaderboard` → top 50 by `user_progress.totalScore`.  
---

## **Combat: Attack Styles, Aiming Rules & Formulas**

Each character has a baseline **attackStyle** (`AOE | ORBIT | CONE`).  
 Everyone starts with a weak AOE aura; in-match weapons may switch style.  
 Effective stats \= base \+ sum(items) \+ weapon.mods.  
**Aiming Rules**

* `CONE`: aim along movement direction if moving; otherwise last non-zero direction.

* `AOE` / `ORBIT`: centered on player.

**Shared Formulas**  
 *(same clamp, intervalMs, damagePerHit, rollCrit, rangeFactor functions you already have)*  
**Style Behaviors**

* **AOE:** pulse radius \= `rangeFactor * baseAuraRadius`; damage pulse each interval.

* **CONE:** length \= `baseLen * rangeFactor`; arc ≈ `baseArc * (0.9 + 0.005*Range)`; fires each interval using movement/last-dir.

* **ORBIT:** orbit count and radius scale with Range; angular speed scales with AttackSpeed.

   All values clamped: min 80 ms interval, cap orbit counts, max movement speed.

  ---

  ## **Characters (Persistent)**

`game_characters` table holds base stats \+ baseline attackStyle.  
 Seeded on startup via `CharacterSeedRunner`; used by shop & validation.  
 At match start: everyone begins with AOE aura.  
---

## **Match Lifecycle**

1. **Lobby:** host creates/join code; presence/ready; WS chat; character select/lock.

2. **Start:** server validates → assigns spawns → broadcasts countdown \+ START → clients subscribe.

3. **During match:** integrate inputs → wake/sleep enemies → AI → attacks → damage/drops → XP (always) + coins (chance) → send snapshots/events.

4. **End:** last survivor or limit → finalize totals → persist summaries.

   ---

   ## **Enemy–Server Relationship**

* Server spawns using `worldLevel` pacing.

* Runs AI only within `R_wake`; sleeps beyond `R_sleep`.

* Sends snapshots filtered by `R_light`.

* All combat outcomes are server authoritative.

  ---

  ## **Anti-Cheat & Validation**

* Light clamps for sanity: max speed, min interval, cap counts.

* Server validates all authoritative damage and score.

* No hardened anti-cheat required.

  ---

  ## **Persistence vs In-Memory**

| Persistent (Spring Data) | In-Memory (per match) |
| ----- | ----- |
| accounts | active positions & HP |
| user\_progress | temporary items & weapon |
| game\_characters | XP, upgrades, AI targets |
| user\_character\_unlocks | visibility sets (R\_light/wake/sleep) |
| matches | upgrade choices/events |
| match\_participants |  |

  ---

  ## **UI / Screens**

* **User Mgmt:** signup/login/password recovery

* **Main Menu:** Host/Join Lobby, Shop, Leaderboard, Exit

* **Lobby:** presence/ready, chat, character select/lock, match settings

* **In-Game HUD:** HP, XP bar, world level, score, coins, upgrade pop-ups

* **End-of-Match:** wave/time, world level, coins, scores

  ---

  ## **Technical Stack**

**Frontend (Android)** — Java, STOMP, Retrofit/Volley, Canvas fog overlay  
 **Backend (Spring Boot)** — REST \+ STOMP, JPA \+ Hibernate, Lombok  
 Authoritative 20 Hz tick via `@Scheduled`.  
 Per-player snapshots via `/user/queue/...` topics.  

### **Mobile Optimization**

**Tile Size:** 24 pixels (optimized for mobile visibility)  
**Map Dimensions:** 53×40 tiles (1,272×960 pixels) - fits most phone screens  
**Fog of War:** 10-tile visibility radius (240 pixels) - balanced for mobile gameplay  
**Controls:** Touch-optimized movement-only input system  
**Performance:** 20 Hz server tick with efficient mobile rendering  

---

## **Demo 3 Responsibilities**

| Person | Task |
| ----- | ----- |
| **Alexandra** | Lobby chat \+ match start sync (countdown, spawns, enemy schedule) |
| **Yingxuan** | Lobby presence/ready \+ character selection/lock (exclusive picks) |
| **John** | Backend logic, DB schema, WebSocket runtime (match/attacks/AI) |

---

## **Non-Functional Requirements**

1. **Real-time performance:** 20 Hz tick, ≤10 ms/tick, ≤2 KB outbound/player.

2. **Fairness:** server-auth damage/coins/score, DB FK/unique constraints.

3. **Stability:** ≤2 % 5xx over 10 min; handle ≤10 matches×4 players.

4. **Backpressure:** reject lobbies if activeMatches ≥ 10 or CPU \> 70 % or DB pool \> 80 %.