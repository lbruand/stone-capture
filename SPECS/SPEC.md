# Stone Capture — Specification

Status: draft v0.1

## 1. Overview

Stone Capture is a two-player, turn-based card duel for Android, written in Kotlin.
Players compete over a row of nine stones by building three-card formations on their
side of each stone. The player who captures enough stones wins.

The rules are a re-implementation of a well-known family of abstract card-duel mechanics
(game mechanics are not copyrightable). All names, artwork, text, sounds and theme in this
project must be original. See section 10.

### Goals

- Offline single-player game versus an AI opponent.
- Local pass-and-play for two humans on one device (stretch goal, see section 11).
- Clean separation between a platform-independent rules engine and the Android UI.

### Non-goals (v1)

- Online multiplayer, accounts, leaderboards.
- Special/tactics cards (possible later expansion).
- Monetization.

## 2. Game rules

### 2.1 Components

- **Stones:** 9 stones in a row, indexed 0..8, left to right.
- **Cards:** 54 cards. Each card has a **color** (6 colors) and a **rank** (1–9).
  Every color/rank combination exists exactly once.
- **Slots:** each stone has 3 card slots on each player's side (two sides: Player A, Player B).
- **Hands:** each player holds 6 cards.
- **Draw pile:** the remaining cards after dealing.

### 2.2 Setup

1. Shuffle all 54 cards.
2. Deal 6 cards to each player.
3. The rest forms the draw pile. All stones are unclaimed with empty slots.
4. Player A moves first (a seeded RNG decides in tests; random in play).

### 2.3 Turn structure

On their turn, a player must, in order:

1. **Play:** place one card from hand on a stone on their own side, in a free slot.
   Stones that are already claimed, or whose side of the current player is full, are not valid.
   Placed cards can never be moved.
2. **Claim (optional):** claim any number of stones they can prove are theirs (2.5).
   This happens after playing and before drawing.
3. **Draw:** draw one card from the pile if it is not empty. Otherwise the hand shrinks
   and play continues.

Some published rule sets let a player claim *before* playing instead. This spec follows the
play → claim → draw order; the engine may expose it as a setting later.

If a player has no legal play (all their sides are full or claimed), they skip the play
step and the game continues. (This case is not covered by the published base rules; it is a
house rule.)

### 2.4 Formations

When a side has three cards it is a **formation**. Formations rank from strongest to weakest:

| Rank | Name          | Definition                                |
|------|---------------|-------------------------------------------|
| 1    | Straight flush| 3 consecutive ranks, all the same color   |
| 2    | Triple        | 3 cards of the same rank                  |
| 3    | Flush         | 3 cards of the same color                 |
| 4    | Straight      | 3 consecutive ranks, mixed colors         |
| 5    | Mixed         | anything else                             |

Within the same formation rank, the formation with the higher **sum of ranks** is stronger.
If both rank and sum are equal, the side that **completed its third card first** wins.

### 2.5 Claiming a stone

A stone may be claimed by the current player if the outcome is certain:

- **Both sides full:** compare formations (2.4). Winner may claim.
- **Claimant's side full, opponent's side not full:** the claim is valid if no combination of
  cards that are still *available* can complete the opponent's side into a formation that
  beats the claimant's formation. A tie in strength is resolved by completion order, so an
  opponent who would complete *after* the claimant loses ties and cannot stop the claim.
- **Available cards** are every card that is not face up on a stone. The claimant's **own hand
  is not used as proof**: those cards still count as available to the opponent, even though
  the claimant knows where they are. Only cards visible on the table can be excluded.

A claimed stone is permanently owned by the claimant. No further cards may be placed on it.

The engine exposes `canClaim(state, stone, player): Boolean`. A claim attempt on an
unprovable stone is rejected (not a penalty), and the UI may offer an auto-claim.

### 2.6 Winning

The game ends immediately when a player owns either:

- **5 stones in total**, or
- **3 adjacent stones** (e.g. indices 3, 4, 5).

Claiming ends the game on the spot; no further claims are allowed once a player has won.

If both players run out of playable cards without a winner, the player with more
claimed stones wins; ties are a draw. (House rule: the published base rules do not describe
this case.)

### 2.7 Rule sources and verification

Checked against public descriptions of the original game and its reskin (BoardGameArena help
page, UltraBoardGames, RulesPal, BoardGameGeek FAQ summaries). Points they agree on: 9 stones,
54 cards (6 colors × 1–9), 6-card hands, 3 cards per side, the five formation tiers and their
order, sum then first-to-complete tie-break, claim by proof, win on 5 stones or 3 adjacent
stones, play continues when the draw pile is empty.

Points where sources differ or are silent (decisions made here):

| Topic | Decision |
|-------|----------|
| Claim timing | After playing, before drawing (BoardGameArena, reskin rules). One English rulebook reading puts it before playing. |
| Claimant's hand in proof | Not used; only table cards are excluded. |
| No legal play | Skip the play step (house rule). |
| Deck exhausted, no winner | Most stones wins (house rule). |
| Multi-round scoring (5 points + 1 per stone) | Out of scope for v1; single game only. |
| Tactics cards | Out of scope for v1. |

Re-verify against the publisher's rulebook before release if exact fidelity matters. This
project does not copy rulebook text (section 10).

## 3. Architecture

Multi-module Gradle project.

```
stone-capture/
├── core/          Pure Kotlin/JVM: rules, state, AI. No Android dependencies.
├── app/           Android app (Jetpack Compose UI, ViewModels).
└── SPECS/         Specs and design docs.
```

- `core` is the single source of truth for game state transitions and is fully unit-tested.
- `app` renders state and forwards user intents. It contains no rules logic.

### 3.1 Tech stack

- Kotlin (current stable), Gradle Kotlin DSL, version catalog (`libs.versions.toml`).
- Android: minSdk 26, Jetpack Compose (Material 3), ViewModel + StateFlow.
- Testing: JUnit 5 (core), kotlin-test, Compose UI tests (app).
- Persistence: DataStore for settings and an in-progress game snapshot (kotlinx.serialization).

## 4. Core domain model

```kotlin
enum class CardColor { RED, BLUE, GREEN, YELLOW, PURPLE, ORANGE }

data class Card(val color: CardColor, val rank: Int)       // rank in 1..9

enum class Player { A, B; fun other(): Player }

enum class Formation { MIXED, STRAIGHT, FLUSH, TRIPLE, STRAIGHT_FLUSH }

data class Side(val cards: List<Card>, val completedAt: Int?)  // completedAt = move counter

data class Stone(val index: Int, val sideA: Side, val sideB: Side, val owner: Player?)

enum class Phase { PLAY, CLAIM, FINISHED }

data class GameState(
    val stones: List<Stone>,          // size 9
    val handA: List<Card>, val handB: List<Card>,
    val drawPile: List<Card>,         // top of the pile is the first element
    val current: Player,
    val moveCount: Int,               // orders side completions (tie-break)
    val phase: Phase,                 // PLAY -> CLAIM -> (next player) PLAY ... FINISHED
    val winner: Player?,              // null with FINISHED means a draw
)
```

State is **immutable**; every transition returns a new `GameState`.

### 4.1 Actions and engine

```kotlin
sealed interface Action {
    data class PlayCard(val card: Card, val stoneIndex: Int) : Action
    data class Claim(val stoneIndex: Int) : Action
    object Pass : Action             // only when no card can be placed
    object EndTurn : Action          // draws and passes the turn
}

interface GameEngine {
    fun newGame(seed: Long? = null, firstPlayer: Player? = null): GameState
    fun legalActions(state: GameState): List<Action>
    fun apply(state: GameState, action: Action): Result<GameState>
    fun canClaim(state: GameState, stoneIndex: Int, player: Player): Boolean
}
```

- `apply` validates the action and returns `Result.failure` with a typed error on illegal input.
- All randomness (shuffle) is injected via a seeded `Random` so games are reproducible.

## 5. AI opponent

Interface:

```kotlin
interface Bot { fun choose(view: BotView, legal: List<Action>): Action }
```

A bot picks one action at a time and is called again until the turn ends. `BotView` holds only
the board, the bot's own hand and public counts (opponent hand size, draw pile size), so peeking
is impossible by construction.

Difficulty levels:

| Level  | Strategy |
|--------|----------|
| Easy   | Random legal card/stone; claims only when trivially provable. |
| Medium | Claims whatever is provable, then plays the move that most raises its estimated chance of winning a stone (sampled completions from unseen cards), favouring stones next to ones it owns. |
| Hard   | Monte-Carlo rollouts (determinization of unseen cards) with a time budget (~500 ms). |

Bots operate only on information a human would have (own hand + public board). They must
never read the opponent's hand or the draw pile order.

## 6. UI / UX

### 6.1 Screens

1. **Home:** New game, Continue, Settings, How to play.
2. **Game board:** see 6.2.
3. **Settings:** difficulty, sound on/off, color-blind mode.
4. **How to play:** short illustrated rules (own wording and art).
5. **Game over:** result, stones summary, rematch.

### 6.2 Game board

- Landscape-first layout; portrait supported.
- Nine stones in a row; opponent side on top, own side on bottom, 3 slots each.
- Own hand along the bottom edge (6 cards).
- Interaction: tap a card, then tap a stone to play it (drag-and-drop as an enhancement).
  Stones that are valid targets are highlighted.
- Claim: long-press or a dedicated button on a stone; provable stones show a hint badge
  (optional setting).
- Turn indicator, draw-pile count, last-move highlight, undo is **not** offered in vs-AI play.
- Animations for dealing, playing, claiming; respect system "reduce motion".

### 6.3 Accessibility

- Cards distinguished by **color and a symbol/pattern**, plus a color-blind palette.
- TalkBack labels for every card and stone ("Blue 7, stone 3, your side, slot 2").
- Minimum touch target 48dp; scalable text.

### 6.4 Language

English only. Strings still live in Android resources (not hard-coded) to keep the code clean.

## 7. Persistence

- Autosave the current `GameState` (serialized) after each action; restore on relaunch.
- Settings in DataStore.
- Optional local stats: games played/won per difficulty.

## 8. Testing strategy

**Core (must have high coverage):**

- Formation evaluation: every formation type, ordering, sum tie-breaks, completion-order tie-break.
- Deck: 54 unique cards, correct distribution.
- Legal-action generation and rejection of illegal actions (full side, claimed stone, card not in hand).
- Claim proof logic, including: opponent side empty/partial/full, tie resolved by completion order,
  only table cards excluded from the opponent's possible completions (claimant's own hand does not count as proof).
- Win conditions: 5 total, 3 adjacent, exhaustion without winner.
- Property tests: random playouts with random legal actions always terminate and never violate
  invariants (card conservation = 54, no duplicates, no >3 cards per side).
- Determinism: same seed + same actions → identical state.

**AI:** bots only return legal actions; Medium beats Easy over N seeded games.

**App:** ViewModel tests (intent → state), Compose UI tests for play and claim flows.

## 9. Build, CI and tooling

- GitHub Actions: build, unit tests, lint (ktlint/detekt) on every push and PR.
- Release build: signed AAB through a documented manual step (keystore not in repo).
- Package name: `io.stonecapture`.

## 10. Legal and branding constraints

- The game is called **Stone Capture**. Do not use the name, logo, artwork, rulebook wording
  or marketing language of any existing commercial game (including the trademarked names of
  the game whose mechanics inspired this one).
- All card art, board art, icons, sounds, and in-app text must be original or properly licensed
  (record sources/licences in `ASSETS.md`).
- Store listing must not imply any affiliation with other publishers or authors.
- Rules reimplementation only; no copying of rulebook text.

## 11. Milestones

1. **M1 — Core engine:** model, formations, actions, claim logic, win detection, tests.
2. **M2 — Bots:** Easy and Medium, with a headless simulation harness.
3. **M3 — Android MVP:** board UI, play vs Easy/Medium, autosave.
4. **M4 — Polish:** animations, sounds, accessibility, settings, how-to-play.
5. **M5 — Hard bot + pass-and-play.**
6. **M6 — Store release:** icons, listing, privacy policy, closed testing track.

## 12. Decisions

All previously open questions are settled with the defaults below.

- **Claiming:** explicit player action, with an optional auto-claim setting (off by default).
- **First player:** random for the first game; the loser of the previous game starts rematches.
- **Special cards expansion:** not in v1. Revisit after release.
- **Theme and art direction:** standing stones with simple flat, original vector art; card
  symbols and patterns double as color-blind aids.
- **Claim timing, no-legal-play and empty-deck rules:** as decided in section 2.7.
- **Language:** English only (section 6.4).
- **Package:** `io.stonecapture` (section 9).
