# Phase 2 API additions (sound engine, full-screen play)

## Sound engine

Every sound now plays through one software mixer (`greenfoot.sound.SoundMixer`)
that sums all playing voices into a single audio line. Consequences:

- Any number of sounds overlap, including many copies of the same file.
- The same file is decoded once and shared (`SoundLibrary`); files over 1 MB
  are streamed instead of held in memory (music).
- Master, per-category and per-sound volume apply instantly and multiply.
- Pausing the scenario holds every playing sound; Run resumes them. Sounds
  started while paused (world constructors, interactive method calls) play at
  once, as in Greenfoot.
- No device, no problem: the mixer runs silently so play state still behaves.
- Formats: WAV, AIFF, AU (any rate, 8/16-bit, mono/stereo) and MP3. MIDI
  still uses the old player.

### GreenfootSound (unchanged API, plus)

| Method | Notes |
|---|---|
| `play()`, `playLoop()`, `stop()`, `pause()`, `isPlaying()`, `setVolume(int)`, `getVolume()` | Same semantics as Greenfoot 3.9 (play while playing does nothing; play on a looping sound finishes the loop; play resumes a paused sound). |
| `boolean isPaused()` | New. |
| `setPan(double)` / `getPan()` | -1 left, 0 centre, 1 right (constant power). |
| `setPlaybackRate(double)` / `getPlaybackRate()` | 0.05..16; 2.0 is twice as fast and an octave up. Random 0.9..1.1 makes repeated effects less mechanical. |
| `setCategory(SoundCategory)` / `getCategory()` | Default `EFFECTS`. |
| `getFilename()` | |

### Sounds (new static registry)

```java
Sounds.load("jump", "jump.wav");                               // decode now
Sounds.load("theme", "theme.mp3", SoundCategory.MUSIC);
Sounds.load("hit", "hit.wav", SoundCategory.EFFECTS, 80);      // base volume
Sounds.play("jump");                 // overlaps if already playing; returns the GreenfootSound
Sounds.play("hit", 100, -0.5);       // volume, pan
Sounds.playLoop("engine");
Sounds.playMusic("theme");           // loops in MUSIC, replaces previous music
Sounds.stopMusic();  Sounds.stop("jump");  Sounds.stopAll();
Sounds.pauseAll();   Sounds.resumeAll();
Sounds.isPlaying("jump");  Sounds.getActiveCount("jump");
Sounds.setMaxVoices("jump", 4);      // oldest copy is stopped beyond this (default 8)
Sounds.setVolume("jump", 60);        // future and current copies
Sounds.setCategory("jump", SoundCategory.VOICE);
Sounds.setMasterVolume(80);
Sounds.setCategoryVolume(SoundCategory.MUSIC, 40);
Sounds.setMuted(SoundCategory.EFFECTS, true);
```

- `Sounds.play("file.wav")` with a file name loads it on first use, so
  `Greenfoot.playSound(file)` now routes through the mixer and overlaps too.
- An unknown key that is not a file name prints one warning and returns a
  silent `GreenfootSound`, so a typo never crashes a game.
- `SoundCategory`: `EFFECTS`, `MUSIC`, `VOICE`, `AMBIENT`.

Volume mapping: level 0..100 becomes amplitude (level/100)^2, which sounds
roughly linear to the ear. The three levels multiply.

### Internals (for the player and web ports)

`AudioDecoder.PcmReader` converts anything Java Sound can read to 16-bit PCM
and then resamples to 44.1 kHz stereo float in plain Java, so the browser
port needs only a replacement decoder and output line. `SoundMixer.mixInto`
is pure and drives the unit tests without a device.

## Full-screen play (IDE)

Controls menu > **Full Screen** (Shortcut+Shift+F) opens a black window
covering the screen with the world scaled to fit. The same window:

- forwards keyboard and mouse input to the scenario (mouse coordinates are
  mapped back through the scale), so games play normally;
- shows a floating, draggable control bar with Act / Run-Pause / Reset / speed
  slider plus **Pixel-perfect** (whole-number scaling, nearest neighbour),
  **Hide Controls**, **Lock Controls**, **Exit Full Screen**;
- **Esc** hides or shows the bar; when locked, Esc is ignored, but **holding
  Esc for two seconds** unlocks and shows it (teacher recovery);
- leaves full screen automatically for `Greenfoot.ask()`, on Reset of the VM,
  and when the project closes.

Actor picking, dragging and context menus stay in the main window only;
in full screen, clicks while paused are just delivered to the scenario.

Not yet: scenario-side API (`Greenfoot.setFullScreen`, `setControlsLocked`)
needs a debug-VM-to-IDE command and comes with the standalone player in
Phase 3, where it is in-process.

## Choosing a world size that scales well

Fit-to-screen scaling is smooth (bilinear), so a world whose size is not a
whole-number fraction of the screen looks slightly soft. For crisp full screen
pick a world size that divides the target screen exactly and matches its
aspect ratio, then use **Pixel-perfect** in the full-screen bar:

| Screen (logical pixels) | Aspect | Crisp world sizes |
|---|---|---|
| 1920x1080 (most Windows laptops, TVs, projectors) | 16:9 | 640x360 (3x), 960x540 (2x), 1920x1080 (1x) |
| 2560x1440 | 16:9 | 640x360 (4x), 1280x720 (2x) |
| 1440x900 / 1512x982 / 1728x1117 (MacBook logical sizes) | 16:10 | 720x450 (2x of 1440x900), 1440x900 (1x); for the Pro sizes 756x491 or 864x558 (2x) |
| 1280x800 (older MacBook Air, Chromebooks) | 16:10 | 640x400 (2x), 1280x800 (1x) |

A 16:9 world on a 16:10 Mac screen is letterboxed top and bottom, which is
fine; a 4:3 world (the classic 600x400 Greenfoot default) leaves wide black
bars on every modern screen.
