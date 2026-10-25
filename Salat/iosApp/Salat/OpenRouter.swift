import Foundation

/// Kall til OpenRouter sitt API. Svarene kommer alltid på hovedtråden.
enum OpenRouter {
    static let baseUrl = URL(string: "https://openrouter.ai/api/v1/")!

    /// Henter [path], f.eks. `models/user?output_modalities=transcription`.
    static func get(path: String, apiKey: String, completion: @escaping (_ body: String?, _ error: String?) -> Void) {
        guard let url = URL(string: path, relativeTo: baseUrl) else {
            completion(nil, "Ugyldig adresse: \(path)")
            return
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        URLSession.shared.dataTask(with: request) { data, response, error in
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            DispatchQueue.main.async {
                if let error {
                    completion(nil, "Fikk ikke kontakt med OpenRouter: \(error.localizedDescription)")
                } else if status == 200, let data {
                    completion(String(decoding: data, as: UTF8.self), nil)
                } else {
                    completion(nil, errorMessage(status: status, data: data))
                }
            }
        }.resume()
    }

    /// Åpner forbindelsen til OpenRouter, så neste kall ikke må vente på at den kobles opp. Svaret
    /// er uinteressant; det viktige er at forbindelsen er klar og gjenbrukes.
    static func warmUp() {
        var request = URLRequest(url: URL(string: "key", relativeTo: baseUrl)!)
        request.timeoutInterval = 10
        URLSession.shared.dataTask(with: request) { _, _, _ in }.resume()
    }

    /// En feilmelding for brukeren, ut fra statuskoden og feilen OpenRouter sendte.
    static func errorMessage(status: Int, data: Data?) -> String {
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
}

/// Et svar fra `chat/completions` som strømmer. Hver hendelse sendes videre til Kotlin som den er,
/// med tiden siden forespørselen ble sendt. Det finnes bare ett svar om gangen.
final class OpenRouterChat {
    static let shared = OpenRouterChat()

    private var task: Task<Void, Never>?
    /// Øker for hvert svar, så hendelser fra et stoppet svar ikke slipper gjennom.
    private var generation = 0

    func start(apiKey: String, bodyJson: String, listener: ChatListener) {
        cancel()
        let current = generation
        var request = URLRequest(url: URL(string: "chat/completions", relativeTo: OpenRouter.baseUrl)!)
        request.httpMethod = "POST"
        request.timeoutInterval = 60
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.httpBody = Data(bodyJson.utf8)
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

        task = Task {
            do {
                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                guard status == 200 else {
                    // Feil før strømmen starter, kommer som vanlig JSON.
                    var data = Data()
                    for try await byte in bytes {
                        data.append(byte)
                    }
                    report { listener.onError(message: OpenRouter.errorMessage(status: status, data: data)) }
                    return
                }
                for try await line in bytes.lines {
                    // Linjer som begynner med kolon er kommentarer som holder forbindelsen åpen.
                    guard line.hasPrefix("data:") else { continue }
                    let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                    if payload == "[DONE]" { break }
                    let millis = elapsed()
                    report { listener.onEvent(dataJson: payload, millis: millis) }
                }
                let millis = elapsed()
                report { listener.onDone(millis: millis) }
            } catch {
                guard !Task.isCancelled else { return }
                report { listener.onError(message: "Fikk ikke kontakt med OpenRouter: \(error.localizedDescription)") }
            }
        }
    }

    func cancel() {
        generation += 1
        task?.cancel()
        task = nil
    }
}
