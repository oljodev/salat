import AVFoundation
import Foundation
import Shared

/// Testen av tale til tekst i Kompis: tar opp mens knappen holdes inne, og sender opptaket til
/// OpenRouter. Mikrofonen står på så lenge testskjermen er åpen, så opptaket begynner med en gang.
final class SttRecorder {
    static let shared = SttRecorder()

    private struct Config: Decodable {
        let apiKey: String
        let model: String
        let language: String?
    }

    private var listener: SttListener?
    private var isOpen = false

    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let playbackFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 24_000, channels: 1, interleaved: false)!
    /// Formatet som sendes: 16 kHz, 16-bit, mono. Nok for tale, og lite å laste opp.
    private let recordFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16_000, channels: 1, interleaved: true)!
    private var converter: AVAudioConverter?

    // Brukes fra lydtråden, og beskyttes av lock.
    private let lock = NSLock()
    private var isRecording = false
    private var recorded = Data()
    /// Lyden rett før knappen ble trykket, så første stavelse ikke forsvinner.
    private var preRoll: [Data] = []
    private var preRollBytes = 0
    /// Omtrent 0,3 sekunder.
    private static let maxPreRollBytes = 16_000 * 2 * 3 / 10
    /// Hvor mange lydbiter mikrofonen har gitt, for å se om den virker.
    private var micBuffers = 0
    private var lastLevelUpdate = Date.distantPast

    // Diagnose, så feilmeldingene kan si hva som faktisk skjedde.
    private var recordingStartedAt: Date?
    /// Om ekkodempingen er på. Den slås av hvis mikrofonen ikke virker med den.
    private var voiceProcessing = true
    private var startError: String?

    // MARK: - Start og stopp

    func open(listener: SttListener) {
        close()
        guard !LiveSession.shared.isRunning else {
            listener.onMicrophoneError(message: "Avslutt samtalen med Kompis først.")
            return
        }
        self.listener = listener
        isOpen = true
        voiceProcessing = true
        startError = nil
        OpenRouter.warmUp()

        Task { @MainActor in
            guard await AVAudioApplication.requestRecordPermission() else {
                self.startError = "Kompis har ikke tilgang til mikrofonen. Slå det på i Innstillinger → Apper → Salat."
                self.listener?.onMicrophoneError(message: self.startError!)
                return
            }
            guard self.isOpen else { return }
            self.startMicrophone()
        }
    }

    func close() {
        guard isOpen else { return }
        isOpen = false
        listener = nil
        stopAudio()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        NotificationCenter.default.removeObserver(self)
        lock.lock()
        isRecording = false
        recorded = Data()
        preRoll = []
        preRollBytes = 0
        lock.unlock()
    }

    func startRecording() {
        OpenRouter.warmUp()
        recordingStartedAt = Date()
        lock.lock()
        let buffers = micBuffers
        recorded = preRoll.reduce(into: Data()) { $0.append($1) }
        preRoll = []
        preRollBytes = 0
        isRecording = true
        lock.unlock()
        // Har mikrofonen aldri gitt lyd, prøv å starte den på nytt.
        if buffers == 0, isOpen {
            startMicrophone()
        }
    }

    func stopRecording(configJson: String) {
        let stoppedAt = Date()
        let heldSeconds = recordingStartedAt.map { stoppedAt.timeIntervalSince($0) } ?? 0
        recordingStartedAt = nil
        lock.lock()
        isRecording = false
        let pcm = recorded
        let buffers = micBuffers
        recorded = Data()
        lock.unlock()

        guard let config = try? JSONDecoder().decode(Config.self, from: Data(configJson.utf8)) else {
            listener?.onError(message: "Kunne ikke lese innstillingene for testen.")
            return
        }
        let seconds = Double(pcm.count / 2) / recordFormat.sampleRate
        guard seconds >= 0.5 else {
            listener?.onError(message: Self.tooShortMessage(seconds: seconds, heldSeconds: heldSeconds, micBuffers: buffers, startError: startError))
            return
        }

        var body: [String: Any] = [
            "model": config.model,
            "input_audio": ["data": Self.wav(pcm: pcm, sampleRate: 16_000).base64EncodedString(), "format": "wav"],
        ]
        if let language = config.language {
            body["language"] = language
        }
        var request = URLRequest(url: URL(string: "audio/transcriptions", relativeTo: OpenRouter.baseUrl)!)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("Bearer \(config.apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        let uploadBytes = request.httpBody?.count ?? 0

        // Svaret skal fram selv om testskjermen lukkes i mellomtiden.
        let listener = self.listener
        URLSession.shared.dataTask(with: request) { data, response, error in
            let millis = Int64(Date().timeIntervalSince(stoppedAt) * 1000)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            DispatchQueue.main.async {
                if let error {
                    listener?.onError(message: "Fikk ikke kontakt med OpenRouter: \(error.localizedDescription)")
                } else if status == 200, let data {
                    listener?.onTranscribed(
                        responseJson: String(decoding: data, as: UTF8.self),
                        audioSeconds: seconds,
                        uploadBytes: Int32(uploadBytes),
                        millis: millis
                    )
                } else {
                    listener?.onError(message: OpenRouter.errorMessage(status: status, data: data))
                }
            }
        }.resume()
    }

    /// Forklarer hvorfor det nesten ikke ble noe lyd: knappen, eller mikrofonen.
    private static func tooShortMessage(seconds: Double, heldSeconds: Double, micBuffers: Int, startError: String?) -> String {
        if micBuffers == 0 {
            return "Mikrofonen har ikke gitt noe lyd" + (startError.map { ": \($0)" } ?? ". Gå ut av testen og inn igjen.")
        }
        if heldSeconds < 0.5 {
            return "Opptaket var for kort. Hold inne knappen mens du snakker, eller trykk én gang for å starte og én gang til for å sende."
        }
        return String(
            format: "Fikk bare %.1f s lyd på %.1f s. Mikrofonen stoppet underveis, prøv igjen.",
            seconds,
            heldSeconds
        )
    }

    // MARK: - Lyd

    /// Starter mikrofonen med ekkodemping, og uten hvis det ikke går. Sjekker etterpå at lyden kommer.
    private func startMicrophone() {
        stopAudio()
        do {
            try startAudio()
        } catch where voiceProcessing {
            voiceProcessing = false
            stopAudio()
            do {
                try startAudio()
            } catch {
                microphoneFailed("Kunne ikke starte mikrofonen: \(error.localizedDescription)")
                return
            }
        } catch {
            microphoneFailed("Kunne ikke starte mikrofonen: \(error.localizedDescription)")
            return
        }
        reportMicrophone()
        checkMicrophone(after: 1.5)
    }

    private func microphoneFailed(_ message: String) {
        startError = message
        listener?.onMicrophoneError(message: message)
    }

    /// Gir ikke mikrofonen lyd, prøves den uten ekkodemping, og så sies det fra.
    private func checkMicrophone(after delay: TimeInterval) {
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self, self.isOpen else { return }
            self.lock.lock()
            let buffers = self.micBuffers
            self.lock.unlock()
            guard buffers == 0 else { return }
            if self.voiceProcessing {
                self.voiceProcessing = false
                self.startMicrophone()
            } else {
                self.microphoneFailed("Mikrofonen gir ingen lyd.")
            }
        }
    }

    private func startAudio() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: voiceProcessing ? .voiceChat : .default,
            options: [.allowBluetoothHFP, .allowBluetoothA2DP, .defaultToSpeaker]
        )
        try session.setActive(true)

        let input = engine.inputNode
        // Samme ekkodemping som i samtalene, så testen hører det samme som Kompis vil høre.
        try input.setVoiceProcessingEnabled(voiceProcessing)

        if player.engine == nil {
            engine.attach(player)
        }
        engine.connect(player, to: engine.mainMixerNode, format: playbackFormat)

        let inputFormat = input.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
            throw NSError(domain: "SttRecorder", code: 1, userInfo: [NSLocalizedDescriptionKey: "Fant ingen mikrofon."])
        }
        converter = AVAudioConverter(from: inputFormat, to: recordFormat)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 2048, format: inputFormat) { [weak self] buffer, _ in
            self?.handleMicrophone(buffer)
        }
        lock.lock()
        micBuffers = 0
        lock.unlock()

        engine.prepare()
        try engine.start()
        player.play()
        // Med ekkodemping gir mikrofonen av og til bare stillhet til noe har vært spilt av, som i
        // LiveSession. Et lite øyeblikk med stillhet får lyden i gang.
        if let silence = AVAudioPCMBuffer(pcmFormat: playbackFormat, frameCapacity: 2400) {
            silence.frameLength = 2400
            silence.floatChannelData?[0].update(repeating: 0, count: 2400)
            player.scheduleBuffer(silence)
        }

        NotificationCenter.default.removeObserver(self)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(routeChanged),
            name: AVAudioSession.routeChangeNotification,
            object: nil
        )
    }

    private func stopAudio() {
        engine.inputNode.removeTap(onBus: 0)
        player.stop()
        engine.stop()
    }

    @objc private func routeChanged() {
        DispatchQueue.main.async {
            guard self.isOpen else { return }
            self.reportMicrophone()
        }
    }

    private func reportMicrophone() {
        let input = AVAudioSession.sharedInstance().currentRoute.inputs.first
        var name = input?.portType == .builtInMic ? "iPhone-mikrofon" : (input?.portName ?? "Mikrofon")
        if !voiceProcessing {
            name += ", uten ekkodemping"
        }
        listener?.onMicrophone(name: name)
    }

    private func handleMicrophone(_ buffer: AVAudioPCMBuffer) {
        guard let converter else { return }
        let ratio = recordFormat.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 32
        guard let converted = AVAudioPCMBuffer(pcmFormat: recordFormat, frameCapacity: capacity) else { return }
        var consumed = false
        var error: NSError?
        converter.convert(to: converted, error: &error) { _, status in
            if consumed {
                status.pointee = .noDataNow
                return nil
            }
            consumed = true
            status.pointee = .haveData
            return buffer
        }
        guard error == nil, converted.frameLength > 0, let samples = converted.int16ChannelData else { return }

        let frames = Int(converted.frameLength)
        let data = Data(bytes: samples[0], count: frames * 2)
        lock.lock()
        micBuffers += 1
        if isRecording {
            recorded.append(data)
        } else {
            preRoll.append(data)
            preRollBytes += data.count
            while preRollBytes > Self.maxPreRollBytes, !preRoll.isEmpty {
                preRollBytes -= preRoll.removeFirst().count
            }
        }
        lock.unlock()
        reportLevel(rms(samples[0], count: frames))
    }

    private func reportLevel(_ level: Float) {
        let now = Date()
        guard now.timeIntervalSince(lastLevelUpdate) > 0.06 else { return }
        lastLevelUpdate = now
        // Samme skala som i LiveSession.
        let scaled = min(1, sqrt(level) * 2.2)
        DispatchQueue.main.async { self.listener?.onLevel(level: scaled) }
    }

    private func rms(_ samples: UnsafePointer<Int16>, count: Int) -> Float {
        guard count > 0 else { return 0 }
        var sum: Float = 0
        for i in 0..<count {
            let v = Float(samples[i]) / 32768
            sum += v * v
        }
        return (sum / Float(count)).squareRoot()
    }

    /// Rå lyd med et WAV-hode foran, så OpenRouter vet hva det er.
    private static func wav(pcm: Data, sampleRate: Int) -> Data {
        var data = Data()
        func append<T: FixedWidthInteger>(_ value: T) {
            withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) }
        }
        data.append(contentsOf: Array("RIFF".utf8))
        append(UInt32(36 + pcm.count))
        data.append(contentsOf: Array("WAVEfmt ".utf8))
        append(UInt32(16)) // Størrelsen på fmt-delen
        append(UInt16(1)) // PCM
        append(UInt16(1)) // Mono
        append(UInt32(sampleRate))
        append(UInt32(sampleRate * 2)) // Byte per sekund
        append(UInt16(2)) // Byte per sample
        append(UInt16(16)) // Bit per sample
        data.append(contentsOf: Array("data".utf8))
        append(UInt32(pcm.count))
        data.append(pcm)
        return data
    }
}
