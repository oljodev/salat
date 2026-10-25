import AVFoundation
import Foundation
import Shared

/// Testen av tekst til tale i Kompis: henter lyd fra OpenRouter og spiller den av mens den kommer.
/// Hvert forsøk tas vare på, så det kan spilles igjen og sammenlignes.
final class TtsPlayer {
    static let shared = TtsPlayer()

    /// Lyden fra ett forsøk. Rå lyd er 16-bit mono med [sampleRate]; mp3 spilles av som en fil.
    private struct Clip {
        var data: Data
        var sampleRate: Double
        var isMp3: Bool
    }

    private var clips: [String: Clip] = [:]
    private var listener: TtsListener?

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private var connectedRate: Double = 0
    private var mp3Player: AVAudioPlayer?

    private var task: Task<Void, Never>?
    /// Øker hver gang noe stoppes, så lyd og hendelser fra det som ble stoppet ikke slipper gjennom.
    private var generation = 0
    private var playingId: String?
    private var pendingBuffers = 0
    private var downloadDone = true
    private var mp3Watch: Timer?

    // MARK: - Les opp

    func speak(id: String, apiKey: String, bodyJson: String, sampleRate: Int, callAudio: Bool, listener: TtsListener) {
        stop()
        self.listener = listener
        let current = generation
        let startedAt = Date()
        downloadDone = false

        func report(_ block: @escaping () -> Void) {
            DispatchQueue.main.async {
                guard current == self.generation else { return }
                block()
            }
        }
        func elapsed() -> Int64 {
            Int64(Date().timeIntervalSince(startedAt) * 1000)
        }

        task = Task {
            do {
                var (bytes, response) = try await self.send(apiKey: apiKey, bodyJson: bodyJson, format: "pcm")
                var status = (response as? HTTPURLResponse)?.statusCode ?? 0
                if status == 400 {
                    // Noen modeller lager bare mp3. Prøv det før feilen vises.
                    let firstError = try await Self.collect(bytes)
                    (bytes, response) = try await self.send(apiKey: apiKey, bodyJson: bodyJson, format: "mp3")
                    status = (response as? HTTPURLResponse)?.statusCode ?? 0
                    if status != 200 {
                        let data = try await Self.collect(bytes)
                        let message = OpenRouter.errorMessage(status: status, data: data.isEmpty ? firstError : data)
                        report { listener.onError(id: id, message: message) }
                        return
                    }
                } else if status != 200 {
                    let data = try await Self.collect(bytes)
                    report { listener.onError(id: id, message: OpenRouter.errorMessage(status: status, data: data)) }
                    return
                }

                let contentType = (response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Type") ?? ""
                if contentType.contains("mpeg") || contentType.contains("mp3") {
                    // En mp3 spilles av først når hele filen er her.
                    let data = try await Self.collect(bytes)
                    let millis = elapsed()
                    report {
                        self.clips[id] = Clip(data: data, sampleRate: 0, isMp3: true)
                        self.downloadDone = true
                        let seconds = (try? AVAudioPlayer(data: data))?.duration ?? 0
                        listener.onFirstAudio(id: id, millis: millis)
                        listener.onReceived(id: id, millis: millis, audioSeconds: seconds, bytes: Int32(data.count), format: "mp3")
                        self.playMp3(id: id, data: data, callAudio: callAudio)
                    }
                    return
                }

                let rate = sampleRate > 0 ? Double(sampleRate) : (Self.rate(from: contentType) ?? 24_000)
                report {
                    do {
                        try self.startEngine(rate: rate, callAudio: callAudio)
                        self.playingId = id
                        listener.onPlaying(id: id, playing: true)
                    } catch {
                        listener.onError(id: id, message: "Kunne ikke spille av: \(error.localizedDescription)")
                    }
                }

                // Omtrent 0,1 sekund lyd om gangen.
                let chunkBytes = Int(rate) / 10 * 2
                var chunk = Data()
                var all = Data()
                var first = true
                for try await byte in bytes {
                    chunk.append(byte)
                    if chunk.count >= chunkBytes {
                        let piece = chunk
                        chunk = Data()
                        all.append(piece)
                        if first {
                            first = false
                            let millis = elapsed()
                            report { listener.onFirstAudio(id: id, millis: millis) }
                        }
                        report { self.schedule(piece, rate: rate) }
                    }
                }
                // Det siste, uten en halv sample til slutt.
                if chunk.count % 2 == 1 { chunk.removeLast() }
                if !chunk.isEmpty {
                    all.append(chunk)
                    let piece = chunk
                    report { self.schedule(piece, rate: rate) }
                }
                let millis = elapsed()
                let total = all
                report {
                    if first { listener.onFirstAudio(id: id, millis: millis) }
                    self.clips[id] = Clip(data: total, sampleRate: rate, isMp3: false)
                    self.downloadDone = true
                    listener.onReceived(
                        id: id,
                        millis: millis,
                        audioSeconds: Double(total.count / 2) / rate,
                        bytes: Int32(total.count),
                        format: "pcm \(Self.formatRate(rate))"
                    )
                    self.finishIfDone()
                }
            } catch {
                guard !Task.isCancelled else { return }
                report { listener.onError(id: id, message: "Fikk ikke kontakt med OpenRouter: \(error.localizedDescription)") }
            }
        }
    }

    func replay(id: String, callAudio: Bool, listener: TtsListener) {
        stop()
        self.listener = listener
        guard let clip = clips[id] else {
            listener.onError(id: id, message: "Lyden er ikke tatt vare på.")
            return
        }
        if clip.isMp3 {
            playMp3(id: id, data: clip.data, callAudio: callAudio)
            return
        }
        do {
            try startEngine(rate: clip.sampleRate, callAudio: callAudio)
        } catch {
            listener.onError(id: id, message: "Kunne ikke spille av: \(error.localizedDescription)")
            return
        }
        playingId = id
        listener.onPlaying(id: id, playing: true)
        schedule(clip.data, rate: clip.sampleRate)
        finishIfDone()
    }

    func stop() {
        generation += 1
        task?.cancel()
        task = nil
        mp3Watch?.invalidate()
        mp3Watch = nil
        mp3Player?.stop()
        mp3Player = nil
        player.stop()
        pendingBuffers = 0
        downloadDone = true
        if let id = playingId {
            playingId = nil
            listener?.onPlaying(id: id, playing: false)
        }
    }

    func close() {
        stop()
        engine.stop()
        connectedRate = 0
        clips = [:]
        listener = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    // MARK: - Nettverk

    private func send(apiKey: String, bodyJson: String, format: String) async throws -> (URLSession.AsyncBytes, URLResponse) {
        var body = (try? JSONSerialization.jsonObject(with: Data(bodyJson.utf8)) as? [String: Any]) ?? [:]
        body["response_format"] = format
        var request = URLRequest(url: URL(string: "audio/speech", relativeTo: OpenRouter.baseUrl)!)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        return try await URLSession.shared.bytes(for: request)
    }

    private static func collect(_ bytes: URLSession.AsyncBytes) async throws -> Data {
        var data = Data()
        for try await byte in bytes {
            data.append(byte)
        }
        return data
    }

    /// Samplingsfrekvensen fra f.eks. `audio/pcm;rate=24000`, hvis den står der.
    private static func rate(from contentType: String) -> Double? {
        for part in contentType.split(separator: ";") {
            let pair = part.split(separator: "=").map { $0.trimmingCharacters(in: .whitespaces).lowercased() }
            if pair.count == 2, ["rate", "sample_rate", "samplerate"].contains(pair[0]), let value = Double(pair[1]) {
                return value
            }
        }
        return nil
    }

    private static func formatRate(_ rate: Double) -> String {
        rate.truncatingRemainder(dividingBy: 1000) == 0
            ? "\(Int(rate / 1000)) kHz"
            : String(format: "%.2f kHz", rate / 1000).replacingOccurrences(of: ".", with: ",")
    }

    // MARK: - Avspilling

    private func configureSession(callAudio: Bool) throws {
        let session = AVAudioSession.sharedInstance()
        if callAudio {
            // Som i samtalene, så man hører det samme som Kompis vil gi.
            try session.setCategory(
                .playAndRecord,
                mode: .voiceChat,
                options: [.allowBluetoothHFP, .allowBluetoothA2DP, .defaultToSpeaker]
            )
        } else {
            try session.setCategory(.playback, mode: .spokenAudio)
        }
        try session.setActive(true)
    }

    private func startEngine(rate: Double, callAudio: Bool) throws {
        try configureSession(callAudio: callAudio)
        if player.engine == nil {
            engine.attach(player)
        }
        if rate != connectedRate {
            engine.stop()
            let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: rate, channels: 1, interleaved: false)!
            engine.connect(player, to: engine.mainMixerNode, format: format)
            connectedRate = rate
        }
        if !engine.isRunning {
            engine.prepare()
            try engine.start()
        }
        player.play()
    }

    /// Spiller av rå lyd: 16-bit little endian mono.
    private func schedule(_ data: Data, rate: Double) {
        let frames = data.count / 2
        guard frames > 0,
              let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: rate, channels: 1, interleaved: false),
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frames)),
              let out = buffer.floatChannelData?[0] else { return }
        buffer.frameLength = AVAudioFrameCount(frames)
        data.withUnsafeBytes { raw in
            for i in 0..<frames {
                let sample = Int16(littleEndian: raw.loadUnaligned(fromByteOffset: i * 2, as: Int16.self))
                out[i] = Float(sample) / 32768
            }
        }
        pendingBuffers += 1
        let current = generation
        player.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self, current == self.generation else { return }
                self.pendingBuffers -= 1
                self.finishIfDone()
            }
        }
    }

    /// Sier fra når alt er lastet ned og spilt av.
    private func finishIfDone() {
        guard downloadDone, pendingBuffers <= 0, let id = playingId else { return }
        playingId = nil
        listener?.onPlaying(id: id, playing: false)
    }

    private func playMp3(id: String, data: Data, callAudio: Bool) {
        do {
            try configureSession(callAudio: callAudio)
            let mp3 = try AVAudioPlayer(data: data)
            mp3Player = mp3
            mp3.play()
        } catch {
            listener?.onError(id: id, message: "Kunne ikke spille av mp3: \(error.localizedDescription)")
            return
        }
        playingId = id
        listener?.onPlaying(id: id, playing: true)
        // AVAudioPlayer sier ikke fra uten en delegat, så sjekk med jevne mellomrom.
        mp3Watch = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] timer in
            guard let self else { return timer.invalidate() }
            if self.mp3Player?.isPlaying != true {
                timer.invalidate()
                self.mp3Watch = nil
                self.finishIfDone()
            }
        }
    }
}
