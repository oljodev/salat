import AVFoundation
import Foundation
import Shared

/// Leser opp setningene til Kompis i en samtale gjennom OpenRouter. Hver setning hentes med en gang
/// den er skrevet, mens den forrige ennå spilles, og spilles av rett etter den forrige uten pause.
///
/// Lyden spilles av gjennom lydmotoren til [SttRecorder], så mikrofonens ekkodemping hører stemmen
/// og Kompis ikke hører seg selv. Alt her skjer på hovedtråden.
final class SpeechQueue {
    static let shared = SpeechQueue()

    private final class Item {
        let id: String
        /// Samplingsfrekvensen til lyden som kommer.
        var rate: Double = 24_000
        /// Lyd som er lastet ned, men ikke lagt i avspilleren ennå.
        var pending = Data()
        var downloadDone = false
        /// Alt er lagt i avspilleren, med en markør til slutt.
        var fullyScheduled = false
        var scheduledAny = false
        var started = false
        var firstAudioReported = false
        var converter: AVAudioConverter?
        var task: Task<Void, Never>?

        init(id: String) {
            self.id = id
        }
    }

    private var listener: SpeechListener?
    private var configuredRate = 0
    private var items: [Item] = []
    /// Øker hver gang køen stoppes, så lyd og hendelser fra det som ble stoppet ikke slipper gjennom.
    private var generation = 0
    private var pendingBuffers = 0
    private var closed = false
    private var reportedFinished = false
    private var lastLevelUpdate = Date.distantPast

    // MARK: - Fra Kotlin

    func start(sampleRate: Int, listener: SpeechListener) {
        stop()
        self.listener = listener
        configuredRate = sampleRate
    }

    func enqueue(id: String, apiKey: String, bodyJson: String) {
        let item = Item(id: id)
        items.append(item)
        reportedFinished = false
        let current = generation
        let configuredRate = self.configuredRate
        let startedAt = Date()

        func report(_ block: @escaping () -> Void) {
            DispatchQueue.main.async {
                guard current == self.generation else { return }
                block()
            }
        }
        func elapsed() -> Int64 {
            Int64(Date().timeIntervalSince(startedAt) * 1000)
        }

        item.task = Task {
            do {
                var body = (try? JSONSerialization.jsonObject(with: Data(bodyJson.utf8)) as? [String: Any]) ?? [:]
                body["response_format"] = "pcm"
                var request = URLRequest(url: URL(string: "audio/speech", relativeTo: OpenRouter.baseUrl)!)
                request.httpMethod = "POST"
                request.timeoutInterval = 60
                request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.httpBody = try JSONSerialization.data(withJSONObject: body)

                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                let http = response as? HTTPURLResponse
                let status = http?.statusCode ?? 0
                guard status == 200 else {
                    var data = Data()
                    for try await byte in bytes {
                        data.append(byte)
                    }
                    let message = OpenRouter.errorMessage(status: status, data: data)
                    report { self.fail(message) }
                    return
                }
                let contentType = http?.value(forHTTPHeaderField: "Content-Type") ?? ""
                if contentType.contains("mpeg") || contentType.contains("mp3") {
                    report { self.fail("Stemmen gir bare mp3, og samtaler trenger rå lyd. Velg en annen modell.") }
                    return
                }
                let rate = configuredRate > 0 ? Double(configuredRate) : (Self.rate(from: contentType) ?? 24_000)

                // Den første biten er liten, så lyden kan begynne så tidlig som mulig. Resten kommer i
                // biter på omtrent 0,1 sekund. Alltid et partall antall byte, siden hver sample er to.
                let firstBytes = Int(rate) / 25 * 2
                let chunkBytes = Int(rate) / 10 * 2
                var chunk = Data()
                var isFirst = true
                for try await byte in bytes {
                    chunk.append(byte)
                    if chunk.count >= (isFirst ? firstBytes : chunkBytes) {
                        let piece = chunk
                        chunk = Data()
                        let millis: Int64? = isFirst ? elapsed() : nil
                        isFirst = false
                        report { self.receive(item, piece, rate: rate, firstMillis: millis) }
                    }
                }
                if chunk.count % 2 == 1 { chunk.removeLast() }
                let tail = chunk
                let tailMillis: Int64? = isFirst ? elapsed() : nil
                report {
                    if !tail.isEmpty || tailMillis != nil {
                        self.receive(item, tail, rate: rate, firstMillis: tailMillis)
                    }
                    self.complete(item)
                }
            } catch {
                guard !Task.isCancelled else { return }
                report { self.fail("Fikk ikke kontakt med OpenRouter: \(error.localizedDescription)") }
            }
        }
    }

    /// Det kommer ikke flere setninger. Sier fra når alt er spilt.
    func finish() {
        closed = true
        pump()
    }

    /// Stopper avspillingen og nedlastingene, uten å si fra til Kotlin.
    func stop() {
        generation += 1
        for item in items {
            item.task?.cancel()
        }
        items = []
        pendingBuffers = 0
        closed = false
        reportedFinished = false
        let recorder = SttRecorder.shared
        guard recorder.player.engine != nil else { return }
        recorder.player.stop()
        // Avspilleren må settes i gang igjen, ellers blir neste setning stum.
        if recorder.engine.isRunning {
            recorder.player.play()
        }
    }

    // MARK: - Lyd som kommer

    private func receive(_ item: Item, _ data: Data, rate: Double, firstMillis: Int64?) {
        item.rate = rate
        if let millis = firstMillis, !item.firstAudioReported {
            item.firstAudioReported = true
            listener?.onFirstAudio(id: item.id, millis: millis)
        }
        item.pending.append(data)
        pump()
    }

    private func complete(_ item: Item) {
        item.downloadDone = true
        pump()
    }

    private func fail(_ message: String) {
        let listener = self.listener
        stop()
        listener?.onError(message: message)
    }

    // MARK: - Avspilling

    /// Legger lyden i avspilleren, én setning om gangen i riktig rekkefølge. Lyden til en setning
    /// som kommer før den forrige er ferdig, venter i `pending`.
    private func pump() {
        while let item = items.first(where: { !$0.fullyScheduled }) {
            if !item.pending.isEmpty {
                let data = item.pending
                item.pending = Data()
                guard schedule(item, data) else { return }
            }
            // Mer lyd kommer til denne setningen, så de neste må vente.
            guard item.downloadDone else { return }
            scheduleMarker(item)
            item.fullyScheduled = true
        }
        checkDrained()
    }

    /// Legger lyden i avspilleren. Gir `false` hvis det ikke går, og da er køen stoppet.
    private func schedule(_ item: Item, _ data: Data) -> Bool {
        let recorder = SttRecorder.shared
        guard recorder.engine.isRunning, recorder.player.engine != nil else {
            fail("Lyden er ikke i gang. Avslutt samtalen og start den på nytt.")
            return false
        }
        let target = recorder.playbackFormat
        let frames = data.count / 2
        guard frames > 0,
              let sourceFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: item.rate, channels: 1, interleaved: false),
              let source = AVAudioPCMBuffer(pcmFormat: sourceFormat, frameCapacity: AVAudioFrameCount(frames)),
              let samples = source.floatChannelData?[0] else { return true }
        source.frameLength = AVAudioFrameCount(frames)
        data.withUnsafeBytes { raw in
            for i in 0..<frames {
                let sample = Int16(littleEndian: raw.loadUnaligned(fromByteOffset: i * 2, as: Int16.self))
                samples[i] = Float(sample) / 32768
            }
        }

        var buffer = source
        if item.rate != target.sampleRate {
            // Motoren spiller av med én frekvens, så andre frekvenser gjøres om.
            if item.converter == nil {
                item.converter = AVAudioConverter(from: sourceFormat, to: target)
            }
            let capacity = AVAudioFrameCount(Double(frames) * target.sampleRate / item.rate) + 64
            guard let converter = item.converter,
                  let converted = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return true }
            var consumed = false
            var error: NSError?
            converter.convert(to: converted, error: &error) { _, status in
                if consumed {
                    status.pointee = .noDataNow
                    return nil
                }
                consumed = true
                status.pointee = .haveData
                return source
            }
            guard error == nil, converted.frameLength > 0 else { return true }
            buffer = converted
        }

        reportLevel(buffer)
        item.scheduledAny = true
        pendingBuffers += 1
        // Spilles med en gang hvis ingenting annet spilles.
        let startsNow = !item.started && pendingBuffers == 1
        if startsNow { item.started = true }
        let current = generation
        recorder.player.scheduleBuffer(buffer, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self, current == self.generation else { return }
                self.pendingBuffers -= 1
                self.checkDrained()
            }
        }
        if startsNow { listener?.onItemStarted(id: item.id) }
        return true
    }

    /// En liten stum bit etter hver setning. Når den er spilt, er setningen ferdig, og neste begynner.
    private func scheduleMarker(_ item: Item) {
        let recorder = SttRecorder.shared
        guard recorder.engine.isRunning, recorder.player.engine != nil else {
            fail("Lyden er ikke i gang. Avslutt samtalen og start den på nytt.")
            return
        }
        guard let marker = AVAudioPCMBuffer(pcmFormat: recorder.playbackFormat, frameCapacity: 1) else { return }
        marker.frameLength = 1
        marker.floatChannelData?[0][0] = 0
        pendingBuffers += 1
        let current = generation
        recorder.player.scheduleBuffer(marker, completionCallbackType: .dataPlayedBack) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self, current == self.generation else { return }
                self.pendingBuffers -= 1
                self.finished(item)
            }
        }
    }

    private func finished(_ item: Item) {
        // En setning uten lyd regnes som spilt, men begynner først.
        if !item.started {
            item.started = true
            listener?.onItemStarted(id: item.id)
        }
        listener?.onItemFinished(id: item.id)
        // Neste setning spilles allerede, hvis lyden er lagt i avspilleren.
        if let next = items.first(where: { !$0.started && $0.scheduledAny }) {
            next.started = true
            listener?.onItemStarted(id: next.id)
        }
        checkDrained()
    }

    private func checkDrained() {
        guard closed, !reportedFinished, pendingBuffers == 0, !items.contains(where: { !$0.fullyScheduled }) else { return }
        reportedFinished = true
        listener?.onFinished()
    }

    private func reportLevel(_ buffer: AVAudioPCMBuffer) {
        let now = Date()
        guard now.timeIntervalSince(lastLevelUpdate) > 0.06,
              let samples = buffer.floatChannelData?[0], buffer.frameLength > 0 else { return }
        lastLevelUpdate = now
        var sum: Float = 0
        for i in 0..<Int(buffer.frameLength) {
            sum += samples[i] * samples[i]
        }
        let rms = (sum / Float(buffer.frameLength)).squareRoot()
        // Samme skala som i LiveSession.
        listener?.onLevel(level: min(1, sqrt(rms) * 2.2))
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
}
