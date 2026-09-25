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
    if (e.isConnected())          { /* e.getConnectionId() joined */ }
    else if (e.isMessage())       { handle(e.getConnectionId(), e.getText()); }
    else /* e.isDisconnected() */ { /* e.getText() says why */ }
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
  connection has a reader thread and a writer thread, and one watchdog
  thread serves every connection in the process; scenario code only ever
  calls `poll()` and `send()`, from its act method, and both return at once.
  A networked scenario is as single-threaded as any other.
- **Per connection, events are ordered:** one `CONNECTED`, then `MESSAGE`s
  in the order they were sent, then one `DISCONNECTED`, and nothing after
  that. Messages arrive whole; they are never split or merged. Only the
  reader thread adds a connection's events, which is what guarantees this:
  the watchdog only ever closes sockets, and the reader reports the end.
- **Connection ids are never reused** while a server runs (a counter from 1).
- **`send` never blocks.** Messages are queued and written in the
  background. If a connection will not take what it is sent, it is dropped
  rather than letting the game wait (below).
- **Every drop says why, in words,** as the `DISCONNECTED` text. When the
  drop is polite (a `kick`, a `close`, a server `stop`, a protocol error,
  flooding) the reason travels in the WebSocket close frame, after
  everything sent before it, so both sides see the same words. When a side
  drops the connection at once because the other is not responding ("send
  buffer full", "send timed out", "timed out: nothing heard for 20
  seconds") only that side knows the reason; the other side sees
  "connection lost" if it ever notices.
- **Connecting is asynchronous and can fail.** `NetClient.getStatus()` is
  `CONNECTING`, `CONNECTED`, `CLOSED` (was connected) or `FAILED` (never
  was); `getError()` is fit to show a player: "no such host: x",
  "connection refused at host:port (is the server running, and the port
  right?)", "timed out connecting to host:port", "not an address: ...",
  "the server is full", "the server is not reading messages (is the host's
  game paused?)". A full server refuses before the handshake, so the
  client ends `FAILED` and never reports `CONNECTED`. (In the rare case of
  two handshakes finishing together for the last slot, the loser is closed
  with the same reason right after connecting: `CONNECTED`, then
  `DISCONNECTED`, status `CLOSED`.)
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
| One message | 64 KiB of UTF-8 (`Network.MAX_MESSAGE_LENGTH`) | `send` throws `IllegalArgumentException` (so does `send(null)`); a peer that sends one is dropped ("message too long", close code 1009) |
| Unsent bytes per connection (outbox) | 256 KiB, counting every queued frame (messages, the one pending pong, the close frame) | that connection is dropped: "send buffer full"; `getPendingBytes(id)` shows it growing |
| One write | 20 s for the other side to take it | dropped: "send timed out: the other side stopped taking data" (a peer that reads nothing cannot hold the writer forever) |
| Unpolled messages per connection (inbox) | 2,000 messages or 1 MiB of text (counted in characters, which is what they cost in memory) | while the host is running, the peer is dropped: "flooding: too many messages not yet read" (close code 1013); while the host is paused, the newest messages are discarded instead (below) |
| Unpolled events per server, over every connection, live or gone | 20,000 events or 16 MiB of text | new connections are refused with "the server is not reading messages (is the host's game paused?)"; the connection that overruns it is dropped with the same words while the host is running, or discarded from while it is paused; polling frees it |
| Discarded while paused | 100,000 messages or 64 MiB from one connection, reset once the host polls it again | dropped after all: "flooding: far too many messages while the host was paused" |
| Silence | ping after 5 s with nothing to send; dropped after 20 s without anything heard | "timed out: nothing heard for 20 seconds" |
| Connect | 10 s to connect, then 10 s in total for the handshake, from the moment the socket was opened or accepted (a byte at a time does not restart it) | "timed out connecting to host:port"; a server closes the socket unanswered |
| Handshakes in progress | 64 per server, 8 per remote address; a handshake holds no connection slot, and a slot is counted only once the handshake completes | further sockets are closed unanswered |
| Closing | after a close frame is queued, the socket is forced closed 1.5 s later if the other side has neither taken it nor answered | the reason is the one already recorded (the kick reason, "server stopped", ...) |
| Connections per server | `Network.DEFAULT_MAX_CONNECTIONS` (64) or `startServer(port, max)` | refused before the handshake with an HTTP 503 whose reason is "the server is full"; no thread is started for it and the server never sees it |

Pongs: at most one is ever pending; a newer ping replaces the answer to an
older one, as RFC 6455 allows, so a peer that pings without reading cannot
fill the outbox. A large frame arriving over a very slow link counts as
silence until it is complete; at 64 KiB in 20 s that is a link too slow to
play on anyway.

## A paused host

Pausing in the IDE (or stepping with Act) stops the act methods, so nothing
polls; the network threads carry on, so players stay connected and pings
keep the links alive. Two things make this work as a pause rather than a
slow disconnect:

- **Tell the players.** Stock Greenfoot calls the world's `stopped()` when
  the simulation pauses and `started()` when it resumes, on the simulation
  thread. A host overrides them and broadcasts, say, `PAUSED` and
  `RESUMED`; the frames go out although nothing is acting, and a client can
  show "the host is paused" and stop sending input. Leaving is never a
  message: a player who quits closes the connection, and that always arrives
  as `DISCONNECTED`, paused or not.
- **Players who keep sending are not dropped.** While the host is paused
  (`Simulation.isPaused()`, which is also true before Run is pressed) a
  connection that overruns its inbox has its newest messages discarded
  instead: the first 2,000 wait, in order, for the host to resume and are
  then delivered; the rest are lost. A connection that sends 100,000
  messages or 64 MiB into a paused host is dropped after all, so the pause
  cannot be used against the host. A running host that does not poll is a
  different matter, a bug or an attack, and still drops the flooder.

A dedicated server never pauses. `NetworkPauseTest` runs a real headless
session through pause and resume.

## The wire

- Text frames must be valid UTF-8 (else close code 1007); binary frames are
  refused (1003); a close frame's payload must be empty or a valid status
  code followed by UTF-8 (else 1002); lengths must be encoded minimally.
- The server checks `Sec-WebSocket-Version: 13` (else `426 Upgrade
  Required`) and that the key is base64 of 16 bytes (else 400). A plain
  HTTP request gets a one-line text answer saying it is a SuperGreenfoot
  game server, and no connection.
- When the other side closes first, its close is echoed before the socket
  is closed (RFC 6455 section 5.5.1), so browsers see a clean 1000 rather
  than 1006.
- Addresses with control characters are not addresses (`connect` fails at
  once), so nothing a player types can reach the request line.

## Exposure

- **A server listens on every interface** of the machine unless told
  otherwise, so anyone who can reach the computer on the network can
  connect to the port: friends on the same Wi-Fi, and anyone else on it. On
  a school network, that is the whole school. That is the default because
  friends joining is the point. Stop the server (or reset the scenario) when
  you are not playing.
- **Local-only:** `Network.startServer(port, max, true)` binds the loopback
  address, so only programs on the same computer can connect, at
  `localhost` (or `127.0.0.1`); `server.isLocalOnly()` says which. Right for
  testing with two copies side by side, and for a class that should not be
  reachable from the corridor; a friend on another computer cannot join it.
  A dedicated server scenario that wants a setting for this can read one
  (`Network.getServerSetting("bind")`, say) and pass it in.
- **Origin is recorded, not enforced.** A web page open in a browser on the
  same machine (or network) can open a WebSocket to the server; browsers do
  not stop that. Browsers send an `Origin` header (`https://example.org`)
  and `server.getOrigin(id)` returns it, "" for a connection from a
  SuperGreenfoot scenario. The server refuses nothing by default, since the
  browser build's own players will carry an origin one day; a scenario that
  wants only its own page can check and kick. Either way a game should not
  trust a connection just because it exists: check what the first message
  says.
- **Passwords:** a scenario that wants one reads it with
  `Network.getServerSetting("password")` and checks it in its own first
  message. Put it in `server.properties` rather than on the command line
  (`--server-setting password=...` is visible to every user of the machine
  in `ps`).

## Other API

- `Network.startServer(0)` picks a free port; `server.getPort()` reports it.
- `Network.connect(typed, defaultPort)` and `Network.normalizeAddress(typed,
  defaultPort)` accept `host`, `host:port`, `ws://host:port/path`,
  `wss://host` (443 unless given) and IPv6 in brackets (`[::1]:7777`).
- `Network.getLocalAddresses()` lists this machine's network addresses
  (IPv4 first, never loopback) for "join me at ...".
- `Network.canHost()` is true on the desktop; the browser build will return
  false, so a title screen can grey out Host.
- `server.getConnectionIds()`, `getRemoteAddress(id)`, `getOrigin(id)`,
  `getConnectionCount()`, `setMaxConnections(n)`, `isLocalOnly()`.
- `NetEvent.isConnected()`, `isMessage()`, `isDisconnected()` beside `getType()`.
- **Reset closes everything.** `WorldHandler.discardWorld()` calls
  `Network.closeAll()`, so an IDE reset or a player reset frees the port at
  once; connections whose peer does not answer are forced closed 1.5 s
  later. `Greenfoot.setWorld` does not close anything. Failed and closed
  clients are forgotten as they end, so a retry loop does not pile them up.

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
  `Greenfoot.stop()`; the hook then flushes `Save`, closes every connection
  with "server stopped" and waits up to two seconds for those close frames
  to go out, then prints "SuperGreenfoot dedicated server: stopped." A world
  that stops itself ends the process the same way with exit code 0, so
  players see "server stopped" rather than "connection lost". A world that
  fails to start ends it with code 1 after 30 s.
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

`greenfoot/src/test/java/greenfoot/NetworkTest.java` (16) runs real
servers and clients on the loopback interface: ordering both ways,
kick-after-last-message, close reasons, server stop, refused/bad/unknown
hosts, port in use, server full (and room again after someone leaves),
`closeAll` freeing a port, the message limit, `send(null)`, a flooding
client, a reader that never reads (400 sends of 60 kB must not block),
fragmented frames, ping/pong and close (with its echo) from a hand-written
raw client, a plain HTTP request, address parsing, and the JDK's own
`java.net.http.WebSocket` client as an independent check that the server
speaks WebSocket. It runs in about 0.3 s.

`greenfoot/src/test/java/greenfoot/net/NetworkAbuseTest.java` (16) shrinks
the limits and timeouts (package-private knobs in `Link`) and checks what a
hostile or broken peer can and cannot do: a handshake sent a byte at a time
is cut off at the total deadline and holds no slot; handshakes in progress
are capped and so are the reader threads (counted by name); a full server
refuses with a 503 and no thread; a socket accepted around `stop()` is
closed; pings without reading leave at most one pong pending; a peer that
stops taking data is dropped by the write deadline, and a `kick` or `stop`
still closes it within the grace; a reconnect flood against a host that
never polls stays under the server-wide cap, and polling makes room again;
a close is echoed before the socket closes; the close codes for invalid
UTF-8, non-minimal lengths, bad close payloads and binary frames; the
handshake's version and key checks; a paused host keeps a player who
sends 3,000 messages and delivers the first 2,000 on resume, yet drops one
past the discard fuse; a local-only server refuses the machine's network
addresses; the origin is recorded. About 6 s, all of it waiting on the
shrunken timeouts. `greenfoot/player/NetworkPauseTest.java` (1) drives a
real headless session: `stopped()`'s broadcast reaches the client while
paused, the client survives 3,000 messages, `started()` announces the
resume and the kept messages are counted, in order.
