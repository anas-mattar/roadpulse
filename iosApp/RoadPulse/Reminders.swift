import Foundation
import RoadPulseShared
import UserNotifications

/// "Time to leave" reminders for planned drives, as local notifications (nothing leaves the phone).
enum LeaveReminders {
    static func schedule(_ item: PlannedItem, strings: AppStrings) {
        let title = strings.timeToLeave(name: item.place.name)
        let body = strings.timeToLeaveText(driveMinutes: item.driveMinutes, arriveBy: clock(item.arriveByMillis))
        let fireAt = max(date(item.remindAtMillis).timeIntervalSinceNow, 5)
        let id = "planned-\(item.id)"
        let plannedId = String(item.id)
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            content.userInfo = ["plannedId": plannedId]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: fireAt, repeats: false)
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
    }

    static func cancel(_ id: Int64) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["planned-\(id)"])
    }

    static func cancelAll() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }
}

/// Opens the route when the driver taps a "time to leave" notification.
final class NotificationRouter: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationRouter()
    /// The planned drive whose reminder was tapped (handled by the map screen).
    @Published var openPlannedId: Int64?

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let text = response.notification.request.content.userInfo["plannedId"] as? String
        DispatchQueue.main.async {
            if let text = text, let id = Int64(text) { self.openPlannedId = id }
        }
        completionHandler()
    }

    /// Show the reminder even when the app is open.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}

func date(_ millis: Int64) -> Date { Date(timeIntervalSince1970: Double(millis) / 1000) }
func millis(_ date: Date) -> Int64 { Int64(date.timeIntervalSince1970 * 1000) }
/// "08:30" in the phone's own time style.
func clock(_ millis: Int64) -> String { date(millis).formatted(date: .omitted, time: .shortened) }
