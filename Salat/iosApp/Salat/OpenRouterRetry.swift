import Foundation

/// Prøver bare igjen ved en eksplisitt serverfeil, før et vellykket svar er gitt til mottakeren.
enum OpenRouterRetry {
    static func run<Value>(
        operation: () async throws -> (Value, URLResponse),
        discard: (Value) async throws -> Void = { _ in },
        wait: (TimeInterval) async throws -> Void = { delay in
            try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        }
    ) async throws -> (Value, URLResponse) {
        for attempt in 0..<3 {
            try Task.checkCancellation()
            let result = try await operation()
            try Task.checkCancellation()
            guard let response = result.1 as? HTTPURLResponse,
                  attempt < 2, [500, 502, 503, 504].contains(response.statusCode) else { return result }
            var delay = 0.6 * pow(2, Double(attempt))
            if let header = response.value(forHTTPHeaderField: "Retry-After") {
                let formatter = DateFormatter()
                formatter.locale = Locale(identifier: "en_US_POSIX")
                formatter.timeZone = TimeZone(secondsFromGMT: 0)
                formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss z"
                let requested = Double(header) ?? formatter.date(from: header)?.timeIntervalSinceNow
                if let requested {
                    // Lange ventetider vises som feil i stedet for å fryse samtalen eller prøve for tidlig.
                    guard requested.isFinite, requested <= 5 else { return result }
                    delay = max(delay, requested)
                }
            }
            // Frigi feilstrømmen før neste forsøk. En avbrutt Task skal aldri starte et nytt kall.
            try? await discard(result.0)
            try Task.checkCancellation()
            try await wait(delay)
        }
        preconditionFailure("Retry loop must return or throw")
    }
}
