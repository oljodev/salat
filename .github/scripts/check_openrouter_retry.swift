import Foundation

/// Kjøres på macOS uten nettverk. Injiserte svar tester ekte retry-logikk.
@main
struct CheckOpenRouterRetry {
    static func response(_ status: Int, headers: [String: String] = [:]) -> URLResponse {
        HTTPURLResponse(url: URL(string: "https://example.invalid")!, statusCode: status, httpVersion: nil, headerFields: headers)!
    }

    static func main() async throws {
        var calls = 0
        var discarded = 0
        var waits: [TimeInterval] = []
        let result = try await OpenRouterRetry.run(operation: {
            calls += 1
            return (calls, response(calls < 3 ? 502 : 200))
        }, discard: { _ in discarded += 1 }, wait: { waits.append($0) })
        assert(calls == 3 && discarded == 2 && result.0 == 3)
        assert(waits == [0.6, 1.2])

        for status in [400, 401, 402, 403, 429, 200, 500, 502, 503, 504] {
            calls = 0
            let result = try await OpenRouterRetry.run(operation: {
                calls += 1
                return ("body", response(status))
            }, wait: { _ in })
            assert(calls == ([500, 502, 503, 504].contains(status) ? 3 : 1))
            assert(result.0 == "body", "Den siste feilmeldingen må beholdes")
        }

        calls = 0
        _ = try await OpenRouterRetry.run(operation: {
            calls += 1
            return (0, response(503, headers: ["Retry-After": "120"]))
        }, wait: { _ in assertionFailure("Ikke prøv før serverens lange ventetid") })
        assert(calls == 1)

        calls = 0
        _ = try await OpenRouterRetry.run(operation: {
            calls += 1
            return (0, response(calls == 1 ? 503 : 200, headers: ["Retry-After": "2"]))
        }, wait: { assert($0 >= 2) })
        assert(calls == 2)

        calls = 0
        do {
            _ = try await OpenRouterRetry.run(operation: {
                calls += 1
                return (0, response(502))
            }, wait: { _ in throw CancellationError() })
            assertionFailure("Avbrudd skal forplante seg")
        } catch is CancellationError { assert(calls == 1) }

        calls = 0
        do {
            _ = try await OpenRouterRetry.run(operation: { () async throws -> (Int, URLResponse) in
                calls += 1
                throw URLError(.networkConnectionLost)
            }, wait: { _ in assertionFailure("Ikke gjenta et kall med ukjent resultat") })
            assertionFailure("Nettverksfeilen skal forplante seg")
        } catch is URLError { assert(calls == 1) }

        print("OpenRouter retry checks passed")
    }
}
