# Crypt of the Cubes API Documentation

**Base URL:** `http://coms-3090-051.class.las.iastate.edu:8080`  
**WebSocket Endpoint:** `ws://coms-3090-051.class.las.iastate.edu:8080/ws`  
**Pure WebSocket Chat:** `ws://coms-3090-051.class.las.iastate.edu:8080/classchat/{matchId}/{username}`

---

## Account Management

### Signup
**`POST /accounts/signup`**  
Creates a new account with automatic progress initialization and WANDERER character unlock.

**Request Body:**
```json
{
  "email": "player@example.com",
  "username": "PlayerName", 
  "password": "securePassword123"
}
```

**Success Response:**
```json
201 Created
{
  "id": 1,
  "email": "player@example.com", 
  "username": "PlayerName"
}
```

**Error Responses:**
- `400 Bad Request {"message": "<field> cannot be blank"}`
- `400 Bad Request {"message": "Invalid email format"}`
- `409 Conflict {"message": "email already in use"}`
- `409 Conflict {"message": "username already in use"}`

---

### Login
**`GET /accounts/login`**  
Authenticates user and returns account information.

**Query Parameters:**
- `email` OR `username` (required)
- `password` (required)

**Examples:**
- `GET /accounts/login?email=player@example.com&password=pass123`
- `GET /accounts/login?username=PlayerName&password=pass123`

**Success Response:**
```json
200 OK
{
  "id": 1,
  "email": "player@example.com",
  "username": "PlayerName"
}
```

**Error Responses:**
- `400 Bad Request {"message": "password is required"}`
- `400 Bad Request {"message": "provide email or username"}`
- `401 Unauthorized {"message": "invalid credentials"}`

---

### Update Account
**`PUT /accounts/{id}`**  
Updates account information. All fields are optional.

**Request Body:**
```json
{
  "email": "newemail@example.com",
  "username": "NewUsername",
  "password": "newPassword123"
}
```

**Success Response:**
```json
200 OK
{
  "id": 1,
  "email": "newemail@example.com",
  "username": "NewUsername"
}
```

**Error Responses:**
- `400 Bad Request {"message": "<field> cannot be blank"}`
- `400 Bad Request {"message": "Invalid email format"}`
- `404 Not Found {"message": "account not found"}`
- `409 Conflict {"message": "email already in use"}`
- `409 Conflict {"message": "username already in use"}`

---

### Get Account by ID
**`GET /accounts/{id}`**  
Retrieves account information by ID (testing only).

**Success Response:**
```json
200 OK
{
  "id": 1,
  "email": "player@example.com",
  "username": "PlayerName"
}
```

**Error Responses:**
- `404 Not Found {"message": "Account not found"}`

---

### List All Accounts
**`GET /accounts`**  
Retrieves all accounts (testing only).

**Success Response:**
```json
200 OK
[
  {
    "id": 1,
    "email": "player1@example.com",
    "username": "Player1"
  },
  {
    "id": 2,
    "email": "player2@example.com",
    "username": "Player2"
  }
]
```

---

### Delete Account
**`DELETE /accounts/{id}`**  
Deletes an account and all associated data (testing only).

**Success Response:**
```json
204 No Content
```

**Error Responses:**
- `404 Not Found {"message": "Account not found"}`

---

## Progress & Leaderboard

### Create Progress
**`POST /progress`**  
Creates progress tracking for an account (testing only).

**Request Body:**
```json
{
  "accountId": 1
}
```

**Success Response:**
```json
201 Created
{
  "id": 1,
  "accountId": 1,
  "coins": 0,
  "totalScore": 0,
  "rank": 1,
  "createdAt": "2025-01-27T10:30:00Z",
  "updatedAt": "2025-01-27T10:30:00Z"
}
```

**Error Responses:**
- `400 Bad Request {"message": "accountId is required"}`
- `404 Not Found {"message": "Account not found"}`
- `409 Conflict {"message": "Progress already exists for this account"}`

---

### Update Progress (Add Coins/Score)
**`PUT /progress/{id}`**  
Adds coins and/or score to existing progress totals.

**Request Body:**
```json
{
  "coins": 50,
  "totalScore": 300
}
```
*Note: Values are ADDED to existing totals, not replaced*

**Coin/XP Mechanics:**
- **XP:** Awarded on every enemy kill (guaranteed) to the killer
- **Coins:** Awarded randomly on enemy kills (not guaranteed) to the killer
- **Bosses:** Always drop coins + bonus XP to the killer
- **Character Costs:** Premium characters require significant coin investment
- **Performance:** No position tracking - drops automatically awarded for lightweight design

**Success Response:**
```json
200 OK
{
  "id": 1,
  "accountId": 1,
  "coins": 350,
  "totalScore": 12645,
  "rank": 7
}
```

**Error Responses:**
- `400 Bad Request {"message": "<field> must be >= 0"}`
- `404 Not Found {"message": "Account progress not found"}`

---

### Get Progress by Account
**`GET /progress/by-account/{accountId}`**  
Retrieves progress information including current rank.

**Success Response:**
```json
200 OK
{
  "id": 1,
  "accountId": 1,
  "coins": 350,
  "totalScore": 12645,
  "rank": 7
}
```

**Error Responses:**
- `404 Not Found {"message": "Progress not found"}`

---

### Get Player Rank
**`GET /progress/leaderboard/{accountId}`**  
Returns the current rank of a specific player.

**Success Response:**
```json
200 OK
{
  "accountId": 1,
  "rank": 7
}
```

**Error Responses:**
- `404 Not Found {"message": "Progress not found"}`

---

### Leaderboard (Top 50)
**`GET /progress/leaderboard`**  
Returns the top 50 players by total score.

**Success Response:**
```json
200 OK
[
  {
    "accountId": 3,
    "username": "alice",
    "totalScore": 12645,
    "rank": 1
  },
  {
    "accountId": 7,
    "username": "bob", 
    "totalScore": 12010,
    "rank": 2
  }
]
```

---

### Delete Progress
**`DELETE /progress/{id}`**  
Deletes progress tracking for an account (testing only).

**Success Response:**
```json
204 No Content
```

**Error Responses:**
- `404 Not Found {"message": "Account progress not found"}`

---

## Character System

### Get All Characters
**`GET /characters`**  
Returns the full character catalog (testing only).

**Success Response:**
```json
200 OK
[
  {
    "id": 1,
    "code": "WANDERER",
    "name": "Wanderer",
    "cost": 0,
    "health": 2,
    "moveSpeed": 2,
    "attackSpeed": 2,
    "damageMult": 2,
    "critChance": 2
  },
  {
    "id": 2,
    "code": "WARRIOR",
    "name": "Warrior",
    "cost": 500,
    "health": 5,
    "moveSpeed": 3,
    "attackSpeed": 2,
    "damageMult": 3,
    "critChance": 1
  }
]
```

---

### Get Character by ID
**`GET /characters/{id}`**  
Returns a specific character by ID (testing only).

**Success Response:**
```json
200 OK
{
  "id": 2,
  "code": "WARRIOR",
  "name": "Warrior",
  "cost": 100,
  "health": 5,
  "moveSpeed": 3,
  "attackSpeed": 2,
  "damageMult": 3,
  "critChance": 1
}
```

**Error Responses:**
- `404 Not Found {"message": "Character not found"}`

---

### Get Unowned Characters (Shop)
**`GET /accounts/{accountId}/shop/characters`**  
Lists characters available for purchase by this account.

**Success Response:**
```json
200 OK
[
  {
    "id": 2,
    "code": "WARRIOR",
    "name": "Warrior",
    "cost": 500,
    "health": 5,
    "moveSpeed": 3,
    "attackSpeed": 2,
    "damageMult": 3,
    "critChance": 1
  },
  {
    "id": 3,
    "code": "ROGUE",
    "name": "Rogue", 
    "cost": 500,
    "health": 2,
    "moveSpeed": 5,
    "attackSpeed": 5,
    "damageMult": 2,
    "critChance": 5
  }
]
```

**Error Responses:**
- `404 Not Found {"message": "Account not found"}`

---

### Get Owned Characters (Lobby)
**`GET /accounts/{accountId}/characters`**  
Lists characters owned by this account for lobby selection.

**Success Response:**
```json
200 OK
{
  "Owned": [
    {
      "characterId": 1,
      "code": "WANDERER",
      "name": "Wanderer"
    },
    {
      "characterId": 2,
      "code": "WARRIOR", 
      "name": "Warrior"
    }
  ]
}
```

**Error Responses:**
- `404 Not Found {"message": "Account not found"}`

---

### Purchase Character
**`POST /accounts/{accountId}/characters/{characterId}/purchase`**  
Purchases a character using coins from the account's progress.

**Character Pricing:**
- **WANDERER:** Free (starting character)
- **Premium Characters:** Significant coin cost (exact prices TBD)
- **Progression:** Requires multiple successful matches to afford new characters

**Success Response:**
```json
200 OK
{
  "message": "purchased",
  "characterId": 3,
  "newCoinBalance": 1200
}
```

**Error Responses:**
- `400 Bad Request {"message": "not enough coins", "have": 500, "need": 800}`
- `404 Not Found {"message": "Account not found"}`
- `404 Not Found {"message": "Character not found"}`
- `409 Conflict {"message": "characterId": 3, "already owned"}`

---

## Match Management

### Create Lobby
**`POST /matches/create`**  
Creates a new match lobby with a unique join code.

**Success Response:**
```json
200 OK
{
  "id": 42,
  "joinCode": "A9KXQZ",
  "status": "LOBBY",
  "createdAt": "2025-01-27T10:30:00Z"
}
```

---

### Join Lobby by Code
**`POST /matches/join/{joinCode}/{accountId}`**  
Joins an existing lobby using the join code.

**Success Response:**
```json
200 OK
{
  "matchId": 42,
  "joinCode": "A9KXQZ"
}
```

**Error Responses:**
- `404 Not Found {"message": "match not found"}`

---

### Get Lobby Snapshot
**`GET /matches/{matchId}/lobby`**  
Gets current lobby state (for debugging/testing).

**Success Response:**
```json
200 OK
[
  {
    "accountId": 1,
    "characterCode": "WARRIOR",
    "ready": true
  },
  {
    "accountId": 2,
    "characterCode": "ROGUE", 
    "ready": false
  }
]
```

**Error Responses:**
- `404 Not Found {"message": "match not found"}`

---

### End Match
**`POST /matches/{matchId}/end`**  
Marks a match as ended with optional winner.

**Request Body:**
```json
{
  "winnerAccountId": 1
}
```

**Success Response:**
```json
200 OK
{
  "ok": true
}
```

---

### Submit Match Results
**`POST /matches/{matchId}/results`**  
Submits final match results for all participants.

**Request Body:**
```json
[
  {
    "accountId": 1,
    "score": 1250,
    "coinsEarned": 45,
    "timeAlive": 120000
  },
  {
    "accountId": 2,
    "score": 800,
    "coinsEarned": 30,
    "timeAlive": 95000
  }
]
```

**Success Response:**
```json
200 OK
{
  "ok": true
}
```

---

## WebSocket Communication

### Connection Setup
**STOMP Endpoint:** `ws://coms-3090-051.class.las.iastate.edu:8080/ws`

**STOMP Destinations:**
- **Send to:** `/app/...`
- **Subscribe to:** `/topic/...`
- **Lobby:** `/topic/match.{matchId}.lobby`
- **Chat:** `/topic/match.{matchId}.chat`  
- **Game:** `/topic/match.{matchId}.game`

---

## Core Feature 1: Lobby Presence & Ready State

### Join Lobby
**Send:** `/app/lobby.join`
```json
{
  "matchId": 42,
  "accountId": 1
}
```

**Broadcast:** `/topic/match.42.lobby`
```json
{
  "matchId": 42,
  "players": [
    {
      "accountId": 1,
      "characterCode": "WANDERER",
      "ready": false
    }
  ]
}
```

---

### Leave Lobby
**Send:** `/app/lobby.leave`
```json
{
  "matchId": 42,
  "accountId": 1
}
```

**Broadcast:** Updated lobby snapshot to all remaining players

---

### Toggle Ready State
**Send:** `/app/lobby.ready`
```json
{
  "matchId": 42,
  "accountId": 1,
  "ready": true
}
```

**Broadcast:** Updated lobby snapshot showing ready status

---

## Core Feature 2: Lobby Chat (Two Options)

### Option A: STOMP-based Chat
**Send:** `/app/lobby.chat`
```json
{
  "matchId": 42,
  "accountId": 1,
  "username": "PlayerName",
  "text": "Hello everyone!"
}
```

**Broadcast:** `/topic/match.42.chat`
```json
{
  "matchId": 42,
  "accountId": 1,
  "username": "PlayerName",
  "text": "Hello everyone!",
  "sentAt": "2025-01-27T10:30:00Z"
}
```

### Option B: Pure WebSocket Chat (No STOMP)
**Connect:** `ws://coms-3090-051.class.las.iastate.edu:8080/classchat/42/PlayerName`

**Send Message:** Plain text
```
Hello everyone!
```

**Send DM:** 
```
@OtherPlayer Private message here
```

**Receive:** Plain text messages
```
PlayerName: Hello everyone!
[DM] PlayerName: Private message here
System: OtherPlayer joined the lobby chat
```

---

## Core Feature 3: Character Selection & Exclusive Locking

### Select Character
**Send:** `/app/lobby.select`
```json
{
  "matchId": 42,
  "accountId": 1,
  "characterCode": "WARRIOR"
}
```

**Success Response:** Updated lobby snapshot with character selected

**Failure Response (Private):**
```json
{
  "event": "SELECT_DENIED",
  "characterCode": "WARRIOR",
  "reason": "Character already selected by another player"
}
```

**Character Selection Rules:**
- **WANDERER:** Non-exclusive, free for all players
- **Other Characters:** Exclusive, only one player per match
- **Ownership Required:** Must own character to select it
- **Auto-unready:** Selecting character sets ready to false

---

## Core Feature 4: Match Start Sync

### Request Match Start
**Send:** `/app/lobby.start`
```json
{
  "matchId": 42
}
```

**Validation Requirements:**
- At least 2 players in lobby
- All players ready
- All players have selected characters

**Success Broadcast:** `/topic/match.42.lobby`
```json
{
  "matchId": 42,
  "fog": {
    "light": 10,
    "wake": 12, 
    "sleep": 14
  },
  "countdown": 3
}
```

**Failure Broadcast:**
```json
{
  "event": "START_FAILED",
  "reason": "Not all players ready",
  "unreadyPlayers": [1, 3]
}
```

---

## Core Feature 5: Player Attacks

### Send Movement Input
**Send:** `/app/match.input`
```json
{
  "matchId": 42,
  "accountId": 1,
  "moveX": 0.5,
  "moveY": -0.3,
  "seq": 12345
}
```

**Attack System:**
- **Auto-attack:** Attacks fire automatically based on stats
- **Attack Styles:** AOE (pulse) and CONE (directional)
- **Aiming:** CONE aims along movement direction, AOE is centered
- **Cooldowns:** Based on attack speed stat
- **Damage:** Calculated with crit chance and damage multiplier

**Game Snapshot (20 Hz):** `/topic/match.42.game`
```json
{
  "players": [
    {
      "id": 1,
      "pos": {"x": 100.5, "y": 200.3},
      "hp": 85,
      "stats": {...}
    }
  ],
  "enemies": [
    {
      "id": 1001,
      "pos": {"x": 150.0, "y": 180.0},
      "hp": 60,
      "type": "BUMPER"
    }
  ]
}
```

**Frontend Implementation Notes:**
- Snapshots are sent at 20 Hz (every 50ms)
- **Interpolation:** Frontend should interpolate positions between snapshots for smooth rendering
- **Visibility Filtering:** Enemies outside visibility radius are automatically filtered server-side
- **Attack Detection:** Frontend does NOT need to calculate collisions or attack hitboxes - just render based on DAMAGE events
- **Enemy State:** Frontend can infer enemy activity from snapshot presence (active enemies appear in snapshots)

---

## Core Feature 6: Enemy AI

### Enemy AI System (Backend-Only)
**Frontend Responsibilities:**
- **Interpolate positions** between snapshots (20 Hz) for smooth rendering
- **Render damage/death events** when received
- **Display enemy types** and visual states based on snapshot data
- **Use fog of war** to hide enemies outside visibility radius

**Wake/Sleep States (Backend Behavior):**
- **Wake Distance:** 12 tiles (enemies become active)
- **Sleep Distance:** 14 tiles (enemies go dormant)
- **Visibility:** 10 tiles (fairness guard for attacks)
- *Note: Frontend only receives enemies within visibility radius in snapshots*

**Enemy Types:**
- **BUMPER:** Contact damage on overlap (backend calculates collision)
- **SWIPER:** Cone attack in movement direction (backend calculates cone hit detection)

**AI Behavior (Backend-Only):**
- Only active enemies are simulated server-side
- Enemies seek nearest player when active (movement handled server-side)
- Fairness guard prevents attacks from outside visibility
- Different attack patterns per enemy type (all resolved server-side)

**Frontend Rendering:**
- Enemies appear/disappear based on visibility in snapshots
- Enemy positions update smoothly via interpolation between snapshots
- Damage indicators appear when DAMAGE events are received
- Death animations trigger when DEATH events are received

---

## Game Events

### Drop Events
**Broadcast:** `/topic/match.42.game`
```json
{
  "event": "DROP",
  "killerId": 1,
  "drops": [
    {
      "type": "XP",
      "amount": 25
    },
    {
      "type": "COIN",
      "amount": 10
    }
  ]
}
```
*Note: Drops are automatically awarded to the killer - no position tracking needed for performance*

### Damage Events
**Broadcast:** `/topic/match.42.game`
```json
{
  "event": "DAMAGE",
  "damages": [
    {
      "targetId": 1001,
      "targetType": "enemy",
      "damage": 15
    }
  ]
}
```

### Death Events
```json
{
  "event": "DEATH", 
  "deaths": [
    {
      "targetId": 1001,
      "targetType": "enemy"
    }
  ]
}
```

### Match End
```json
{
  "event": "MATCH_ENDED",
  "winnerId": 1,
  "results": [
    {
      "accountId": 1,
      "score": 1250,
      "coinsEarned": 45,
      "timeAlive": 120000
    }
  ]
}
```

---

## Technical Specifications

**Server Tick Rate:** 20 Hz (50ms intervals)  
**Tile Size:** 24 pixels  
**Map Dimensions:** 53×40 tiles (1272×960 pixels)  
**Fog of War:** light=10, wake=12, sleep=14 tiles  
**Max Movement Speed:** 160 px/s  
**Attack Styles:** AOE (pulse), CONE (directional)  
**Character Unlocks:** WANDERER (free), others purchasable  

---

## Frontend vs Backend Responsibilities

### What the Frontend Needs to Know for core gameplay

**✅ Required:**
- **Snapshot Structure:** Position, HP, enemy types for rendering
- **Event Types:** DAMAGE, DEATH, DROP, MATCH_ENDED
- **Interpolation:** Linear interpolation between snapshots (20 Hz → 60fps rendering)
- **Fog of War:** Render only entities visible in snapshots
- **Attack Visualization:** Trigger visual effects based on DAMAGE events (NOT hitbox calculations)

**📊 Data Flow:**
1. Receive snapshots at 20 Hz with positions/HP
2. Interpolate between last two snapshots for smooth rendering
3. Display damage indicators when DAMAGE events arrive
4. Play death animations when DEATH events arrive
5. Update UI when DROP events arrive

### What the Frontend Does NOT Need

**❌ NOT Required (Backend-Only):**
- `circlesOverlap()` - Circle collision detection (backend handles)
- `Cone.contains()` - Cone hit detection (backend handles)
- `dist()` - Distance calculations for wake/sleep (backend handles)
- Attack hitbox calculations (backend resolves hits server-side)
- Wake/sleep state logic (backend manages enemy activity)
- Attack cooldown tracking (backend enforces attack intervals)
- Damage calculation formulas (backend computes damage)

**Key Point:** The frontend is a **presentation layer** that displays server-authoritative game state. All combat logic, AI behavior, and game rules are handled server-side.

---

## Error Handling

All WebSocket messages return appropriate error responses:
- **START_FAILED:** Match cannot start (with specific reason)
- **SELECT_DENIED:** Character selection failed (with reason)
- **Validation Errors:** Field-specific error messages
- **Connection Errors:** Automatic reconnection recommended

**HTTP Status Codes:**
- `200` - Success
- `201` - Created
- `400` - Bad Request
- `404` - Not Found
- `409` - Conflict
- `500` - Internal Server Error