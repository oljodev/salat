import Foundation
import Shared
import UIKit

/// Swift-implementasjonen av `NativeBridge` fra Kotlin.
final class IosNativeBridge: NSObject, NativeBridge {
    static let shared = IosNativeBridge()

    func signingExpiresAtMillis() -> KotlinLong? {
        guard let expiry = ProvisioningProfile.expirationDate else { return nil }
        return KotlinLong(value: Int64(expiry.timeIntervalSince1970 * 1000))
    }

    func scheduleExpiryReminder() {
        Notifications.shared.scheduleExpiryReminder()
    }

    func sendTestNotification(delaySeconds: Int32) {
        Notifications.shared.sendTestNotification(after: TimeInterval(delaySeconds))
    }

    func scheduleTestAlarm(delaySeconds: Int32, snoozeMinutes: Int32, onResult: @escaping (String?) -> Void) {
        Alarms.shared.scheduleTestAlarm(
            after: TimeInterval(delaySeconds),
            snoozeMinutes: Int(snoozeMinutes),
            completion: onResult
        )
    }

    func loadText(key: String) -> String? {
        UserDefaults.standard.string(forKey: "salat.\(key)")
    }

    func saveText(key: String, value: String) {
        UserDefaults.standard.set(value, forKey: "salat.\(key)")
    }

    func loadSecret(key: String) -> String? {
        Keychain.load(key)
    }

    func saveSecret(key: String, value: String) {
        Keychain.save(key, value: value)
    }

    func applyRiseSchedule(planJson: String, onResult: @escaping (String?) -> Void) {
        RiseScheduler.apply(planJson: planJson, completion: onResult)
    }

    func previewSound(sound: String) {
        SoundPreview.shared.play(sound)
    }

    func stopSoundPreview() {
        SoundPreview.shared.stop()
    }

    func startLiveSession(configJson: String, listener: LiveListener) {
        EkkoAudio.shared.pauseForOtherAudio()
        LiveSession.shared.start(configJson: configJson, listener: listener)
    }

    func stopLiveSession() {
        LiveSession.shared.stop()
    }

    func setLiveMuted(muted: Bool) {
        LiveSession.shared.setMuted(muted)
    }

    func rollLiveSession(historyJson: String) {
        LiveSession.shared.roll(historyJson: historyJson)
    }

    func kompisSttOpen(listener: SttListener) {
        EkkoAudio.shared.pauseForOtherAudio()
        SttRecorder.shared.open(listener: listener)
    }

    func kompisSttStartRecording() {
        SttRecorder.shared.startRecording()
    }

    func kompisSttStopRecording(configJson: String) {
        SttRecorder.shared.stopRecording(configJson: configJson)
    }

    func kompisSttClose() {
        SttRecorder.shared.close()
    }

    func ekkoImportYouTube(clipId: String, videoId: String, listener: EkkoImportListener) {
        EkkoImport.shared.youTube(clipId: clipId, videoId: videoId, listener: listener)
    }

    func ekkoImportWeb(clipId: String, url: String, listener: EkkoImportListener) {
        EkkoImport.shared.web(clipId: clipId, url: url, listener: listener)
    }

    func ekkoImportFile(clipId: String, fromPhotos: Bool, listener: EkkoImportListener) {
        EkkoImport.shared.file(clipId: clipId, fromPhotos: fromPhotos, listener: listener)
    }

    func ekkoCancelImport() {
        EkkoImport.shared.cancel()
    }

    func ekkoDeleteAudio(clipId: String) {
        EkkoFiles.delete(clipId)
    }

    func ekkoPasteboardText() -> String? {
        UIPasteboard.general.url?.absoluteString ?? UIPasteboard.general.string
    }

    func ekkoPlay(
        clipId: String,
        startSeconds: Double,
        endSeconds: Double,
        fromSeconds: Double,
        title: String,
        artist: String,
        lockScreen: Bool,
        listener: EkkoPlayerListener
    ) {
        EkkoAudio.shared.play(
            clipId: clipId,
            start: startSeconds,
            end: endSeconds,
            from: fromSeconds,
            title: title,
            artist: artist,
            lockScreen: lockScreen,
            listener: listener
        )
    }

    func ekkoPause() {
        EkkoAudio.shared.pause()
    }

    func ekkoResume() {
        EkkoAudio.shared.resume()
    }

    func ekkoSeek(seconds: Double) {
        EkkoAudio.shared.seek(to: seconds)
    }

    func ekkoStop() {
        EkkoAudio.shared.stop()
    }

    func saveWidgetData(name: String, json: String) {
        WidgetActions.save(name, Data(json.utf8))
    }

    func openUrl(url: String) {
        guard let url = URL(string: url) else { return }
        UIApplication.shared.open(url)
    }

    func bremsNotify(noticesJson: String) {
        Notifications.shared.scheduleBrems(noticesJson)
    }

    func haptic(kind: String) {
        switch kind {
        case "selection": UISelectionFeedbackGenerator().selectionChanged()
        case "rigid": UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        case "success": UINotificationFeedbackGenerator().notificationOccurred(.success)
        case "warning": UINotificationFeedbackGenerator().notificationOccurred(.warning)
        default: UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }
}
