# jepsen-powersync-js-web

## Experimental Repository Only - This is *Not* a Real Jepsen Test (yet :)

### Testing [PowerSync's SDK for JavaScript Web clients](https://github.com/powersync-ja/powersync-js/tree/main/packages/web) with [Jepsen](https://github.com/jepsen-io/jepsen) for [Causal Consistency](https://jepsen.io/consistency/models/causal), [Atomic transactions](https://jepsen.io/consistency/models/monotonic-atomic-view), and Strong Convergence

----

#### Testing Reality

A strength of Jepsen is that it tests real systems with real faults.

This is problematic when testing web apps that run in the browser.

An initial implementation:

- using the [etaoin](https://github.com/clj-commons/etaoin) Clojure library
- to use the [WebDriver](https://w3c.github.io/webdriver/) protocol
- to execute a PowerSync transaction:
  - in a real web app developed with the PowerSync JS Web SDK
  - in an actual browser instance, Chrome headless

was not practical. High latency, low throughput, and occasional flakiness prevented Jepsen from being able to meaningfully test the app.

This repository is a follow on to try a different approach:

- **no** use of a `WebDriver`
- web app
  - does not use the "screen" (DOM)
  - but instead spawns a `WebWorker`
  - which opens a `WebSocket` back to the Jepsen control node
  - and implements Jepsen's `client` interface
