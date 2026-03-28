# SceneMax3D Demo Projects

This folder contains demo projects that showcase the capabilities of SceneMax3D Desktop. Each project demonstrates different aspects of the scripting language, from 3D scene management and animation to game logic and AI.

---

## Fighting Game

A fully playable 3D fighting game featuring two characters with a complete combat system, AI opponent, dynamic camera, and cinematic sequences. This project demonstrates how to build a complex, real-time interactive game using the SceneMax3D scripting language.
**Note**: for this game to run as expected, you will need to download its resources (Models / Sprites / Audio etc.) and import them to the game.
Download the resources ZIP file from: [Here](https://drive.google.com/file/d/1u9g_e4iRIM--yOfyFwgvQb2hgo9k3XvB/view?usp=sharing)
Import the resources by selecting the File/Import program... menu:
<img width="556" height="527" alt="image" src="https://github.com/user-attachments/assets/83e9cc82-c987-42dc-99a1-ea47a8e71fed" />


### Folder Structure

```
fighting_game_project/
├── scripts/Fighting Game/
│   ├── main                              # Entry point - canvas setup, module loading, intro sequence
│   ├── loading                           # Asset loading screen
│   ├── variables/
│   │   ├── constants                     # Game state enums, action codes, config values
│   │   └── global_variables              # Runtime variables, camera params, logical expression pointers
│   ├── game_init/
│   │   ├── game_init.smdesign            # Visual scene designer file (3D models, colliders, sprites)
│   │   ├── game_init.code                # Auto-generated initialization code from the designer
│   │   ├── game_init_init.code           # Pre-init code (sprites, lighting, environment)
│   │   └── game_init_end.code            # Post-init code (player data flags reset)
│   ├── game_states/
│   │   ├── game_start                    # Round begin - boss intro, "FIGHT" text, music
│   │   ├── game_over                     # Player defeat - KO cinematic camera, restart prompt
│   │   └── game_time_out                 # Timer expiry - timeout sequence
│   ├── game_input/
│   │   ├── input                         # Main keyboard input (movement, attacks A/S/D/Z, jump)
│   │   ├── input_x                       # Grab & throw mechanics (X key - 2-stage attack)
│   │   └── input_c                       # Takedown punch mechanics (C key - ground attack)
│   ├── states/
│   │   ├── player1_attack_states         # Player 1 hit resolution, damage, slow-motion effects
│   │   ├── player2_attack_states         # Player 2 hit resolution, damage to player 1
│   │   ├── player1_general_states        # Player 1 KO recovery, general state transitions
│   │   └── player2_general_states        # Player 2 down state, KO trigger
│   ├── utils/
│   │   ├── camera_strategy               # Dynamic ambient camera, shake effects, KO cinematic
│   │   ├── printing                      # HUD - health bars, timer display
│   │   └── tiger_utils                   # Tiger roar effect + camera attachment
│   ├── collision_new                     # Collision detection between player colliders
│   ├── enemy_ai                          # Opponent AI - distance-based combat decisions
│   ├── boss_ai                           # Boss idle behavior
│   └── enemy_knockout                    # Enemy KO sequence, victory celebration
├── resources/
│   ├── Models/                           # 3D character & environment models
│   ├── audio/                            # Sound effects and music tracks
│   ├── sprites/                          # 2D UI elements (health bars, hit effects, profiles)
│   └── skyboxes/                         # Skybox textures
```

### Game State Machine

The game uses a simple but effective state machine defined in [`variables/constants`](scripts/Fighting%20Game/variables/constants):

```
GAME_STATE_BEFORE_START = 0    // Initial loading and intro
GAME_STATE_START = 1           // Active fighting round
GAME_STATE_OVER = 2            // Round ended (KO or timeout)
```

The flow is:

1. **Intro** (`main`) - Title screen, "press space to start"
2. **game_start** (`game_states/game_start`) - Reset health/timer, boss intro cinematic, begin round
3. **Active Round** - Player input, AI, collision detection, and camera all run concurrently
4. **Round End** - Triggered by one of:
   - `life2 == 0` &rarr; `enemy_knockout` (player wins)
   - `life1 == 0` &rarr; `game_over` (player loses)
   - `timer == 0` &rarr; `game_time_out` (timeout)
5. **restart_game** (`game_states/game_over`) - Reset positions and health, loop back to `game_start`

### Control Flow

The game makes heavy use of SceneMax3D's [control flow](https://github.com/scenemax3d/scenemax3d-desktop/blob/main/docs/control-flow.md) constructs. Here are the key patterns used throughout the project:

#### Guarded Event Handlers

Conditions placed in square brackets `[condition]` before event handlers act as guards - the handler only fires when the condition is true. This is used extensively to prevent actions during invalid states:

```
[@allow_move]
when key W is pressed once do
  // Only executes when game is active and player is on the ground
  player1.character.jump at speed of 35
end do
```

See [`game_input/input`](scripts/Fighting%20Game/game_input/input) for all guarded input handlers.

#### Async Blocks

`do async ... end do` runs code blocks concurrently without blocking the main flow. The fighting game uses this to run animations, sounds, and camera movements in parallel:

```
do async
  camera_mode = CAMERA_MODE_CHASE_PLAYER1
  camera.chase player1
  wait 1 seconds
  camera.chase stop
  camera_mode = CAMERA_MODE_DEFAULT
end do

do async
  player1.big_jump : protected
  player1.idle2 loop
  player1.data.is_jumping = 0
end do

// Both blocks run simultaneously - camera chases while player jumps
```

#### State-Triggered Actions (`when ... do`)

The `when` construct monitors a condition and fires when it becomes true. This drives the reactive state machine - when collision flags are set, the appropriate damage/animation response triggers automatically:

```
when @player1_hand_attack_head do
  run player2_head_hit
  player1.data.hand_attack_hit = 0
  player2.data.head_hit = 0
end do
```

See [`states/player1_attack_states`](scripts/Fighting%20Game/states/player1_attack_states) and [`states/player2_attack_states`](scripts/Fighting%20Game/states/player2_attack_states) for the full set of state-triggered actions.

#### Timed Loops

`run function every N seconds` creates recurring execution. The game uses several concurrent timed loops:

```
run ambient_camera every 0.1 seconds       // Camera updates 10x/sec
run opponent_ai(player1,player2) every 0.8 seconds  // AI decisions
run rest every 10 seconds                   // Enemy health regen
run enemy_crazy every 2 seconds             // Random enemy behavior
run enemy_turn every 1.2 seconds            // Enemy facing adjustment
```

See [`enemy_ai`](scripts/Fighting%20Game/enemy_ai) and [`utils/camera_strategy`](scripts/Fighting%20Game/utils/camera_strategy).

#### Counted Loops

`do N times` repeats a block a fixed number of times:

```
do 6 times async
  audio.play "kick1"
  wait 0.1 seconds
end do
```

#### Conditional Functions (Guarded Functions)

Functions can have guard conditions - they only execute when the condition is true:

```
[camera_mode==CAMERA_MODE_DEFAULT]
shake_the_world = {
  camera_mode = CAMERA_MODE_SHAKE
  do 2 times
    camera.move backward 0.03 in 0.051 seconds
    camera.move forward 0.03 in 0.051 seconds
  end do
  camera_mode = CAMERA_MODE_DEFAULT
}
```

### Logical Expression Pointers

One of the most powerful patterns in this project is the use of **logical expression pointers** - variables prefixed with `@` that hold reusable boolean expressions. These are defined in [`variables/global_variables`](scripts/Fighting%20Game/variables/global_variables) and evaluated in real-time wherever they are referenced.

#### Definition

```
var @allow_move = game_status!=GAME_STATE_OVER && player1.data.is_jumping==0

var @asd_go_condition = player1.data.is_jumping==0
    && slow_motion==0 && game_status!=GAME_STATE_OVER
    && player_hit==0
    && player1_ko==0 && action != PLAYER_ACTION_X_2

var @enemy_ai_allowed = enemy_ko==0 && op_hit==0 && player1_ko==0
    && game_status!=GAME_STATE_OVER
    && slow_motion==0 && player2.data.trapped == 0
```

#### Usage as Guards

Once defined, these expressions are used as guards throughout the codebase, keeping the logic DRY and readable:

```
[@asd_go_condition]
when key A is pressed once do async
  // Kick attack - only allowed when all conditions in @asd_go_condition are met
end do
```

#### Composition

Logical expression pointers can reference other pointers, building complex conditions from simpler ones:

```
var @player1_can_hit = player1.data.is_jumping == 0 && player1_ko == 0

var @player1_hand_attack_head = @player1_can_hit
    && player1.data.hand_attack_hit == 1
    && player2.data.head_hit == 1

var @player1_grabs_for_throwing_player2 = @player1_can_hit
    && player1.data.hand_attack_hit == 1
    && action == PLAYER_ACTION_X_1
```

This composition pattern is used in [`collision_new`](scripts/Fighting%20Game/collision_new) and [`states/player1_attack_states`](scripts/Fighting%20Game/states/player1_attack_states) to cleanly express collision and attack resolution conditions.

#### Control Flow with Logical Expression Pointers and `when` Statements

The real power of logical expression pointers emerges when they are combined with `when ... do` statements. A `when` block is a **reactive listener** - the engine continuously monitors the condition and fires the block the moment it becomes true. When that condition includes an `@` pointer, you get a clean, declarative way to express complex game rules that respond to state changes automatically.

**Example: Foot attack to the head with knockout logic**

From [`states/player2_attack_states`](scripts/Fighting%20Game/states/player2_attack_states):

```
when @player2_can_hit && player2.data.foot_attack_hit == 1 && player1.data.head_hit == 1 do
  high_kick_counter = high_kick_counter + 1
  run player1_head_hit
  player1.data.head_hit = 0
  if (player2.data.is_jumping == 2) {
    player1_ko = 1
  } else {
    if (high_kick_counter % 3 == 0) {
      player1_ko = 1
    }
  }
end do
```

This single `when` block combines the `@player2_can_hit` pointer (which itself checks that player 2 is on the ground, player 1 isn't in a KO state, and an attack action is active) with additional inline conditions for foot collision and head hit detection. When all conditions align simultaneously, the block fires and applies the game rule: deal damage, and trigger a knockout if the kick landed during a jump descent or if every third high kick connects.

**Example: Hand attack to head - simple hit resolution**

```
when @player2_can_hit && player2.data.hand_attack_hit == 1 && player1.data.head_hit == 1 do
  run player1_head_hit
end do
```

The same `@player2_can_hit` pointer guards this handler, but with different inline conditions (hand collision instead of foot). The shared pointer ensures both handlers enforce the same base rules without duplicating the logic.

**Example: Enemy knockout trigger**

From [`states/player2_general_states`](scripts/Fighting%20Game/states/player2_general_states):

```
when life2 == 0 && enemy_ko == 0 do
  run enemy_knockout
end do
```

This `when` statement monitors the enemy's health and fires the moment it reaches zero - but only if the knockout sequence hasn't already started (`enemy_ko == 0`), preventing duplicate triggers.

**Example: Player knockout recovery**

From [`states/player1_general_states`](scripts/Fighting%20Game/states/player1_general_states):

```
when player1_ko == 1 do
  if (rnd(2) == 0) {
    player1.back_death1 at speed of 1.2 : protected
  } else {
    player1.right_death1 at speed of 1.2 : protected
  }
  wait 1.5 seconds
  player1.standing_up at speed of 1.2 : protected
  player1.idle2 loop
  player1_ko = 0
end do
```

When the `player1_ko` flag is set to 1 (by the foot-attack handler above or any other source), this listener fires automatically, plays a random death animation, waits, then plays the recovery animation and resets the flag.

**Example: State transition with `after` clause**

From [`enemy_knockout`](scripts/Fighting%20Game/enemy_knockout):

```
when op_hit == 0 after op_hit == 1 do
  life1 = INITIAL_PLAYER_STRENGTH
  life2 = INITIAL_PLAYER_STRENGTH
  timer = 60
  enemy_ko = 0
end do
```

The `after` clause adds transition detection - this fires only when `op_hit` transitions from 1 to 0, meaning all victory sequences have finished. This prevents the reset from happening prematurely.

#### Benefits of `when` with Logical Expression Pointers

1. **Reactive, event-driven design** - Instead of polling or checking conditions inside a game loop, `when` blocks let you declare *what should happen* when a state is reached. The engine handles the monitoring. This eliminates the need for manual frame-by-frame condition checking and makes the game logic inherently event-driven.

2. **Separation of "when to act" from "what to do"** - The `@` pointer defines the preconditions in one place (`global_variables`), while the `when` block defines the response in another (`player2_attack_states`). This separation means you can modify the rules for "when can player 2 hit" without touching any of the individual attack handlers - every `when` block referencing `@player2_can_hit` automatically picks up the change.

3. **Eliminating duplicated guard logic** - Without `@` pointers, every `when` block for player 2's attacks would need to repeat the full condition: `player2.data.is_jumping == 0 && player1_ko == 0 && op_action != 0`. With the pointer, this is written once and referenced everywhere. In this project, `@player2_can_hit` is used in 6 different collision handlers in [`collision_new`](scripts/Fighting%20Game/collision_new) and 4 attack state handlers in [`states/player2_attack_states`](scripts/Fighting%20Game/states/player2_attack_states) - that's 10 places where the guard logic is guaranteed to be consistent.

4. **Composable complexity** - Simple pointers combine into complex ones (`@player1_hand_attack_head` builds on `@player1_can_hit`), and those composed pointers can be mixed with inline conditions in `when` blocks. This lets you layer conditions naturally: the shared base rules live in the pointer, while handler-specific conditions are written inline.

5. **Real-time evaluation** - Logical expression pointers are not evaluated once at definition time. They are evaluated every time they are checked, reflecting the current game state. This means a single `@enemy_ai_allowed` pointer correctly gates all AI behavior as variables like `enemy_ko`, `slow_motion`, and `player2.data.trapped` change throughout gameplay.

6. **Self-documenting code** - A guard like `[@asd_go_condition]` immediately communicates intent ("the A/S/D attack keys should only work under these conditions") without requiring the reader to parse a multi-line boolean expression every time. The pointer name acts as documentation.

### Player Input

Defined in [`game_input/input`](scripts/Fighting%20Game/game_input/input), [`game_input/input_x`](scripts/Fighting%20Game/game_input/input_x), and [`game_input/input_c`](scripts/Fighting%20Game/game_input/input_c):

| Key | Action | Notes |
|-----|--------|-------|
| **W** | Big jump + forward lunge | Camera switches to chase mode during the jump |
| **Arrow keys** | Turn left/right, move forward/backward | Continuous press for turning, tap for movement |
| **Space** | Vertical jump | Repositions ambient camera on landing |
| **A** | MMA kick &rarr; flying kick combo | Two-stage attack with forward momentum |
| **S** | Hurricane kick | Sometimes triggers cinematic close-up camera |
| **D** | Quad punch &rarr; flying kick combo | Rapid 6-hit sound effect sequence |
| **X** | Grab &rarr; throw (2-stage) | First grabs the opponent, then throws on second press |
| **C** | Takedown ground punch | Only available when opponent is knocked down |
| **Z** | Duck/block | Defensive move with 1-second cooldown |
| **P** | Toggle debug mode | Development tool |

All combat inputs are guarded by `@asd_go_condition` which prevents actions during jumps, knockdowns, slow-motion, or game-over states.

### Camera System

The camera system in [`utils/camera_strategy`](scripts/Fighting%20Game/utils/camera_strategy) provides cinematic framing that adapts to the action in real-time.

**Modes** (defined in [`variables/constants`](scripts/Fighting%20Game/variables/constants)):

| Mode | Value | Behavior |
|------|-------|----------|
| `CAMERA_MODE_DEFAULT` | 0 | Dynamic ambient camera following the fight |
| `CAMERA_MODE_ATTACH_PLAYER1` | 1 | Fixed attachment to player 1 |
| `CAMERA_MODE_SHAKE` | 2 | Impact shake effect (blocks ambient updates) |
| `CAMERA_MODE_CHASE_PLAYER1` | 3 | Chase camera during jumps |

**Dynamic Ambient Camera** (`ambient_camera`, runs every 0.1 seconds):

The camera adjusts based on the distance between fighters:
- **< 3m** (close combat): Tight claustrophobic framing, low angle
- **3-5m**: Slightly pulled in, neutral height
- **5-8m**: Standard mid-range framing
- **> 8m**: Wide cinematic sweep

Additional dynamic behaviors:
- **Breathing wave**: Subtle +-0.6 unit oscillation on a 4-tick cycle
- **Jump lift**: Camera rises 1.8 units when a player jumps
- **Side switching**: Camera alternates left/right after jumps (`cam_side` flips on each big jump)

**Camera Effects**:
- `shake_the_world` - Quick forward/backward camera shake on impact hits
- `shake_the_world2` - Roll-based camera shake on heavy landings
- **KO Cinematic** (in [`game_states/game_over`](scripts/Fighting%20Game/game_states/game_over)) - A 9-step 360-degree orbital spiral that starts close and low, then rises and pulls out, creating a dramatic overhead shot of the fallen fighter

### Collision Detection

Defined in [`collision_new`](scripts/Fighting%20Game/collision_new), the system uses collider spheres attached to character joints (hands, feet, head) and a collider box for the body. Each collision pair is guarded by a logical expression pointer:

```
[@player1_hand_attack]
when player1_left_hand_collider, player1_right_hand_collider
    collides with player2_head_collider do
  player1.data.hand_attack_hit = 1
  player2.data.head_hit = 1
end do
```

The colliders are set up in [`game_init/game_init.code`](scripts/Fighting%20Game/game_init/game_init.code), where each sphere is attached to the corresponding bone joint of the 3D model (e.g., `player1_left_hand_collider.attach to player1."mixamorig:LeftHand"`).

When collisions are detected, they set flags on the player `data` objects. The state handlers in [`states/player1_attack_states`](scripts/Fighting%20Game/states/player1_attack_states) and [`states/player2_attack_states`](scripts/Fighting%20Game/states/player2_attack_states) then react to these flags, applying damage, playing hit animations, and resetting the flags.

### Enemy AI

The AI system in [`enemy_ai`](scripts/Fighting%20Game/enemy_ai) runs on multiple concurrent timers:

**`opponent_ai`** (every 0.8 seconds) - The main decision-maker, using distance-based strategy:

| Distance | Behavior |
|----------|----------|
| Player jumping (descending) | High kick interrupt if within 7m |
| < 3.5m (close) | Random: punch, high kick, or butterfly kick (when desperate) |
| 3.5-5.0m (mid) | Dash forward + fly kick or high kick |
| >= 5.0m (far) | Charge in with fly kick, butterfly kick, or jump attack |

The AI becomes more aggressive when health drops below 6 HP (`is_desperate`), favoring powerful butterfly kicks over simple punches.

**Supporting AI loops:**
- `rest` (every 10 seconds) - Regenerates +1 HP, capped at 10
- `enemy_crazy` (every 2 seconds) - Random flavor actions: tiger roar camera cuts, spins, fight sounds
- `enemy_turn` (every 1.2 seconds) - Subtle turning to track the player

All AI functions are guarded by `@enemy_ai_allowed`, ensuring the AI stops during knockouts, grabs, slow-motion, and game-over.

### Health & HUD

- Each player starts with `INITIAL_PLAYER_STRENGTH = 10` HP
- Health bars are rendered as sprites with frame-based animation (frame 0-15 maps to full-empty)
- A 60-second round timer counts down and is displayed on the HUD
- Player profile sprites are drawn at the top corners of the screen
- The HUD updates via `print_status` in [`utils/printing`](scripts/Fighting%20Game/utils/printing)
