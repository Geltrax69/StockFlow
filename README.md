# StockFlow — Intelligent Inventory Redistribution System

A pure-Java desktop simulation of a multi-warehouse, multi-store retail
supply chain. Java + Swing + AWT + Collections + Multithreading. No external
libraries, no JavaFX, no databases.

## Run

```bash
cd StockFlow
javac -d out $(find src -name '*.java')
java -cp out com.stockflow.App
```

## Architecture

```
com.stockflow
├── models/         # Plain data: Product, Inventory, Warehouse, Store, Truck, Transfer, CustomerOrder
├── core/           # State containers + helpers: World, InventoryIndex, LocationMap,
│                   # DemandModel, DemandBucket, StoreProductDemand, EventLogger, MetricsManager
├── engine/         # Threads: SimulationEngine, RedistributionEngine, OptimizationEngine,
│                   # OrderGenerator, DemandGenerator, TransportationSimulator
└── ui/             # Swing only: DashboardFrame, MapPanel, InventoryTable, TransferTable,
                    # LineChart, MetricsPanel, EventLogPanel
```

UI never mutates model state directly; the engine owns the threads. The UI
reads snapshot copies on the EDT.

## ALGORITHM.md — the hard part

See `ALGORITHM.md` for the full write-up on the redistribution engine,
complexity, tradeoffs, and how to scale to millions of products.
