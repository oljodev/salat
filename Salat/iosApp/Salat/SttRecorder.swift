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

    private static let transcriptionsUrl = URL(string: "https://openrouter.ai/api/v1/audio/transcriptions")!
    /// Et lite kall som bare åpner forbindelsen til OpenRouter, så opptaket ikke må vente på det.
    private static let warmUpUrl = URL(string: "https://openrouter.ai/api/v1/key")!

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
    private var lastLevelUpdate = Date.distantPast

    // MARK: - Start og stopp

    func open(listener: SttListener) {
        close()
        guard !LiveSession.shared.isRunning else {
            listener.onError(message: "Avslutt samtalen med Kompis først.")
            return
        }
        self.listener = listener
        isOpen = true
        warmUp()

        Task { @MainActor in
            guard await AVAudioApplication.requestRecordPermission() else {
                self.listener?.onError(message: "Kompis trenger tilgang til mikrofonen. Slå det på i Innstillinger → Apper → Salat.")
                return
            }
            guard self.isOpen else { return }
            do {
                try self.startAudio()
            } catch {
                self.listener?.onError(message: "Kunne ikke starte mikrofonen: \(error.localizedDescription)")
            }
        }
    }

    func close() {
        guard isOpen else { return }
        isOpen = false
        listener = nil
        engine.inputNode.removeTap(onBus: 0)
        player.stop()
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        lock.lock()
        isRecording = false
        recorded = Data()
        preRoll = []
        preRollBytes = 0
        lock.unlock()
    }

    func startRecording() {
        warmUp()
        lock.lock()
        recorded = preRoll.reduce(into: Data()) { $0.append($1) }
        preRoll = []
        preRollBytes = 0
        isRecording = true
        lock.unlock()
    }

    func stopRecording(configJson: String) {
        let stoppedAt = Date()
        lock.lock()
        isRecording = false
        let pcm = recorded
        recorded = Data()
        lock.unlock()

        guard let config = try? JSONDecoder().decode(Config.self, from: Data(configJson.utf8)) else {
            listener?.onError(message: "Kunne ikke lese innstillingene for testen.")
            return
        }
        let seconds = Double(pcm.count / 2) / recordFormat.sampleRate
        guard seconds >= 0.5 else {
            listener?.onError(message: "Opptaket var for kort. Hold inne knappen mens du snakker.")
            return
        }

        var body: [String: Any] = [
            "model": config.model,
            "input_audio": ["data": Self.wav(pcm: pcm, sampleRate: 16_000).base64EncodedString(), "format": "wav"],
        ]
        if let language = config.language {
            body["language"] = language
        }
        var request = URLRequest(url: Self.transcriptionsUrl)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
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
                    listener?.onError(message: Self.errorMessage(status: status, data: data))
                }
            }
        }.resume()
    }

    /// Åpner forbindelsen til OpenRouter, så sendingen ikke må vente på at den kobles opp. Svaret
    /// er uinteressant; det viktige er at forbindelsen er klar til den gjenbrukes.
    private func warmUp() {
        var request = URLRequest(url: Self.warmUpUrl)
        request.timeoutInterval = 10
        URLSession.shared.dataTask(with: request) { _, _, _ in }.resume()
    }

    private static func errorMessage(status: Int, data: Data?) -> String {
        let message = data
            .flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] }
            .flatMap { $0["error"] as? [String: Any] }
            .flatMap { $0["message"] as? String }
        switch status {
        case 401: return "OpenRouter godtok ikke nøkkelen. Sjekk den i innstillingene."
        case 402: return "Det er ikke nok kreditt på OpenRouter-kontoen."
        default: return "OpenRouter svarte med feil \(status)" + (message.map { ": \($0)" } ?? ".")
        }
    }

    // MARK: - Lyd

    private func startAudio() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(
            .playAndRecord,
            mode: .voiceChat,
            options: [.allowBluetoothHFP, .allowBluetoothA2DP, .defaultToSpeaker]
        )
        try session.setActive(true)

        let input = engine.inputNode
        // Samme ekkodemping som i samtalene, så testen hører det samme som Kompis vil høre.
        try input.setVoiceProcessingEnabled(true)

        if player.engine == nil {
            engine.attach(player)
        }
        engine.connect(player, to: engine.mainMixerNode, format: playbackFormat)

        let inputFormat = input.outputFormat(forBus: 0)
        converter = AVAudioConverter(from: inputFormat, to: recordFormat)
        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 2048, format: inputFormat) { [weak self] buffer, _ in
            self?.handleMicrophone(buffer)
        }

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
