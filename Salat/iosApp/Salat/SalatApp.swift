import Shared
import SwiftUI

@main
struct SalatApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        Notifications.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                // Lenker fra widgetene, f.eks. salat://kompis/start.
                .onOpenURL { WidgetActions.open($0.absoluteString) }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                // SideStore kan ha fornyet appen siden sist, så flytt påminnelsen til ny utløpsdato.
                Notifications.shared.scheduleExpiryReminder()
                // Fyll på alarmer i Rise.
                RiseStoreKt.refreshRise(bridge: IosNativeBridge.shared)
                // Er Salat åpen, er appen Brems fulgte med på forlatt, så økta er over.
                BremsStoreKt.bremsSalatOpened(bridge: IosNativeBridge.shared)
                // Gi widgetene lydene i Ekko, og ta en lenke fra kontrollsenteret som venter.
                EkkoWidgetsKt.refreshEkkoWidget(bridge: IosNativeBridge.shared)
                WidgetActions.openPendingLink()
            }
        }
    }
}
