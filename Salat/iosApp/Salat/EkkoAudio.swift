import AVFoundation
import MediaPlayer
import Shared
import UIKit

/// Spiller av lydene i Ekko, også med skjermen låst, og viser dem på låseskjermen og i kontrollsenteret.
/// Køen styres fra Kotlin. Her spilles én lyd om gangen, fra et sted i filen til et annet.
final class EkkoAudio: NSObject, AVAudioPlayerDelegate {
    static let shared = EkkoAudio()

    private var player: AVAudioPlayer?
    private var listener: EkkoPlayerListener?
    private var startSeconds = 0.0
    private var endSeconds = 0.0
    private var title = ""
    private var artist = ""
    private var lockScreen = false
    private var timer: Timer?
    private var lastReported = -1.0
    private var observing = false
    /// Volumet i Ekko, oppå volumet på telefonen.
    private var volume: Float = 1

    func play(
        clipId: String,
        start: Double,
        end: Double,
        from: Double,
        title: String,
        artist: String,
        lockScreen: Bool,
        listener: EkkoPlayerListener
    ) {
        stopPlayer()
        self.listener = listener
        self.startSeconds = start
        self.title = title
        self.artist = artist
        self.lockScreen = lockScreen

        let url = EkkoFiles.audio(clipId)
        guard FileManager.default.fileExists(atPath: url.path),
              let player = try? AVAudioPlayer(contentsOf: url) else {
            report { $0.onError(message: "Fant ikke lydfilen. Prøv å legge til lyden på nytt.") }
            return
        }
        activateSession()
        player.delegate = self
        player.volume = volume
        player.prepareToPlay()
        endSeconds = min(end, player.duration)
        player.currentTime = max(0, min(from, endSeconds))
        self.player = player
        player.play()
        startTimer()
        if lockScreen {
            setUpRemoteCommands()
        }
        updateNowPlaying()
        report { $0.onPlaying(playing: true) }
    }

    func setVolume(_ volume: Float) {
        self.volume = max(0, min(1, volume))
        player?.volume = self.volume
    }

    func pause() {
        guard let player, player.isPlaying else { return }
        player.pause()
        updateNowPlaying()
    }

    func resume() {
        guard let player else { return }
        activateSession()
        player.play()
        startTimer()
        updateNowPlaying()
    }

    func seek(to seconds: Double) {
        guard let player else { return }
        player.currentTime = max(0, min(seconds, endSeconds))
        updateNowPlaying()
        report { $0.onPosition(seconds: player.currentTime) }
    }

    func stop() {
        stopPlayer()
        listener = nil
        if lockScreen {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        }
        if !LiveSession.shared.isRunning {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    /// Setter Ekko på pause når noe annet i Salat skal bruke lyden, f.eks. Kompis.
    func pauseForOtherAudio() {
        guard player?.isPlaying == true else { return }
        pause()
        report { $0.onPlaying(playing: false) }
    }

    // MARK: Avspilling

    private func stopPlayer() {
        timer?.invalidate()
        timer = nil
        player?.delegate = nil
        player?.stop()
        player = nil
        lastReported = -1
    }

    private func activateSession() {
        // Kompis bruker lyden med mikrofonen på. Da lar vi den være som den er.
        guard !LiveSession.shared.isRunning else { return }
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)
        observeSession()
    }

    /// Sjekker hvor langt lyden har kommet, og stopper den der utsnittet slutter.
    private func startTimer() {
        timer?.invalidate()
        let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self] _ in self?.tick() }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    private func tick() {
        guard let player else { return }
        if player.currentTime >= endSeconds - 0.01 {
            finish()
            return
        }
        // Ti ganger i sekundet holder for at tiden og lydbølgen ser jevn ut.
        if abs(player.currentTime - lastReported) >= 0.1 {
            lastReported = player.currentTime
            let seconds = player.currentTime
            report { $0.onPosition(seconds: seconds) }
        }
    }

    private func finish() {
        stopPlayer()
        report { $0.onFinished() }
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        guard player === self.player else { return }
        finish()
    }

    /// Sender til Kotlin etter at det som skjer nå er ferdig, så Kotlin aldri kalles midt i et kall fra Kotlin.
    private func report(_ call: @escaping (EkkoPlayerListener) -> Void) {
        guard let listener else { return }
        DispatchQueue.main.async { [weak self] in
            guard self?.listener === listener else { return }
            call(listener)
        }
    }

    // MARK: Låseskjermen

    private func updateNowPlaying() {
        guard lockScreen, let player else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyPlaybackDuration: endSeconds - startSeconds,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: max(0, player.currentTime - startSeconds),
            MPNowPlayingInfoPropertyPlaybackRate: player.isPlaying ? 1.0 : 0.0,
            MPMediaItemPropertyAlbumTitle: "Ekko",
        ]
        if !artist.isEmpty {
            info[MPMediaItemPropertyArtist] = artist
        }
        if let image = UIImage(named: "EkkoArtwork") {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private var remoteCommandsReady = false

    private func setUpRemoteCommands() {
        guard !remoteCommandsReady else { return }
        remoteCommandsReady = true
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            guard let self, self.lockScreen, self.player != nil else { return .noActionableNowPlayingItem }
            self.resume()
            self.report { $0.onPlaying(playing: true) }
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            guard let self, self.lockScreen else { return .noActionableNowPlayingItem }
            self.pauseForOtherAudio()
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self, self.lockScreen, let player = self.player else { return .noActionableNowPlayingItem }
            if player.isPlaying {
                self.pauseForOtherAudio()
            } else {
                self.resume()
                self.report { $0.onPlaying(playing: true) }
            }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            guard let self, self.lockScreen else { return .noActionableNowPlayingItem }
            self.report { $0.onNext() }
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            guard let self, self.lockScreen else { return .noActionableNowPlayingItem }
            self.report { $0.onPrevious() }
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let self, self.lockScreen, let event = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            self.seek(to: self.startSeconds + event.positionTime)
            return .success
        }
    }

    // MARK: Avbrudd

    private func observeSession() {
        guard !observing else { return }
        observing = true
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(interrupted), name: AVAudioSession.interruptionNotification, object: nil)
        center.addObserver(self, selector: #selector(routeChanged), name: AVAudioSession.routeChangeNotification, object: nil)
    }

    /// En telefonsamtale eller en alarm: sett på pause, og fortsett etterpå hvis iOS sier det.
    @objc private func interrupted(_ notification: Notification) {
        guard let raw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, let player = self.player else { return }
            switch type {
            case .began:
                self.updateNowPlaying()
                self.report { $0.onPlaying(playing: false) }
            case .ended:
                let options = (notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt)
                    .map(AVAudioSession.InterruptionOptions.init(rawValue:)) ?? []
                if options.contains(.shouldResume), !player.isPlaying {
                    self.resume()
                    self.report { $0.onPlaying(playing: true) }
                }
            @unknown default:
                break
            }
        }
    }

    /// Tar man ut AirPods eller kobler fra høyttaleren, settes lyden på pause, som i Musikk.
    @objc private func routeChanged(_ notification: Notification) {
        guard let raw = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable else { return }
        DispatchQueue.main.async { [weak self] in self?.pauseForOtherAudio() }
    }
}
