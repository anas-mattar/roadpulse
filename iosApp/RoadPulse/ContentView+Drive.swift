import RoadPulseShared
import SwiftUI

/// The turn-by-turn screen.
extension ContentView {
    func drivingOverlay(_ s: DriveSnapshot) -> some View {
        VStack(spacing: 8) {
            turnBanner(s)
            if !s.lanes.isEmpty && s.distanceToTurnM >= 0 && s.distanceToTurnM <= 1500 { lanes(s) }
            HStack(alignment: .top) {
                alerts(s)
                Spacer()
                VStack(spacing: 10) {
                    circleButton(drive.muted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                                 label: drive.muted ? strings.unmuteVoice : strings.muteVoice) { drive.muted.toggle() }
                    circleButton("location.fill", label: strings.showMyLocation) { recenterToken += 1 }
                    circleButton("map", label: strings.overview) {
                        overviewShape = drive.remainingShape()
                        overviewToken += 1
                    }
                    if model.hasServer && !s.sharing {
                        circleButton("square.and.arrow.up", label: strings.shareDrive) { showSharePreview = true }
                    }
                }
            }
            Spacer()
            if let point = model.pinned { pinnedCard(point, driving: true) }
            speedRow(s)
            if let question = s.stillThereQuestion { stillThereCard(question, colorHex: s.stillThereColorHex ?? "#4C4F69") }
            if !s.routeOfferTitle.isEmpty { fasterRouteCard(s) }
            tripCard(s)
        }
    }

    func turnBanner(_ s: DriveSnapshot) -> some View {
        HStack(spacing: 14) {
            Image(systemName: turnSymbol(s.turnIcon))
                .font(.system(size: 38, weight: .bold))
                .frame(width: 52)
            VStack(alignment: .leading, spacing: 2) {
                if s.distanceToTurnM >= 0 {
                    Text(strings.distance(km: s.distanceToTurnM / 1000)).font(.system(size: 30, weight: .semibold))
                }
                Text(s.instruction).font(.subheadline).opacity(0.85).lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .foregroundColor(.white)
        .padding(.horizontal, 16).padding(.vertical, 14)
        .background(brand)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 5)
    }

    /// Lanes: always left to right, as on the road.
    func lanes(_ s: DriveSnapshot) -> some View {
        HStack(spacing: 4) {
            ForEach(Array(s.lanes.enumerated()), id: \.offset) { _, lane in
                Image(systemName: turnSymbol(lane.icon))
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Color.white.opacity(laneOpacity(lane)))
                    .frame(width: 34, height: 34)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
        .background(Color(red: 0.031, green: 0.204, blue: 0.227))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .environment(\.layoutDirection, .leftToRight)
    }

    func alerts(_ s: DriveSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if s.sharing {
                Button { showSharedPanel = true } label: {
                    pill(strings.sharingLive, icon: "dot.radiowaves.left.and.right", trailing: strings.viewers(count: s.shareViewers),
                         background: Color(red: 0.06, green: 0.43, blue: 0.34))
                }
            }
            if let label = s.reportLabel, s.reportAheadM >= 0 {
                pill(label, icon: "exclamationmark.triangle.fill", trailing: strings.distance(km: s.reportAheadM / 1000),
                     background: Color(hex: s.reportColorHex ?? "#4C4F69"))
            }
            if let label = s.cameraLabel, s.cameraAheadM >= 0 {
                pill(label, icon: "camera.fill", trailing: strings.distance(km: s.cameraAheadM / 1000),
                     background: Color(red: 0.031, green: 0.204, blue: 0.227))
            }
            if let label = s.schoolLabel, s.schoolAheadM >= 0 {
                pill(label, icon: "graduationcap.fill", trailing: strings.distance(km: s.schoolAheadM / 1000),
                     background: Color(red: 0.98, green: 0.78, blue: 0.46), foreground: Color(red: 0.39, green: 0.22, blue: 0.02))
            }
            if let status = s.status {
                pill(status, icon: "info.circle.fill", trailing: nil, background: Color.orange.opacity(0.9), foreground: .black)
            }
        }
    }

    func speedRow(_ s: DriveSnapshot) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            if prefs.showSpeedometer && s.speedKmh >= 0 {
                VStack(spacing: 0) {
                    Text("\(s.speedKmh)").font(.system(size: 22, weight: .semibold))
                    Text(strings.kmh).font(.system(size: 10))
                }
                .foregroundColor(isSpeeding(s) ? Color.red : Color.white)
                .frame(width: 64, height: 64)
                .background(Color(white: 0.17)).clipShape(Circle())
            }
            if s.speedLimitKph > 0 && Choices.shared.showLimit(limitDisplayName: prefs.limitDisplay, speeding: isSpeeding(s)) {
                Text("\(s.speedLimitKph)").font(.system(size: 17, weight: .bold)).foregroundColor(.black)
                    .frame(width: 46, height: 46)
                    .background(Color.white).clipShape(Circle())
                    .overlay(Circle().stroke(Color.red, lineWidth: 5))
            }
            Spacer()
            if model.hasServer {
                Button { showReport = true } label: {
                    Image(systemName: "exclamationmark.bubble.fill")
                        .font(.system(size: 22)).foregroundColor(Color(red: 0.39, green: 0.22, blue: 0.02))
                        .frame(width: 56, height: 56)
                        .background(Color(red: 0.98, green: 0.78, blue: 0.46)).clipShape(Circle()).shadow(radius: 3)
                }
                .accessibilityLabel(strings.report)
            }
        }
    }

    /// Just passed a report: one tap to say whether it is still there (closes by itself).
    func stillThereCard(_ question: String, colorHex: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(question).font(.headline)
            HStack(spacing: 8) {
                answerButton(strings.stillThere, icon: "hand.thumbsup.fill", filled: true) { drive.answerStillThere(true) }
                answerButton(strings.notThere, icon: "hand.thumbsdown.fill", filled: false) { drive.answerStillThere(false) }
                Button(strings.skip) { drive.answerStillThere(nil) }
                    .foregroundColor(.white.opacity(0.85)).padding(.horizontal, 6)
            }
        }
        .foregroundColor(.white)
        .padding(14)
        .background(Color(hex: colorHex))
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    func answerButton(_ text: String, icon: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(text, systemImage: icon).font(.subheadline.bold())
                .frame(maxWidth: .infinity).frame(height: 44)
                .background(filled ? Color.white : Color.clear)
                .foregroundColor(filled ? .black : .white)
                .overlay(Capsule().stroke(Color.white, lineWidth: 1))
                .clipShape(Capsule())
        }
    }

    func fasterRouteCard(_ s: DriveSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(s.routeOfferTitle).font(.headline)
            HStack(spacing: 10) {
                Button { drive.takeFasterRoute() } label: {
                    Text(strings.takeRoute).bold().frame(maxWidth: .infinity).frame(height: 46)
                        .background(Color.white).foregroundColor(Color(red: 0.06, green: 0.43, blue: 0.34)).clipShape(Capsule())
                }
                Button { drive.keepRoute() } label: {
                    Text(strings.keepRoute).frame(maxWidth: .infinity).frame(height: 46)
                        .overlay(Capsule().stroke(Color.white, lineWidth: 1)).foregroundColor(.white)
                }
            }
        }
        .foregroundColor(.white)
        .padding(14)
        .background(Color(red: 0.06, green: 0.43, blue: 0.34))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .task(id: s.routeOfferTitle) {
            // Closes by itself (keeping the route) after 20 seconds.
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            if !(drive.snapshot?.routeOfferTitle ?? "").isEmpty { drive.keepRoute() }
        }
    }

    /// Arrival time and what is left; tap (or the handle) to pull up the drive sheet.
    func tripCard(_ s: DriveSnapshot) -> some View {
        VStack(spacing: 8) {
            Capsule().fill(Color.white.opacity(0.4)).frame(width: 40, height: 4)
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(arrival(s), style: .time).font(.system(size: 26, weight: .semibold))
                    Text(tripLine(s)).font(.subheadline).opacity(0.8)
                }
                Spacer()
                if !sheetOpen {
                    Button { drive.stop() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                            .frame(width: 52, height: 52).background(Color.red).clipShape(Circle())
                    }
                    .accessibilityLabel(strings.stopNavigation)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { toggleSheet() }
            if sheetOpen {
                driveSheet(s)
            } else {
                TrafficBarView(pieces: s.trafficPieces, progress: s.progress, track: Color(white: 0.35))
            }
        }
        .foregroundColor(.white)
        .padding(16)
        .background(Color(white: 0.2))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 5)
    }

    func toggleSheet() {
        withAnimation(.easeInOut(duration: 0.2)) {
            sheetOpen.toggle()
            if !sheetOpen { alternatives = nil }
        }
    }

    /// The drive sheet: where to, the route with traffic and progress, Routes / Share / Stop.
    func driveSheet(_ s: DriveSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(strings.shareTo(destination: s.destinationName)).font(.footnote).opacity(0.7).lineLimit(1)
            if let via = s.via {
                Text(strings.via(road: via)).font(.headline).lineLimit(2)
            }
            TrafficBarView(pieces: s.trafficPieces, progress: s.progress, track: Color(white: 0.35))
            if let list = alternatives {
                alternativesList(list)
            } else {
                HStack(spacing: 8) {
                    sheetButton(strings.otherRoutes, icon: "arrow.triangle.branch", color: Color(white: 0.35)) { loadAlternatives() }
                    if model.hasServer && !s.sharing {
                        sheetButton(strings.shareDrive, icon: "square.and.arrow.up", color: Color(white: 0.35)) {
                            sheetOpen = false
                            showSharePreview = true
                        }
                    }
                    sheetButton(strings.notificationStop, icon: "xmark", color: .red) { drive.stop() }
                }
            }
        }
    }

    func sheetButton(_ label: String, icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon).font(.system(size: 20, weight: .semibold))
                Text(label).font(.caption).lineLimit(1)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity).padding(.vertical, 10)
            .background(color).clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }

    func loadAlternatives() {
        alternatives = []
        Task { alternatives = await drive.alternatives() }
    }

    func alternativesList(_ list: [RouteSummary]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(strings.otherRoutes).font(.headline)
                Spacer()
                Button { alternatives = nil } label: { Image(systemName: "xmark").foregroundColor(.white) }
                    .accessibilityLabel(strings.close)
            }
            if list.isEmpty {
                ProgressView().tint(.white)
            }
            ForEach(Array(list.enumerated()), id: \.offset) { _, route in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(strings.duration(minutes: route.minutes)).font(.headline)
                        Text(alternativeLine(route)).font(.caption).opacity(0.75).lineLimit(1)
                    }
                    Spacer()
                    Button {
                        sheetOpen = false
                        alternatives = nil
                        drive.useRoute(route)
                    } label: {
                        Text(strings.goShort).bold().padding(.horizontal, 16).padding(.vertical, 8)
                            .background(brand).foregroundColor(.white).clipShape(Capsule())
                    }
                }
                .padding(10)
                .background(Color(white: 0.3)).clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
    }

    func alternativeLine(_ route: RouteSummary) -> String {
        var parts = [strings.distance(km: route.km)]
        if let via = route.via { parts.append(strings.via(road: via)) }
        return parts.joined(separator: " · ")
    }

    // MARK: Share my drive

    /// Before sharing: who can see it, where to and when you arrive.
    var sharePreview: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(strings.shareYourDrive).font(.title2).bold()
            if let s = drive.snapshot {
                Text(strings.shareTo(destination: s.destinationName)).font(.subheadline).foregroundColor(.secondary)
                Text(arrival(s), style: .time).font(.system(size: 34, weight: .semibold))
            }
            Text(strings.shareExplain).font(.subheadline).foregroundColor(.secondary)
            Spacer(minLength: 0)
            HStack(spacing: 10) {
                Button { showSharePreview = false } label: {
                    Text(strings.cancel).frame(maxWidth: .infinity).frame(height: 50)
                        .background(Color(.secondarySystemBackground)).foregroundColor(brand).clipShape(Capsule())
                }
                Button {
                    showSharePreview = false
                    startSharing()
                } label: {
                    Text(strings.shareDrive).bold().frame(maxWidth: .infinity).frame(height: 50)
                        .background(brand).foregroundColor(.white).clipShape(Capsule())
                }
            }
        }
        .padding(20)
        .presentationDetents([.medium])
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }

    /// While sharing: how many people are watching, share the link again, or stop.
    var sharedPanel: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(strings.sharedDrive).font(.title2).bold()
            if let s = drive.snapshot {
                Label(strings.viewers(count: s.shareViewers), systemImage: "eye").font(.headline)
                Text(strings.shareTo(destination: s.destinationName)).font(.subheadline).foregroundColor(.secondary)
            }
            Spacer(minLength: 0)
            HStack(spacing: 10) {
                Button {
                    showSharedPanel = false
                    drive.stopSharing()
                } label: {
                    Text(strings.stopSharing).frame(maxWidth: .infinity).frame(height: 50)
                        .background(Color(.secondarySystemBackground)).foregroundColor(.red).clipShape(Capsule())
                }
                Button {
                    showSharedPanel = false
                    if let url = shareUrl {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { sendShareText(url) }
                    }
                } label: {
                    Text(strings.shareDrive).bold().frame(maxWidth: .infinity).frame(height: 50)
                        .background(brand).foregroundColor(.white).clipShape(Capsule())
                }
            }
        }
        .padding(20)
        .presentationDetents([.medium])
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }

    /// Starts a live link, then opens the share sheet with "I'll arrive at … at …".
    func startSharing() {
        let nickname = model.profile?.nickname ?? ""
        Task {
            do {
                let url = try await drive.startSharing(name: nickname.isEmpty ? nil : nickname)
                shareUrl = url
                // Let the preview sheet close before the share sheet opens.
                try? await Task.sleep(nanoseconds: 600_000_000)
                sendShareText(url)
            } catch {
                model.message = strings.shareFailed
            }
        }
    }

    func sendShareText(_ url: String) {
        guard let s = drive.snapshot else { return }
        let eta = arrival(s).formatted(date: .omitted, time: .shortened)
        shareItem = ShareItem(items: [strings.shareText(destination: s.destinationName, eta: eta, url: url)])
    }

    func laneOpacity(_ lane: LaneArrow) -> Double {
        if lane.recommended { return 1 }
        return lane.usable ? 0.6 : 0.25
    }

    func isSpeeding(_ s: DriveSnapshot) -> Bool {
        let limit = Int(s.speedLimitKph)
        return limit > 0 && Int(s.speedKmh) > limit + prefs.warnOverBy
    }

    /// Arrival time, including the delay from live traffic on the route ahead.
    func arrival(_ s: DriveSnapshot) -> Date {
        let minutes = Int(s.remainingMinutes) + Int(s.trafficDelayMinutes)
        return Date().addingTimeInterval(Double(minutes) * 60)
    }

    func tripLine(_ s: DriveSnapshot) -> String {
        var parts: [String] = [strings.duration(minutes: s.remainingMinutes), strings.distance(km: s.remainingKm)]
        if s.trafficDelayMinutes > 0 { parts.append(strings.trafficDelay(minutes: s.trafficDelayMinutes)) }
        return parts.joined(separator: " · ")
    }
}
