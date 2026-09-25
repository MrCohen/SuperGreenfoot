# Network module and dedicated servers

Four public classes in `greenfoot`: `Network`, `NetServer`, `NetClient` and
`NetEvent`. One copy of a scenario can be a server; others connect to it and
exchange text messages. The wire protocol is WebSocket (RFC 6455), so a
scenario exported to the web will be able to join a desktop server, and a
desktop client can reach a server behind a TLS proxy with a `wss://` address.

```java
// The host
NetServer server = Network.startServer(7777);
if (server.getError() != null) { /* port in use, in words */ }

// In act():
NetEvent e = server.poll();
while (e != null) {
    if (e.getType() == NetEvent.CONNECTED)         { /* e.getConnectionId() joined */ }
    else if (e.getType() == NetEvent.MESSAGE)      { handle(e.getConnectionId(), e.getText()); }
    else /* NetEvent.DISCONNECTED */               { /* e.getText() says why */ }
    e = server.poll();
}
server.send(id, "STATE;...");   server.broadcast("EVT;...");
server.kick(id, "the game is full");   server.stop();

// A player joining
NetClient client = Network.connect("192.168.1.5:7777");      // or connect(typed, defaultPort)
// In act(): client.getStatus() goes CONNECTING -> CONNECTED, or FAILED with client.getError()
client.send("HELLO;Ada");   NetEvent e = client.poll();   client.close();
```

## The contract scenarios rely on

Scenarios can count on these properties; they hold on the desktop, and the
browser build must keep them.

- **Threads stay inside the engine and never call scenario code.** Each
  connection has a reader thread and a writer thread; scenario code only
  ever calls `poll()` and `send()`, from its act method, and both return at
  once. A networked scenario is as single-threaded as any other.
- **Per connection, events are ordered:** one `CONNECTED`, then `MESSAGE`s
  in the order they were sent, then one `DISCONNECTED`, and nothing after
  that. Messages arrive whole; they are never split or merged. Only the
  reader thread adds a connection's events, which is what guarantees this.
- **Connection ids are never reused** while a server runs (a counter from 1).
- **`send` never blocks.** Messages are queued and written in the
  background. If a connection will not take what it is sent, it is dropped
  rather than letting the game wait (below).
- **Every drop says why, in words,** as the `DISCONNECTED` text on both
  sides: the other side's stated reason if it gave one (a `kick` reason
  travels in the WebSocket close frame, after everything sent before it),
  else the engine's own ("timed out: nothing heard for 20 seconds",
  "flooding: too many messages not yet read", "send buffer full: the other
  side is not keeping up", "connection lost", "server stopped").
- **Connecting is asynchronous and can fail.** `NetClient.getStatus()` is
  `CONNECTING`, `CONNECTED`, `CLOSED` (was connected) or `FAILED` (never
  was); `getError()` is fit to show a player: "no such host: x",
  "connection refused at host:port (is the server running, and the port
  right?)", "timed out connecting to host:port", "not an address: ...",
  "the server is full".
- **A client outlives a world change.** The connection belongs to the
  scenario, not to the world that opened it, so a "connecting" world can
  wait for the server's first message and then hand the client to the real
  world. Only a reset (or `Network.closeAll()`) closes everything.
- **Byte counters are from the wire:** `getBytesSent()` and
  `getBytesReceived()` count WebSocket frame and handshake bytes (before
  TLS), and the two ends of a connection agree exactly.

## Limits (`greenfoot.net.Link`)

| What | Limit | Past it |
|---|---|---|
| One message | 64 KiB of UTF-8 (`Network.MAX_MESSAGE_LENGTH`) | `send` throws `IllegalArgumentException`; a peer that sends one is dropped ("message too long") |
| Unsent bytes per connection (outbox) | 256 KiB | that connection is dropped: "send buffer full"; `getPendingBytes(id)` shows it growing |
| Unpolled messages per connection (inbox) | 2,000 messages or 1 MiB | the peer is dropped: "flooding" |
| Silence | ping after 5 s idle; dropped after 20 s without anything heard | "timed out" |
| Connect | 10 s to connect, 10 s for the handshake | "timed out connecting" |
| Connections per server | `Network.DEFAULT_MAX_CONNECTIONS` (64) or `startServer(port, max)` | refused after the handshake with "the server is full"; the server never sees it |

## Other API

- `Network.startServer(0)` picks a free port; `server.getPort()` reports it.
- `Network.connect(typed, defaultPort)` and `Network.normalizeAddress(typed,
  defaultPort)` accept `host`, `host:port`, `ws://host:port/path`,
  `wss://host` (443 unless given) and IPv6 in brackets (`[::1]:7777`).
- `Network.getLocalAddresses()` lists this machine's network addresses
  (IPv4 first, never loopback) for "join me at ...".
- `Network.canHost()` is true on the desktop; the browser build will return
  false, so a title screen can grey out Host.
- `server.getConnectionIds()`, `getRemoteAddress(id)`, `getConnectionCount()`,
  `setMaxConnections(n)`.
- A plain HTTP request to the server's port gets a one-line text answer
  saying it is a SuperGreenfoot game server, and no connection.
- **Reset closes everything.** `WorldHandler.discardWorld()` calls
  `Network.closeAll()`, so an IDE reset or a player reset frees the port.
  `Greenfoot.setWorld` does not.

## Dedicated servers

`java -jar game.jar --server` (or `PlayerMain <scenario> --server` during
development) runs the scenario with no window and nothing drawn, paced at
the scenario's speed, until the world stops itself.

- **Settings** come from `server.properties` next to the jar (or in the
  scenario folder), or `--server-properties FILE`; `--server-setting key=value`
  overrides one. `server.world` names the world class to run (default: the
  scenario's main world). The scenario reads any key with
  `Network.getServerSetting(key)` / `getServerSetting(key, default)`, and
  `Network.isDedicatedServer()` is true.
- **Console:** lines typed at the server's console queue up for
  `Network.pollConsoleCommand()`, one per call, null when none. The engine
  attaches no meaning to them; the scenario does (`list`, `kick`, `save`,
  `stop`...).
- **Stopping:** Ctrl-C or a TERM signal (what systemd sends) puts `stop` on
  the console queue and gives the world five seconds to save and call
  `Greenfoot.stop()`; the hook then flushes `Save` and prints
  "SuperGreenfoot dedicated server: stopped." A world that stops itself
  ends the process with exit code 0. A world that fails to start ends it
  with code 1 after 30 s.
- Saves go in `saves/` beside the properties file.

The runtime jar needs no extra JDK module for any of this: the WebSocket
implementation (`greenfoot.net`) is written on `java.net` sockets, with
`javax.net.ssl` for `wss://`, so exported games built by jlink with the
existing module list still run. Servers do not terminate TLS; put a
reverse proxy in front for `wss://`.

## What it is not

No UDP or unreliable channels, no binary messages, no RPC, no discovery,
NAT traversal, compression or per-message priorities. Reliable, ordered,
polled text is the whole design.

## Tests

`greenfoot/src/test/java/greenfoot/NetworkTest.java` (15) runs real
servers and clients on the loopback interface: ordering both ways,
kick-after-last-message, close reasons, server stop, refused/bad/unknown
hosts, port in use, server full, `closeAll` freeing a port, the message
limit, a flooding client, a reader that never reads (400 sends of 60 kB
must not block), fragmented frames, ping/pong and close from a hand-written
raw client, a plain HTTP request, address parsing, and the JDK's own
`java.net.http.WebSocket` client as an independent check that the server
speaks WebSocket. The whole set runs in about 0.3 s.
