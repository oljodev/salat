import AppIntents
import Shared
import UIKit

// Brems har én handling i Snarveier, «Brems», som gjør alt. Den legges i en automasjon som kjører når
// appene åpnes. Skal appen vente eller er den sperret, hopper Salat fram med pausen med en gang.

/// Det handlingen skal gjøre.
enum BremsEvent: String, AppEnum {
    case opened
    case closed
    case block
    case status

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Hendelse"
    static let caseDisplayRepresentations: [BremsEvent: DisplayRepresentation] = [
        .opened: DisplayRepresentation(title: "Åpnet", subtitle: "En app ble åpnet. Brems tar pausen eller sperren."),
        .closed: DisplayRepresentation(title: "Lukket", subtitle: "En app ble lukket, så Brems vet hvor lenge den var åpen."),
        .block: DisplayRepresentation(title: "Sperr", subtitle: "Sperr alle appene i noen minutter. Kan ikke oppheves."),
        .status: DisplayRepresentation(title: "Status", subtitle: "Gir «Sperret», «Pause», «Fri» eller «Av»."),
    ]
}

/// Den ene handlingen. Gir «OK», «Pause», «Sperret», «Fri» eller «Av», så man kan bygge videre på den i Snarveier.
struct BremsIntent: AppIntent {
    static let title: LocalizedStringResource = "Brems"
    static let description = IntentDescription(
        "Legg denne i en automasjon som kjører når en app åpnes. Skal appen vente eller er den sperret, åpnes Salat med en gang. Velg «Lukket» i en automasjon for når appene lukkes, så Brems kan måle tiden. «Sperr» sperrer alle appene i noen minutter, f.eks. fra en automasjon for et klokkeslett eller en fokusmodus."
    )
    static let supportedModes: IntentModes = [.background, .foreground(.dynamic)]

    @Parameter(title: "Hendelse", default: .opened)
    var event: BremsEvent

    @Parameter(title: "App", description: "Navnet på appen, f.eks. TikTok. Kan stå tomt.")
    var app: String?

    @Parameter(title: "Minutter", description: "Hvor lenge «Sperr» sperrer appene.", default: 60)
    var minutes: Int

    static var parameterSummary: some ParameterSummary {
        Switch(\.$event) {
            Case(.block) { Summary("Brems: \(\.$event) i \(\.$minutes) minutter") }
            Case(.closed) { Summary("Brems: \(\.$event)") }
            DefaultCase { Summary("Brems: \(\.$event) \(\.$app)") }
        }
    }

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let bridge = IosNativeBridge.shared
        switch event {
        case .opened:
            let result = await bremsOpened(app: app) { try await continueInForeground(alwaysConfirm: false) }
            return .result(value: result)
        case .closed:
            BremsStoreKt.bremsClosed(bridge: bridge)
            return .result(value: "OK")
        case .block:
            BremsStoreKt.bremsBlock(bridge: bridge, minutes: Int32(minutes))
            return .result(value: "Sperret")
        case .status:
            return .result(value: BremsStoreKt.bremsStatus(bridge: bridge, app: app))
        }
    }
}

/// Teller åpningen. Skal appen vente, åpnes pausen i Salat. Slipper ikke iOS Salat fram, kommer et varsel i stedet.
@MainActor
private func bremsOpened(app: String?, foreground: () async throws -> Void) async -> String {
    let bridge = IosNativeBridge.shared
    guard BremsStoreKt.bremsOpened(bridge: bridge, app: app) else { return "OK" }
    let state = BremsStoreKt.bremsStatus(bridge: bridge, app: app) == "Sperret" ? "Sperret" : "Pause"
    WidgetActions.open("salat://brems/pause")
    if UIApplication.shared.applicationState == .active { return state }
    do {
        try await foreground()
        BremsStoreKt.bremsLog(bridge: bridge, text: "Salat kom fram")
    } catch {
        BremsStoreKt.bremsLog(bridge: bridge, text: "Fikk ikke åpne Salat: \(error.localizedDescription)")
        Notifications.shared.sendBremsNow(
            title: state == "Sperret" ? "Sperret" : "Pause først",
            body: "Trykk her for å åpne Brems."
        )
    }
    return state
}

/// Den gamle handlingen for når en app åpnes. Skjult, men virker fortsatt i automasjoner som bruker den.
struct BremsOpenedIntent: AppIntent {
    static let title: LocalizedStringResource = "Brems: App åpnet"
    static let description = IntentDescription("Erstattet av «Brems». Virker fortsatt.")
    static let isDiscoverable = false
    static let supportedModes: IntentModes = [.background, .foreground(.dynamic)]

    @Parameter(title: "App", description: "Navnet på appen, f.eks. TikTok. Kan stå tomt.")
    var app: String?

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let result = await bremsOpened(app: app) { try await continueInForeground(alwaysConfirm: false) }
        // Gamle automasjoner åpner pausen selv når svaret er «Pause».
        return .result(value: result == "OK" ? "OK" : "Pause")
    }
}

/// Den gamle handlingen for når en app lukkes. Skjult, men virker fortsatt.
struct BremsClosedIntent: AppIntent {
    static let title: LocalizedStringResource = "Brems: App lukket"
    static let description = IntentDescription("Erstattet av «Brems» med «Lukket». Virker fortsatt.")
    static let isDiscoverable = false

    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        BremsStoreKt.bremsClosed(bridge: IosNativeBridge.shared)
        return .result()
    }
}
