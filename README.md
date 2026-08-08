# optraIX

Superoptimized Minecraft server for computational redstone.

## Features

- Highly optimized runtime redstone execution (capable of averaging 200M TPS on mattbatwings' Redstone Tetris)
- Fast-ish compilation times
- Built-in WorldEdit commands with schematic streaming for fast pastes

## Running

optraIX requires JDK 25 and a Minecraft 1.20.4 client.

```bash
./gradlew run
```

The server starts on `0.0.0.0:25565` and stores worlds, players, and schematics in `run/`.

The following command line options are available:

```text
--host <address>
--port <port>
--view-distance <chunks>
--tps <ticks-per-second>
--run-dir <directory>
```

Pass them through Gradle with `--args`, for example:

```bash
./gradlew run --args="--port 25566 --view-distance 8"
```

## Redstone

optraIX is fully MCHPRS-conformant. It does NOT simulate vanilla quirks such as:

- Torch burnout
- Locationality
- Update suppression
- Update order

optraIX builds a datapath graph of the circuit and acts directly on that graph instead of raw blocks.

During compilation, chunk sections without redstone components are skipped. The remaining components become nodes connected by weighted edges, where the weight represents signal loss and side inputs are tracked separately. Dust is traced in 32×32-chunk regions and replaced with direct component-to-component edges, so wires are completely removed from the compiled circuit.

Linear runs of repeaters, comparators, and torches are fused into compact chain nodes. The final graph is locality-ordered and flattened into primitive arrays with packed node and edge state. At runtime, an event-driven queue only visits consumers whose inputs changed, while a priority scheduler handles delayed redstone ticks. Only changed I/O nodes are written back to the world.

When no compiled circuit is active, optraIX falls back to the MCHPRS engine. Pending ticks are transferred between the world and the compiled circuit when switching engines.

Use `/optraix compile` to compile the world. `/optraix status`, `/optraix pause`, and `/optraix resume` control the engine. `/stats` and `/tps` show runtime information.

## WorldEdit

### Selection

- `//wand`
- `//pos1`
- `//pos2`
- `//size`
- `//expand`
- `//contract`
- `//shift`
- `//sel`

### Editing

- `//set`
- `//replace`
- `//count`
- `//stack`
- `//move`

### Clipboard

- `//copy`
- `//cut`
- `//paste`
- `//rotate`
- `//flip`

### History

- `//undo`
- `//redo`

### Schematics

- `//schem`
- `//load`

Use `/help` in-game for command usage. Put `.schem` or `.schematic` files in `run/schematics/` to load them.

> [!WARNING]
> Edits larger than 1M blocks cannot be undone.
