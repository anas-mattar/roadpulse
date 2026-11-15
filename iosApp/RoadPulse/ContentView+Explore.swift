import CoreLocation
import RoadPulseShared
import SwiftUI

/// The map screen when not driving: search card, saved places, planned drives, route choices.
extension ContentView {
    var exploreOverlay: some View {
        VStack(spacing: 10) {
            HStack {
                if model.hasServer {
                    circleButton("person.crop.circle", label: strings.profile) { showProfile = true }
                }
                Spacer()
                circleButton("gearshape.fill", label: strings.settings) { showSettings = true }
            }
            Spacer()
            HStack(alignment: .bottom) {
                if model.hasServer { reportButton }
                Spacer()
                circleButton("location.fill", label: strings.showMyLocation) { recenterToken += 1 }
            }
            if let pin = selectedReport { reportCard(pin) } else { card }
        }
    }

    // MARK: Reports

    var reportButton: some View {
        Button { showReport = true } label: {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                Text(strings.report).bold()
            }
            .font(.subheadline)
            .foregroundColor(Color(red: 0.39, green: 0.22, blue: 0.02))
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(Color(red: 0.98, green: 0.78, blue: 0.46))
            .clipShape(Capsule())
            .shadow(radius: 3)
        }
    }

    func reportCard(_ pin: ReportPin) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: reportSymbol(pin.typeName))
                    .foregroundColor(.white).frame(width: 44, height: 44)
                    .background(Color(hex: pin.colorHex)).clipShape(Circle())
                VStack(alignment: .leading) {
                    Text(pin.label).font(.headline)
                    if !pin.detail.isEmpty { Text(pin.detail).font(.footnote).foregroundColor(.secondary) }
                }
                Spacer()
                Button { selectedReport = nil } label: { Image(systemName: "xmark").foregroundColor(.secondary) }
                    .accessibilityLabel(strings.close)
            }
            if pin.own {
                Text(strings.yourReport).font(.footnote).foregroundColor(.secondary)
            } else {
                Text(strings.isItStillThere).font(.subheadline)
                HStack(spacing: 10) {
                    Button {
                        selectedReport = nil
                        Task { await model.vote(pin, stillThere: true, language: language, strings: strings) }
                    } label: {
                        Label(strings.stillThere, systemImage: "hand.thumbsup.fill")
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .background(indigo).foregroundColor(.white).clipShape(Capsule())
                    }
                    Button {
                        selectedReport = nil
                        Task { await model.vote(pin, stillThere: false, language: language, strings: strings) }
                    } label: {
                        Label(strings.notThere, systemImage: "hand.thumbsdown.fill")
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .overlay(Capsule().stroke(Color.secondary, lineWidth: 1)).foregroundColor(.primary)
                    }
                }
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
    }

    // MARK: Search card

    var showExplore: Bool {
        model.query.isEmpty && model.results.isEmpty && model.destination == nil && model.shortcutTarget == nil
    }

    var searchHint: String {
        switch model.shortcutTarget {
        case "home": return strings.yourHomeAddress
        case "work": return strings.yourWorkAddress
        default: return strings.whereTo
        }
    }

    /// The bottom card: search box, then saved places and categories, results or the route choices.
    var card: some View {
        VStack(alignment: .leading, spacing: 10) {
            searchBox
            if let error = model.errorText {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            if showExplore {
                explore
            } else if !model.results.isEmpty {
                resultsList
            } else if !model.routes.isEmpty {
                routePreview
            }
            Text("© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap")
                .font(.system(size: 9)).foregroundColor(.secondary)
        }
        .padding(14)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
    }

    var searchBox: some View {
        HStack {
            Image(systemName: model.shortcutTarget == nil ? "magnifyingglass" : "house.fill").foregroundColor(indigo)
            TextField(searchHint, text: $model.query)
                .focused($searching)
                .submitLabel(.search)
                .onSubmit { Task { await model.search(near: here) } }
            if model.busy { ProgressView() }
            if !showExplore {
                Button { model.clear() } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }
                    .accessibilityLabel(strings.clear)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: Saved places, categories, planned and recent

    var explore: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    shortcutChip(home: true, place: model.book.home)
                    shortcutChip(home: false, place: model.book.work)
                    ForEach(Array(model.book.favourites.enumerated()), id: \.offset) { _, place in
                        chip(place.name, icon: "star.fill") { go(place) }
                            .contextMenu {
                                Button(strings.remove(name: place.name), role: .destructive) {
                                    model.removeFavourite(place, strings: strings)
                                }
                            }
                    }
                }
            }
            if model.hasServer { categoryChips }
            plannedList
            recentList
        }
    }

    func shortcutChip(home: Bool, place: SearchResult?) -> some View {
        let label = home ? strings.home : strings.work
        return chip(place == nil ? "\(label) · \(strings.setAddress)" : label, icon: home ? "house.fill" : "briefcase.fill") {
            if let p = place { go(p) } else { askShortcut(home: home) }
        }
        .contextMenu {
            Button(strings.setAddress) { askShortcut(home: home) }
        }
    }

    func askShortcut(home: Bool) {
        model.clear()
        model.shortcutTarget = home ? "home" : "work"
        model.message = strings.setShortcutHint(home: home)
        searching = true
    }

    func go(_ place: SearchResult) {
        searching = false
        Task { await model.choose(place, from: here, language: language, strings: strings) }
    }

    var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(model.api.categories(language: language), id: \.self) { c in
                    Button {
                        let label = strings.categoryNearby(c: categoryFor(c[0]))
                        Task { await model.searchCategory(c[0], label: label, near: here, language: language) }
                    } label: {
                        Text(c[1]).font(.subheadline)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .overlay(Capsule().stroke(indigo.opacity(0.4), lineWidth: 1)).foregroundColor(indigo)
                    }
                }
            }
        }
    }

    @ViewBuilder var plannedList: some View {
        let planned = model.plannedDrives()
        if !planned.isEmpty {
            Text(strings.plannedDrives).font(.caption).bold().foregroundColor(.secondary)
            ForEach(Array(planned.prefix(3).enumerated()), id: \.offset) { _, item in
                Button { go(item.place) } label: {
                    row(icon: "alarm", title: item.place.name, detail: plannedLine(item))
                }
                .contextMenu {
                    Button(strings.remove(name: item.place.name), role: .destructive) { model.removePlanned(item) }
                }
            }
        }
    }

    func plannedLine(_ item: PlannedItem) -> String {
        let tomorrow = Calendar.current.isDateInTomorrow(date(item.arriveByMillis))
        return strings.plannedFor(time: clock(item.arriveByMillis), tomorrow: tomorrow) + " · "
            + strings.leaveAt(time: clock(item.leaveAtMillis), driveMinutes: item.driveMinutes, spareMinutes: model.book.spareMinutes)
    }

    @ViewBuilder var recentList: some View {
        let recent = model.book.recent
        ForEach(Array(recent.prefix(4).enumerated()), id: \.offset) { _, place in
            Button { go(place) } label: { row(icon: "clock.arrow.circlepath", title: place.name, detail: place.detail) }
        }
    }

    func row(icon: String, title: String, detail: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundColor(.secondary).frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundColor(.primary).lineLimit(1)
                if !detail.isEmpty { Text(detail).font(.footnote).foregroundColor(.secondary).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }

    // MARK: Results and routes

    var resultsList: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let title = model.resultsTitle { Text(title).font(.headline) }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(model.results.enumerated()), id: \.offset) { _, place in
                        Button { go(place) } label: { row(icon: "mappin.circle", title: place.name, detail: place.detail) }
                    }
                }
            }
            .frame(maxHeight: 220)
        }
    }

    var routePreview: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(model.routes.enumerated()), id: \.offset) { index, route in
                        routeCard(route, isSelected: index == model.selected)
                            .onTapGesture { model.selected = index }
                    }
                }
            }
            if let place = model.destination, model.selected < model.routes.count {
                HStack(spacing: 8) {
                    smallButton("star", label: strings.savePlace) {
                        favouriteName = place.name
                        askFavouriteName = true
                    }
                    smallButton("alarm", label: strings.leaveLater) { showLeaveLater = true }
                    // Try the drive at a desk: the car follows the route by itself.
                    smallButton("play.fill", label: strings.simulateDrive) {
                        drive.start(route: model.routes[model.selected], to: place, simulated: true)
                    }
                    Button { drive.start(route: model.routes[model.selected], to: place, simulated: false) } label: {
                        HStack {
                            Image(systemName: "location.north.fill")
                            Text(strings.goNow).font(.headline)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity).frame(height: 52)
                        .background(indigo).clipShape(Capsule())
                    }
                }
            }
        }
    }

    func smallButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).foregroundColor(.primary)
                .frame(width: 48, height: 48)
                .background(Color(.secondarySystemBackground)).clipShape(Circle())
        }
        .accessibilityLabel(label)
    }

    func categoryFor(_ name: String) -> PlaceCategory {
        switch name {
        case "PARKING": return .parking
        case "FOOD": return .food
        case "EV_CHARGING": return .evCharging
        case "REST_AREA": return .restArea
        default: return .fuel
        }
    }

    func routeCard(_ route: RouteSummary, isSelected: Bool) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(route.tag)
                .font(.caption2).bold()
                .padding(.horizontal, 7).padding(.vertical, 2)
                .background(isSelected ? indigo : Color(.systemBackground))
                .foregroundColor(isSelected ? .white : .secondary)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            Text(strings.duration(minutes: route.minutes)).font(.title2).bold()
            Text(strings.distance(km: route.km)).font(.footnote).foregroundColor(.secondary)
            if let via = route.via {
                Text(strings.via(road: via)).font(.caption).foregroundColor(.secondary).lineLimit(1)
            }
            if let toll = route.tollLabel {
                Text(toll).font(.caption).bold().foregroundColor(.orange).lineLimit(1)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .frame(width: 160, alignment: .leading)
        .background(isSelected ? indigo.opacity(0.12) : Color(.secondarySystemBackground))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(isSelected ? indigo : Color.clear, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    // MARK: Leave later

    var leaveLaterSheet: some View {
        LeaveLaterView(model: model, strings: strings, onDone: { showLeaveLater = false })
    }
}

/// "Leave later": choose when to arrive; the app works out when to leave and reminds you.
struct LeaveLaterView: View {
    @ObservedObject var model: SearchModel
    let strings: AppStrings
    let onDone: () -> Void
    @State private var arriveBy = Date().addingTimeInterval(3600)

    private var route: RouteSummary? {
        model.selected < model.routes.count ? model.routes[model.selected] : nil
    }

    var body: some View {
        NavigationStack {
            Form {
                if let place = model.destination, let route = route {
                    Section(place.name) {
                        DatePicker(strings.arriveBy, selection: $arriveBy, in: Date()..., displayedComponents: [.date, .hourAndMinute])
                        Text(leaveText(route)).font(.subheadline).foregroundColor(tooLate(route) ? .red : .secondary)
                    }
                    Button(strings.setReminder) {
                        if let item = model.book.addPlanned(place: place, route: route, arriveByMillis: millis(arriveBy),
                                                            nowMillis: millis(Date())) {
                            LeaveReminders.schedule(item, strings: strings)
                            model.message = strings.reminderSet(time: clock(item.remindAtMillis))
                            model.objectWillChange.send()
                            onDone()
                        }
                    }
                    .disabled(tooLate(route))
                }
            }
            .navigationTitle(strings.planDrive)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(strings.cancel, action: onDone) }
            }
        }
        .presentationDetents([.medium])
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }

    private func leaveAt(_ route: RouteSummary) -> Int64 {
        model.book.leaveAtMillis(route: route, arriveByMillis: millis(arriveBy))
    }

    private func tooLate(_ route: RouteSummary) -> Bool { leaveAt(route) <= millis(Date()) }

    private func leaveText(_ route: RouteSummary) -> String {
        if tooLate(route) { return strings.tooLate }
        let minutes = Int32((route.route.timeSeconds / 60).rounded())
        return strings.leaveAt(time: clock(leaveAt(route)), driveMinutes: minutes, spareMinutes: model.book.spareMinutes)
    }
}
