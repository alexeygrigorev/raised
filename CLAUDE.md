# Raised

Android interval-workout app (HIIT + Raised). Configurable times, exercises,
rounds/sets, warm-up/challenge/cool-down, with presets.

Work is delegated to sub-agents via the `Agent` tool. The orchestrator (this
main thread) plans, briefs, parallelises, verifies, and merges. Sub-agents do
focused implementation work for one issue at a time and never see the
orchestrator's conversation.

The full process — implementer/reviewer loop, briefing, parallelisation,
verification, CI — lives in [process.md](process.md). Agent role pointers and
project knowledge live in [agents.md](agents.md). Locked design decisions live
in [docs/decisions.md](docs/decisions.md).

@process.md
