import AVFoundation
import PhotosUI
import Shared
import UIKit
import UniformTypeIdentifiers

/// Henter lyden til en ny lyd i Ekko, fra YouTube, en nettside eller en fil, og lagrer den som `.m4a`.
final class EkkoImport: NSObject {
    static let shared = EkkoImport()

    private var task: Task<Void, Never>?
    /// Øker for hver henting, så en gammel henting ikke kan rapportere til en ny.
    private var generation = 0
    /// Lyden som skal hentes fra en fil, mens brukeren velger filen.
    private var picking: (clipId: String, listener: EkkoImportListener)?

    func youTube(clipId: String, videoId: String, listener: EkkoImportListener) {
        start(clipId: clipId, listener: listener) { progress in
            let stream = try await EkkoYouTube.audioStream(videoId: videoId)
            let file = try await Self.download(stream.url, fileExtension: stream.fileExtension, progress: progress)
            return (file, stream.title, "")
        }
    }

    /// Videoen i en post på X. [apiUrl] er den åpne adressen innebygde poster bruker (se `EkkoLink.X` i Kotlin).
    func x(clipId: String, apiUrl: String, listener: EkkoImportListener) {
        start(clipId: clipId, listener: listener) { progress in
            let post = try await Self.xVideo(apiUrl: apiUrl)
            let file = try await Self.download(post.url, fileExtension: "mp4", progress: progress)
            return (file, post.title, post.artist)
        }
    }

    func web(clipId: String, url: String, listener: EkkoImportListener) {
        start(clipId: clipId, listener: listener) { progress in
            guard let pageURL = URL(string: url) else { throw EkkoError("Lenken ser ikke riktig ut.") }
            let ext = pageURL.pathExtension.lowercased()
            if ["mp4", "m4a", "mov", "mp3", "aac", "wav"].contains(ext) {
                let file = try await Self.download(pageURL, fileExtension: ext, progress: progress)
                return (file, pageURL.deletingPathExtension().lastPathComponent, "")
            }
            let (videoURL, title) = try await Self.findVideo(onPage: pageURL)
            let file = try await Self.download(videoURL, fileExtension: "mp4", progress: progress)
            return (file, title, "")
        }
    }

    func file(clipId: String, fromPhotos: Bool, listener: EkkoImportListener) {
        cancel()
        picking = (clipId, listener)
        let picker: UIViewController
        if fromPhotos {
            var config = PHPickerConfiguration()
            config.filter = .videos
            config.selectionLimit = 1
            config.preferredAssetRepresentationMode = .current
            let photos = PHPickerViewController(configuration: config)
            photos.delegate = self
            photos.isModalInPresentation = true
            picker = photos
        } else {
            let files = UIDocumentPickerViewController(forOpeningContentTypes: [.audiovisualContent, .audio, .movie], asCopy: true)
            files.delegate = self
            picker = files
        }
        guard let presenter = Self.topViewController() else {
            picking = nil
            listener.onFailed(message: "Klarte ikke å åpne velgeren.")
            return
        }
        presenter.present(picker, animated: true)
    }

    /// Avbryter hentingen som pågår. Listeneren får ikke flere kall.
    func cancel() {
        generation += 1
        task?.cancel()
        task = nil
    }

    private func start(
        clipId: String,
        listener: EkkoImportListener,
        fetch: @escaping (_ progress: @escaping (Double) -> Void) async throws -> (URL, String, String)
    ) {
        cancel()
        let current = generation
        let deliver: (@escaping () -> Void) -> Void = { [weak self] call in
            DispatchQueue.main.async {
                guard let self, self.generation == current else { return }
                call()
            }
        }
        task = Task {
            var downloaded: URL?
            do {
                let (file, title, artist) = try await fetch { fraction in deliver { listener.onProgress(fraction: Float(fraction)) } }
                downloaded = file
                let result = try await Self.save(source: file, clipId: clipId, title: title, artist: artist)
                try Task.checkCancellation()
                deliver { listener.onImported(resultJson: result) }
            } catch {
                EkkoFiles.delete(clipId)
                if !(error is CancellationError) {
                    deliver { listener.onFailed(message: Self.describe(error)) }
                }
            }
            if let downloaded { try? FileManager.default.removeItem(at: downloaded) }
        }
    }

    // MARK: Nedlasting

    /// Laster ned [url] i biter, så man ser hvor langt det har kommet, og så YouTube ikke struper farten.
    private static func download(_ url: URL, fileExtension: String, progress: @escaping (Double) -> Void) async throws -> URL {
        let file = EkkoFiles.temporary(extension: fileExtension)
        FileManager.default.createFile(atPath: file.path, contents: nil)
        let handle = try FileHandle(forWritingTo: file)
        defer { try? handle.close() }

        let chunk = 4 * 1024 * 1024
        var offset = 0
        var total: Int?
        while true {
            try Task.checkCancellation()
            var request = URLRequest(url: url)
            request.setValue("bytes=\(offset)-\(offset + chunk - 1)", forHTTPHeaderField: "Range")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                throw EkkoError("Nedlastingen ble avvist (\(code)). Prøv igjen litt senere.")
            }
            try handle.write(contentsOf: data)
            offset += data.count
            // 200 betyr at serveren sendte hele filen på én gang.
            if http.statusCode == 200 { break }
            if total == nil, let range = http.value(forHTTPHeaderField: "Content-Range"),
               let size = range.split(separator: "/").last.flatMap({ Int($0) }) {
                total = size
            }
            if let total { progress(min(1, Double(offset) / Double(total))) }
            if data.count < chunk || offset >= (total ?? .max) { break }
        }
        progress(1)
        return file
    }

    /// Leter etter videoen på en nettside, f.eks. Snapchat Spotlight, og tittelen på den.
    private static func findVideo(onPage url: URL) async throws -> (URL, String) {
        var request = URLRequest(url: url)
        // Som Safari på iPhone, så siden ser ut som den gjør i nettleseren.
        request.setValue(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1",
            forHTTPHeaderField: "User-Agent"
        )
        let (data, _) = try await URLSession.shared.data(for: request)
        let html = String(decoding: data, as: UTF8.self)
        let patterns = [
            #"<meta[^>]+property=["']og:video(?::secure_url|:url)?["'][^>]*content=["']([^"']+)["']"#,
            #"<meta[^>]+content=["']([^"']+)["'][^>]*property=["']og:video(?::secure_url|:url)?["']"#,
            #""contentUrl"\s*:\s*"([^"]+)""#,
            #""(https?:(?:\\?/){2}[^"]+?\.mp4[^"]*)""#,
        ]
        for pattern in patterns {
            if let match = firstMatch(pattern, in: html), let videoURL = URL(string: unescape(match)) {
                let title = firstMatch(#"<meta[^>]+property=["']og:title["'][^>]*content=["']([^"']+)["']"#, in: html).map(unescape) ?? ""
                return (videoURL, title)
            }
        }
        throw EkkoError("Fant ingen video på siden. Lagre videoen på telefonen, og hent den fra Bilder i stedet.")
    }

    /// Henter posten som JSON og finner videoen. Den minste mp4-en holder, siden det bare er lyden som brukes.
    private static func xVideo(apiUrl: String) async throws -> (url: URL, title: String, artist: String) {
        guard let url = URL(string: apiUrl) else { throw EkkoError("Lenken ser ikke riktig ut.") }
        let (data, response) = try await URLSession.shared.data(from: url)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let notFound = EkkoError("Fant ikke posten. Den kan være slettet, eller kontoen kan være privat.")
        if status == 404 { throw notFound }
        guard status == 200, let post = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            throw EkkoError("Klarte ikke å hente posten fra X (\(status)). Prøv igjen litt senere.")
        }
        if (post["__typename"] as? String) == "TweetTombstone" { throw notFound }

        // Helst en vanlig video. En «GIF» på X er en video uten lyd, og gir feilen «Videoen har ingen lyd».
        let media = (post["mediaDetails"] as? [[String: Any]]) ?? []
        let videos = media.filter { ($0["type"] as? String) == "video" } + media.filter { ($0["type"] as? String) == "animated_gif" }
        let variants = videos.lazy.compactMap { ($0["video_info"] as? [String: Any])?["variants"] as? [[String: Any]] }.first ?? []
        let mp4s: [(bitrate: Int, url: URL)] = variants.compactMap { variant in
            guard (variant["content_type"] as? String) == "video/mp4",
                  let address = variant["url"] as? String, let videoURL = URL(string: address) else { return nil }
            return ((variant["bitrate"] as? Int) ?? 0, videoURL)
        }
        guard let smallest = mp4s.min(by: { $0.bitrate < $1.bitrate }) else {
            throw EkkoError("Fant ingen video i posten.")
        }

        let user = post["user"] as? [String: Any]
        let artist = (user?["name"] as? String) ?? (user?["screen_name"] as? String).map { "@\($0)" } ?? ""
        return (smallest.url, xTitle(post["text"] as? String ?? ""), artist)
    }

    /// Første linje i teksten på posten, uten lenker, kortet ned så den passer som tittel.
    private static func xTitle(_ text: String) -> String {
        let withoutLinks = text.replacingOccurrences(of: #"https?://\S+"#, with: "", options: .regularExpression)
        let line = withoutLinks.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .first { !$0.isEmpty } ?? ""
        return line.count > 60 ? String(line.prefix(59)).trimmingCharacters(in: .whitespaces) + "…" : line
    }

    private static func firstMatch(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }

    private static func unescape(_ text: String) -> String {
        text.replacingOccurrences(of: "\\u002F", with: "/")
            .replacingOccurrences(of: "\\u0026", with: "&")
            .replacingOccurrences(of: "\\/", with: "/")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
    }

    // MARK: Lagring

    /// Lagrer lyden fra [source] som `.m4a`, og gir tilbake en `ImportedAudio` som JSON.
    private static func save(source: URL, clipId: String, title: String, artist: String) async throws -> String {
        let asset = AVURLAsset(url: source)
        let tracks = try await asset.loadTracks(withMediaType: .audio)
        guard !tracks.isEmpty else {
            throw EkkoError("Videoen har ingen lyd.")
        }
        let target = EkkoFiles.audio(clipId)
        try? FileManager.default.removeItem(at: target)
        guard let export = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else {
            throw EkkoError("Klarte ikke å hente ut lyden.")
        }
        try await export.export(to: target, as: .m4a)
        try Task.checkCancellation()

        let saved = AVURLAsset(url: target)
        let duration = try await saved.load(.duration).seconds
        // Omtrent seks streker per sekund, så man kan klippe nøyaktig, men ikke uendelig mange.
        let bars = min(1000, max(160, Int(duration * 6)))
        let waveform = (try? await Self.waveform(of: saved, bars: bars)) ?? []
        let json: [String: Any] = [
            "title": title,
            "artist": artist,
            "durationSeconds": duration,
            "waveform": waveform.map { (Double($0) * 100).rounded() / 100 },
        ]
        let data = try JSONSerialization.data(withJSONObject: json)
        return String(decoding: data, as: UTF8.self)
    }

    /// Hvor høy lyden er gjennom filen, delt inn i [bars] biter, fra 0 til 1.
    private static func waveform(of asset: AVURLAsset, bars: Int) async throws -> [Float] {
        guard let track = try await asset.loadTracks(withMediaType: .audio).first else { return [] }
        let reader = try AVAssetReader(asset: asset)
        let output = AVAssetReaderTrackOutput(track: track, outputSettings: [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false,
            AVNumberOfChannelsKey: 1,
            AVSampleRateKey: 8000,
        ])
        reader.add(output)
        guard reader.startReading() else { return [] }

        // Lydnivået for hvert 50. millisekund.
        let window = 400
        var levels: [Float] = []
        var sum: Float = 0
        var count = 0
        while let buffer = output.copyNextSampleBuffer() {
            try Task.checkCancellation()
            guard let block = CMSampleBufferGetDataBuffer(buffer) else { continue }
            let length = CMBlockBufferGetDataLength(block)
            var samples = [Int16](repeating: 0, count: length / 2)
            samples.withUnsafeMutableBytes { bytes in
                _ = CMBlockBufferCopyDataBytes(block, atOffset: 0, dataLength: bytes.count, destination: bytes.baseAddress!)
            }
            for sample in samples {
                let value = Float(sample) / 32768
                sum += value * value
                count += 1
                if count == window {
                    levels.append((sum / Float(window)).squareRoot())
                    sum = 0
                    count = 0
                }
            }
        }
        if count > 0 { levels.append((sum / Float(count)).squareRoot()) }
        guard !levels.isEmpty else { return [] }

        // Den høyeste verdien i hver bit, skalert så den høyeste streken er 1.
        var result: [Float] = []
        for i in 0..<bars {
            let from = min(i * levels.count / bars, levels.count - 1)
            let to = min(max(from + 1, (i + 1) * levels.count / bars), levels.count)
            result.append(levels[from..<to].max() ?? 0)
        }
        let loudest = result.max() ?? 0
        guard loudest > 0 else { return result }
        return result.map { powf($0 / loudest, 0.7) }
    }

    // MARK: Hjelpere

    private static func describe(_ error: Error) -> String {
        if let error = error as? EkkoError { return error.message }
        if error is URLError { return "Fikk ikke kontakt. Sjekk nettet og prøv igjen." }
        return "Klarte ikke å hente lyden. (\(error.localizedDescription))"
    }

    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    private func importPicked(_ fetch: @escaping () async throws -> (URL, String)) {
        guard let picked = picking else { return }
        picking = nil
        start(clipId: picked.clipId, listener: picked.listener) { _ in
            let (file, title) = try await fetch()
            return (file, title, "")
        }
    }

    private func pickingCancelled() {
        guard let picked = picking else { return }
        picking = nil
        picked.listener.onFailed(message: "")
    }
}

extension EkkoImport: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else { return pickingCancelled() }
        importPicked {
            let file: URL = try await withCheckedThrowingContinuation { continuation in
                provider.loadFileRepresentation(forTypeIdentifier: UTType.movie.identifier) { url, error in
                    // Filen slettes når denne blokken er ferdig, så den må kopieres med en gang.
                    guard let url else {
                        continuation.resume(throwing: error ?? EkkoError("Klarte ikke å hente videoen."))
                        return
                    }
                    let copy = EkkoFiles.temporary(extension: url.pathExtension)
                    do {
                        try FileManager.default.copyItem(at: url, to: copy)
                        continuation.resume(returning: copy)
                    } catch {
                        continuation.resume(throwing: error)
                    }
                }
            }
            return (file, "")
        }
    }
}

extension EkkoImport: UIDocumentPickerDelegate {
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return pickingCancelled() }
        importPicked { (url, url.deletingPathExtension().lastPathComponent) }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        pickingCancelled()
    }
}
