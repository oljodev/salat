import AppIntents
import Shared
import UIKit

// Handlingene automasjonene i Snarveier kjører, så Brems vet når apper åpnes og lukkes.

/// Kjøres når en app åpnes. Gir «Pause» når det er tid for en pause, ellers «OK».
/// Automasjonen åpner salat://brems/pause når svaret er «Pause». Det er mer pålitelig enn at handlingen
/// ber om å komme fram selv, som Snarveier av og til avviser.
struct BremsOpenedIntent: AppIntent {
    static let title: LocalizedStringResource = "Brems: App åpnet"
    static let description = IntentDescription(
        "Legg denne i en automasjon som kjører når en app åpnes. Gir «Pause» når det er tid for en pause. " +
            "Legg til «Hvis» etterpå, og åpne salat://brems/pause når svaret er «Pause»."
    )
    static let supportedModes: IntentModes = [.background]

    @Parameter(title: "App", description: "Navnet på appen, f.eks. TikTok. Kan stå tomt.")
    var app: String?

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let bridge = IosNativeBridge.shared
        guard BremsStoreKt.bremsOpened(bridge: bridge, app: app) else { return .result(value: "OK") }
        if UIApplication.shared.applicationState == .active {
            WidgetActions.open("salat://brems/pause")
        } else {
            // Åpner automasjonen Salat, fjernes varselet før det kommer. Ellers er det en reserve.
            Notifications.shared.sendBremsNow(
                title: "Pause først",
                body: "Trykk her for å ta pausen i Brems.",
                afterSeconds: 4
            )
        }
        return .result(value: "Pause")
    }
}

/// Kjøres når en app lukkes, så Brems vet hvor lenge den var åpen.
struct BremsClosedIntent: AppIntent {
    static let title: LocalizedStringResource = "Brems: App lukket"
    static let description = IntentDescription("Legg denne i en automasjon som kjører når en app lukkes.")

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        BremsStoreKt.bremsClosed(bridge: IosNativeBridge.shared)
        return .result()
    }
}
