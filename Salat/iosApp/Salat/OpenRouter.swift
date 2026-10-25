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
