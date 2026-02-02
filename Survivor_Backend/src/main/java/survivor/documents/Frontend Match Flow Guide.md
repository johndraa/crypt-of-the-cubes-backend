# Frontend Guide: Starting a Regular Match Flow

## Regular Match Flow (Production Use)

### Step 1: Host Creates Lobby
**REST Endpoint:**
```
POST /matches/create
```
**Response:**
```json
{
  "id": 42,
  "joinCode": "A9KXQZ",
  "status": "LOBBY",
  "createdAt": "2025-01-27T10:30:00Z"
}
```
**Action:** Creates a match in the database. No WebSocket connection is created yet.

---

### Step 2: Players Join the Lobby
**Option A: REST Join (Initial Entry - Use This)**
```
POST /matches/join/{joinCode}/{accountId}
```
**Response:**
```json
{
  "matchId": 42,
  "joinCode": "A9KXQZ"
}
```
**Action:** Adds player to the database. Does NOT broadcast to other players yet.

**Note:** Use this when you only have the join code. After getting `matchId`, connect to WebSocket and use WebSocket join to broadcast presence.

---

### Step 3: Establish WebSocket Connection
**Connection:**
```
ws://your-server:8080/ws (STOMP endpoint)
```
**Action:** Establish STOMP WebSocket connection. This is REQUIRED for all real-time lobby operations.

---

### Step 4: Subscribe to WebSocket Topics
**Subscribe to:**
- `/topic/match.{matchId}.lobby` - Lobby state updates (players joining/leaving, ready status, character selection)
- `/topic/match.{matchId}.chat` - Chat messages (optional)
- `/topic/match.{matchId}.game` - Gameplay events (match start, game state, match end)

**Action:** Topics are created on-demand when you subscribe. No pre-creation needed.

---

### Step 5: Announce Presence via WebSocket
**Send to:** `/app/lobby.join`
```json
{
  "matchId": 42,
  "accountId": 1
}
```
**Broadcast Received:** `/topic/match.42.lobby`
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
**Action:** Broadcasts your presence to all connected players. Safe to call even if you already joined via REST (idempotent).

**Why both REST and WebSocket join?**
- **REST join:** Initial entry when you only have join code (before WebSocket connection)
- **WebSocket join:** Broadcast presence once connected (for real-time updates)

---

### Step 6: Select Character
**Send to:** `/app/lobby.select`
```json
{
  "matchId": 42,
  "accountId": 1,
  "characterCode": "WARRIOR"
}
```
**Broadcast Received:** `/topic/match.42.lobby` (updated snapshot)

**Error Response:** Private message if selection fails
```json
{
  "event": "SELECT_DENIED",
  "characterCode": "WARRIOR",
  "reason": "Character not owned"
}
```

---

### Step 7: Toggle Ready Status
**Send to:** `/app/lobby.ready`
```json
{
  "matchId": 42,
  "accountId": 1,
  "ready": true
}
```
**Broadcast Received:** `/topic/match.42.lobby` (updated snapshot with ready status)

---

### Step 8: Start Match
**Send to:** `/app/lobby.start`
```json
{
  "matchId": 42
}
```

**Success Response:** `/topic/match.42.lobby` (or game topic)
```json
{
  "matchId": 42,
  "fog": {
    "light": 15.0,
    "wake": 20.0,
    "sleep": 30.0
  },
  "countdown": 3
}
```

**Failure Response:** (if validation fails)
```json
{
  "event": "START_FAILED",
  "reason": "Not all players ready",
  "unreadyPlayers": [2, 3]
}
```

**Action:** 
- Validates all players are ready and have selected characters
- Creates game runtime
- Broadcasts countdown
- Match transitions to `ACTIVE` status

---

### Step 9: During Match (Automatic)
- **Game State:** Subscribe to `/topic/match.{matchId}.game` for snapshots and events
- **Player Input:** Send to `/app/match.input`
- **Match End:** Automatically detected and broadcast when ≤1 players alive

**Match End Event:** `/topic/match.{matchId}.game`
```json
{
  "event": "MATCH_ENDED",
  "winnerId": 1,
  "results": [
    {
      "accountId": 1,
      "score": 1250,
      "coins": 45,
      "kills": 12,
      "timeAliveMs": 120000
    }
  ]
}
```
**Action:** Automatically handled by `TickService`. No client action needed.

---

## WebSocket Message Summary (Production Use)

| Endpoint | Direction | Purpose |
|----------|-----------|---------|
| `/app/lobby.join` | Send | Announce presence after connecting |
| `/app/lobby.leave` | Send | Leave lobby |
| `/app/lobby.select` | Send | Select/lock character |
| `/app/lobby.ready` | Send | Toggle ready status |
| `/app/lobby.chat` | Send | Send chat message |
| `/app/lobby.start` | Send | Start the match |
| `/app/match.input` | Send | Send player input during gameplay |
| `/topic/match.{id}.lobby` | Subscribe | Receive lobby updates |
| `/topic/match.{id}.chat` | Subscribe | Receive chat messages |
| `/topic/match.{id}.game` | Subscribe | Receive gameplay state/events |

---

## REST Endpoints Summary

### Production Use (Regular Match Flow)
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/matches/create` | POST | Create new lobby |
| `/matches/join/{joinCode}/{accountId}` | POST | Initial join by code |

### Admin/Testing Only (Do Not Use in Regular Flow)
| Endpoint | Method | Purpose | Why Admin Only |
|----------|--------|---------|----------------|
| `/matches/{matchId}/end` | POST | Manually end match | Match ends automatically via gameplay |
| `/matches/{matchId}/results` | POST | Manually submit results | Results are calculated and saved automatically |
| `/matches/{matchId}/lobby` | GET | Get lobby snapshot | For debugging/testing only (use WebSocket instead) |

---

## Complete Flow Example

```javascript
// 1. Host creates lobby
const createResponse = await POST('/matches/create');
const { id: matchId, joinCode } = createResponse;

// 2. Host joins via REST (optional but recommended)
await POST(`/matches/join/${joinCode}/${hostAccountId}`);

// 3. Connect WebSocket
const stompClient = new StompJs.Client({
  brokerURL: 'ws://server:8080/ws'
});

// 4. Subscribe to topics
stompClient.onConnect = () => {
  // Lobby updates
  stompClient.subscribe(`/topic/match.${matchId}.lobby`, (message) => {
    const snapshot = JSON.parse(message.body);
    updateLobbyUI(snapshot);
  });
  
  // Chat
  stompClient.subscribe(`/topic/match.${matchId}.chat`, (message) => {
    const chatMsg = JSON.parse(message.body);
    displayChat(chatMsg);
  });
  
  // Game events
  stompClient.subscribe(`/topic/match.${matchId}.game`, (message) => {
    const event = JSON.parse(message.body);
    handleGameEvent(event);
  });
  
  // 5. Announce presence
  stompClient.publish({
    destination: `/app/lobby.join`,
    body: JSON.stringify({ matchId, accountId: hostAccountId })
  });
};

// 6. When player selects character
stompClient.publish({
  destination: '/app/lobby.select',
  body: JSON.stringify({ matchId, accountId, characterCode: 'WARRIOR' })
});

// 7. When player ready
stompClient.publish({
  destination: '/app/lobby.ready',
  body: JSON.stringify({ matchId, accountId, ready: true })
});

// 8. Start match
stompClient.publish({
  destination: '/app/lobby.start',
  body: JSON.stringify({ matchId })
});
```

---

## Important Notes

1. **REST join vs WebSocket join:** Use REST join first (by code), then WebSocket join (by matchId) to broadcast. Both call the same backend logic (idempotent).

2. **No WebSocket for creating:** Creating a lobby is REST-only. WebSocket is for real-time operations after creation.

3. **Match ending is automatic:** Do NOT call the REST `/end` endpoint. The server automatically detects match end (≤1 players alive) and broadcasts `MATCH_ENDED`.

4. **Results are automatic:** Do NOT submit results manually. The server calculates and saves results automatically when match ends.

5. **Lobby snapshot via WebSocket:** Use WebSocket subscriptions, not `GET /matches/{matchId}/lobby`. The REST endpoint is for debugging only.

