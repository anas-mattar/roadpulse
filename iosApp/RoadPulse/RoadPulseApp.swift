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
                // Buttons, switches and links use the Jalanna teal instead of the system blue.
                .tint(Color(red: 0.043, green: 0.498, blue: 0.471))
        }
    }
}
