import greenfoot.*;

/**
 * SuperGreenfoot Phase 2 sound demo. Press Run, click the world, then:
 *
 *   SPACE  play a beep (mash it: copies overlap)
 *   1-5    play the beep at different pitches (playback rate)
 *   D      drum, panned to where the mouse is (left/right)
 *   M      start / stop looping music (streamed from a 12 s file)
 *   P      pause all / resume all
 *   -/=    music volume down / up
 *   X      mute / unmute effects
 *
 * Pausing the scenario (Pause button) holds all sound; Run resumes it.
 */
public class SoundWorld extends World
{
    private boolean musicOn = false;
    private boolean paused = false;
    private int beeps = 0;

    public SoundWorld()
    {
        super(600, 300, 1);
        Sounds.load("beep", "beep.wav");
        Sounds.load("drum", "drum.wav", SoundCategory.EFFECTS, 90);
        Sounds.setCategoryVolume(SoundCategory.MUSIC, 60);
        draw();
    }

    public void act()
    {
        String key = Greenfoot.getKey();
        if (key == null) {
            return;
        }
        switch (key) {
            case "space": Sounds.play("beep"); beeps++; break;
            case "1": case "2": case "3": case "4": case "5":
                GreenfootSound s = Sounds.play("beep");
                s.setPlaybackRate(0.5 + (key.charAt(0) - '1') * 0.5);
                beeps++;
                break;
            case "d":
                MouseInfo m = Greenfoot.getMouseInfo();
                double pan = m == null ? 0 : (m.getX() - getWidth() / 2.0) / (getWidth() / 2.0);
                Sounds.play("drum", 100, pan);
                break;
            case "m":
                if (musicOn) { Sounds.stopMusic(); } else { Sounds.playMusic("music.wav"); }
                musicOn = !musicOn;
                break;
            case "p":
                if (paused) { Sounds.resumeAll(); } else { Sounds.pauseAll(); }
                paused = !paused;
                break;
            case "-": Sounds.setCategoryVolume(SoundCategory.MUSIC, Sounds.getCategoryVolume(SoundCategory.MUSIC) - 10); break;
            case "=": Sounds.setCategoryVolume(SoundCategory.MUSIC, Sounds.getCategoryVolume(SoundCategory.MUSIC) + 10); break;
            case "x": Sounds.setMuted(SoundCategory.EFFECTS, !Sounds.isMuted(SoundCategory.EFFECTS)); break;
            default: return;
        }
        draw();
    }

    private void draw()
    {
        GreenfootImage bg = getBackground();
        bg.setColor(new Color(24, 28, 40));
        bg.fill();
        bg.setColor(Color.WHITE);
        bg.setFont(new Font("SansSerif", true, false, 20));
        bg.drawCenteredString("SuperGreenfoot sound demo", 300, 30);
        bg.setFont(new Font("SansSerif", false, false, 15));
        String[] lines = {
            "SPACE beep (overlaps)   1-5 pitched beeps   D drum panned to mouse",
            "M music " + (musicOn ? "ON" : "off") + "   P " + (paused ? "RESUME" : "pause all")
                + "   -/= music volume " + Sounds.getCategoryVolume(SoundCategory.MUSIC)
                + "   X effects " + (Sounds.isMuted(SoundCategory.EFFECTS) ? "MUTED" : "on"),
            "beeps played: " + beeps + "   active beep voices: " + Sounds.getActiveCount("beep")
                + "   music playing: " + Sounds.isPlaying("music.wav"),
            "Pause button holds all sound; Run resumes it."
        };
        for (int i = 0; i < lines.length; i++) {
            bg.drawCenteredString(lines[i], 300, 90 + i * 40);
        }
    }
}
