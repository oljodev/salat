import UserNotifications

final class Notifications: NSObject, UNUserNotificationCenterDelegate {
    static let shared = Notifications()

    private let center = UNUserNotificationCenter.current()
    private let expiryReminderId = "salat.expiry-reminder"

    /// Ber om tillatelse til varsler og planlegger påminnelsen om fornyelse.
    func start() {
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                self.scheduleExpiryReminder()
            }
        }
    }

    /// Planlegger et varsel ett døgn før signeringen går ut. Erstatter et tidligere planlagt varsel.
    func scheduleExpiryReminder() {
        guard let expiry = ProvisioningProfile.expirationDate else { return }
        let fireDate = expiry.addingTimeInterval(-24 * 60 * 60)
        guard fireDate > Date() else { return }

        let content = UNMutableNotificationContent()
        content.title = "Salat går ut i morgen"
        content.body = "Åpne SideStore og trykk «Refresh All» for å fornye appene dine."
        content.sound = .default

        let components = Calendar.current.dateComponents(
            [.year, .month, .day, .hour, .minute, .second],
            from: fireDate
        )
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        center.add(UNNotificationRequest(identifier: expiryReminderId, content: content, trigger: trigger))
    }

    func sendTestNotification(after seconds: TimeInterval) {
        let content = UNMutableNotificationContent()
        content.title = "Hei fra Salat"
        content.body = "Varsler virker!"
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(seconds, 1), repeats: false)
        center.add(UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger))
    }

    /// Så mange varsler Brems kan ha planlagt samtidig.
    private let bremsSlots = 20

    /// Erstatter varslene fra Brems med `json`, en liste av `ScheduledNotice` fra Kotlin.
    func scheduleBrems(_ json: String) {
        struct Notice: Decodable {
            let title: String
            let body: String
            let atMillis: Int64
        }
        center.removePendingNotificationRequests(withIdentifiers: (0..<bremsSlots).map { "brems.\($0)" })
        let notices = (try? JSONDecoder().decode([Notice].self, from: Data(json.utf8))) ?? []
        for (index, notice) in notices.prefix(bremsSlots).enumerated() {
            let seconds = Double(notice.atMillis) / 1000 - Date().timeIntervalSince1970
            guard seconds > 0 else { continue }
            let content = UNMutableNotificationContent()
            content.title = notice.title
            content.body = notice.body
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false)
            center.add(UNNotificationRequest(identifier: "brems.\(index)", content: content, trigger: trigger))
        }
    }

    /// Et varsel fra Brems med en lenke til pausen, med en gang eller etter [afterSeconds].
    func sendBremsNow(title: String, body: String, afterSeconds: TimeInterval = 0) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.userInfo = ["link": "salat://brems/pause"]
        let trigger = afterSeconds > 0 ? UNTimeIntervalNotificationTrigger(timeInterval: afterSeconds, repeats: false) : nil
        center.add(UNNotificationRequest(identifier: "brems.now", content: content, trigger: trigger))
    }

    /// Salat er framme, så varselet om pausen trengs ikke lenger.
    func cancelBremsNow() {
        center.removePendingNotificationRequests(withIdentifiers: ["brems.now"])
        center.removeDeliveredNotifications(withIdentifiers: ["brems.now"])
    }

    /// Trykker man på et varsel med en lenke, åpnes den.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let link = response.notification.request.content.userInfo["link"] as? String {
            DispatchQueue.main.async { WidgetActions.open(link) }
        }
        completionHandler()
    }

    /// Vis varsler også når appen er åpen.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}
