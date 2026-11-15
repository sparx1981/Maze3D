package com.maze3d.audio

/** Everything the game can make noise about. Implemented by [SoundEngine]; [NoAudio] is the silent stand-in. */
interface Audio {
    /** Marble hit a wall: [strength] 0..1, [pan] -1 (left) .. 1 (right). */
    fun impact(strength: Float, pan: Float)

    /** Called every frame. [speed] 0..1 of top speed; [kind] selects the floor's rolling sound: 0 hard, 1 wood, 2 sand, 3 mud. */
    fun roll(speed: Float, kind: Int)

    fun doorOpen(volume: Float, pan: Float)
    fun doorClose(volume: Float, pan: Float)
    fun holeOpen(volume: Float, pan: Float)
    fun holeClose(volume: Float, pan: Float)
    fun fall(pan: Float)
    fun respawn(pan: Float)
    fun goal()
    fun levelGrow()
    fun levelSink()

    /** Cloud ambience level 0..1. */
    fun wind(amount: Float)

    /** Rain ambience level 0..1. */
    fun rain(amount: Float)

    /** Marble ploughing through a puddle. */
    fun splash(volume: Float, pan: Float)
}

object NoAudio : Audio {
    override fun impact(strength: Float, pan: Float) {}
    override fun roll(speed: Float, kind: Int) {}
    override fun doorOpen(volume: Float, pan: Float) {}
    override fun doorClose(volume: Float, pan: Float) {}
    override fun holeOpen(volume: Float, pan: Float) {}
    override fun holeClose(volume: Float, pan: Float) {}
    override fun fall(pan: Float) {}
    override fun respawn(pan: Float) {}
    override fun goal() {}
    override fun levelGrow() {}
    override fun levelSink() {}
    override fun wind(amount: Float) {}
    override fun rain(amount: Float) {}
    override fun splash(volume: Float, pan: Float) {}
}
