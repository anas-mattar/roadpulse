import SwiftUI
import UserNotifications

@main
struct RoadPulseApp: App {
    init() {
        UNUserNotificationCenter.current().delegate = NotificationRouter.shared
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
