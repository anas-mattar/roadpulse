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
                    if model.hasServer && !s.sharing {
                        circleButton("square.and.arrow.up", label: strings.shareDrive) { shareDrive(s) }
                    }
                }
            }
            Spacer()
            speedRow(s)
            if let question = s.stillThereQuestion { stillThereCard(question, colorHex: s.stillThereColorHex ?? "#4C4F69") }
            if s.fasterRouteSavesMinutes > 0 { fasterRouteCard(s) }
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
        .background(indigo)
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
        .background(Color(red: 0.15, green: 0.13, blue: 0.36))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .environment(\.layoutDirection, .leftToRight)
    }

    func alerts(_ s: DriveSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if s.sharing {
                Button { drive.stopSharing() } label: {
                    pill(strings.sharingLive, icon: "dot.radiowaves.left.and.right", trailing: strings.stopSharing,
                         background: Color(red: 0.06, green: 0.43, blue: 0.34))
                }
            }
            if let label = s.reportLabel, s.reportAheadM >= 0 {
                pill(label, icon: "exclamationmark.triangle.fill", trailing: strings.distance(km: s.reportAheadM / 1000),
                     background: Color(hex: s.reportColorHex ?? "#4C4F69"))
            }
            if let label = s.cameraLabel, s.cameraAheadM >= 0 {
                pill(label, icon: "camera.fill", trailing: strings.distance(km: s.cameraAheadM / 1000),
                     background: Color(red: 0.15, green: 0.13, blue: 0.36))
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
            if s.speedKmh >= 0 {
                VStack(spacing: 0) {
                    Text("\(s.speedKmh)").font(.system(size: 22, weight: .semibold))
                    Text(strings.kmh).font(.system(size: 10))
                }
                .foregroundColor(isSpeeding(s) ? Color.red : Color.white)
                .frame(width: 64, height: 64)
                .background(Color(white: 0.17)).clipShape(Circle())
            }
            if s.speedLimitKph > 0 {
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
            Text(strings.fasterRoute(savedMinutes: s.fasterRouteSavesMinutes)).font(.headline)
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
        .task(id: s.fasterRouteSavesMinutes) {
            // Closes by itself (keeping the route) after 20 seconds.
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            let saves = drive.snapshot?.fasterRouteSavesMinutes ?? 0
            if saves > 0 { drive.keepRoute() }
        }
    }

    func tripCard(_ s: DriveSnapshot) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(arrival(s), style: .time).font(.system(size: 26, weight: .semibold))
                Text(tripLine(s)).font(.subheadline).opacity(0.8)
            }
            Spacer()
            Button { drive.stop() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                    .frame(width: 52, height: 52).background(Color.red).clipShape(Circle())
            }
            .accessibilityLabel(strings.stopNavigation)
        }
        .foregroundColor(.white)
        .padding(16)
        .background(Color(white: 0.2))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 5)
    }

    /// Starts a live link and opens the share sheet with "I'm driving to …, arriving around …".
    func shareDrive(_ s: DriveSnapshot) {
        let nickname = model.profile?.nickname ?? ""
        let eta = arrival(s).formatted(date: .omitted, time: .shortened)
        let destination = drive.destinationName
        Task {
            do {
                let url = try await drive.startSharing(name: nickname.isEmpty ? nil : nickname)
                shareItem = ShareItem(items: [strings.shareText(destination: destination, eta: eta, url: url)])
            } catch {
                model.message = strings.shareFailed
            }
        }
    }

    func laneOpacity(_ lane: LaneArrow) -> Double {
        if lane.recommended { return 1 }
        return lane.usable ? 0.6 : 0.25
    }

    func isSpeeding(_ s: DriveSnapshot) -> Bool {
        let limit = Int(s.speedLimitKph)
        return limit > 0 && Int(s.speedKmh) > limit + 3
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
