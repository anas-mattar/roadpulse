import CoreLocation
import RoadPulseShared
import SwiftUI

/// The map screen when not driving: search card, saved places, planned drives, route choices.
extension ContentView {
    var exploreOverlay: some View {
        VStack(spacing: 10) {
            HStack {
                circleButton("line.3.horizontal", label: strings.menu) { showSettings = true }
                Spacer()
                if model.destination != nil && model.routes.count > 1 {
                    MapListSwitch(list: $listMode, strings: strings, brand: brand)
                    Spacer()
                    Color.clear.frame(width: 44, height: 44)
                }
            }
            if listMode && model.destination != nil && model.routes.count > 1 {
                routeList
            }
            Spacer()
            HStack(alignment: .bottom) {
                if model.hasServer { reportButton }
                Spacer()
                circleButton("location.fill", label: strings.showMyLocation) { recenterToken += 1 }
            }
            if listMode && model.destination != nil && model.routes.count > 1 {
                EmptyView()
            } else if let pin = selectedReport {
                reportCard(pin)
            } else if let point = model.pinned {
                pinnedCard(point, driving: false)
            } else {
                card
            }
        }
    }

    // MARK: Long press

    /// Actions for a long-pressed point.
    func pinnedCard(_ point: SearchResult, driving: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(point.name).font(.headline)
                    if !point.detail.isEmpty { Text(point.detail).font(.footnote).foregroundColor(.secondary).lineLimit(2) }
                }
                Spacer()
                Button { model.pinned = nil } label: { Image(systemName: "xmark").foregroundColor(.secondary) }
                    .accessibilityLabel(strings.close)
            }
            HStack(spacing: 8) {
                if !driving {
                    pinAction(strings.driveHere, icon: "location.north.fill", filled: true) {
                        model.pinned = nil
                        model.clear()
                        go(point)
                    }
                }
                pinAction(strings.addStop, icon: "flag.fill", filled: driving) { addStop(point) }
            }
            if !driving {
                HStack(spacing: 8) {
                    pinAction(strings.parkedHere, icon: "car.fill", filled: false) {
                        model.pinned = nil
                        model.setParking(point, strings: strings)
                    }
                    pinAction(strings.savePlace, icon: "star", filled: false) {
                        favouritePlace = point
                        favouriteName = point.name
                        model.pinned = nil
                        askFavouriteName = true
                    }
                }
                if model.hasServer {
                    Button {
                        addPlacePoint = point
                        model.pinned = nil
                        showAddPlace = true
                    } label: {
                        Label(strings.addPlace, systemImage: "mappin.and.ellipse").font(.subheadline.bold())
                    }
                    .foregroundColor(brand)
                }
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
    }

    func pinAction(_ text: String, icon: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(text, systemImage: icon).font(.subheadline.bold()).lineLimit(1)
                .frame(maxWidth: .infinity).frame(height: 44)
                .background(filled ? brand : brand.opacity(0.1))
                .foregroundColor(filled ? .white : brand)
                .clipShape(Capsule())
        }
    }

    /// Adds a stop: to the drive in progress, or to the route being previewed.
    func addStop(_ point: SearchResult) {
        model.pinned = nil
        if drive.isDriving {
            model.message = drive.addStop(lat: point.lat, lon: point.lon) ? strings.stopAdded(name: point.name) : strings.waitingForLocation
        } else if model.destination != nil {
            Task { await model.addStop(point, from: here, language: language) }
        } else {
            model.message = strings.chooseDestinationFirst
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
                            .background(brand).foregroundColor(.white).clipShape(Capsule())
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
            Text("© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap · Places: Overture Maps Foundation")
                .font(.system(size: 9)).foregroundColor(.secondary)
        }
        .padding(14)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
    }

    var searchBox: some View {
        HStack {
            Image(systemName: model.shortcutTarget == nil ? "magnifyingglass" : "house.fill").foregroundColor(brand)
            TextField(searchHint, text: $model.query)
                .focused($searching)
                .submitLabel(.search)
                .onSubmit { Task { await model.search(near: here) } }
                // Results appear while typing (after a short pause), like on Android.
                .onChange(of: model.query) { _ in model.queryChanged(near: here) }
            if model.busy { ProgressView() }
            if model.query.isEmpty && model.shortcutTarget == nil {
                Button { listen() } label: {
                    Image(systemName: voice.listening ? "mic.fill" : "mic")
                        .foregroundColor(voice.listening ? .red : brand)
                }
                .accessibilityLabel(strings.searchByVoice)
            }
            if !showExplore {
                Button { model.clear() } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }
                    .accessibilityLabel(strings.clear)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    /// Search by voice: the words appear in the box, then the search runs.
    func listen() {
        if voice.listening {
            voice.stop()
            return
        }
        voice.start(localeTag: VoiceSearch.localeTag(for: languageCode)) { text, final in
            if !text.isEmpty { model.query = text }
            if final && !text.isEmpty { Task { await model.search(near: here) } }
        }
    }

    // MARK: Saved places, categories, planned and recent

    var explore: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if let parked = model.book.parking,
                       let label = model.book.parkingLabel(nowMillis: millis(Date()), language: language) {
                        chip(label, icon: "car.fill") {
                            go(SearchResult(name: strings.parkedHere, detail: "", lat: parked.lat, lon: parked.lon))
                        }
                        .contextMenu {
                            Button(strings.forgetParking, role: .destructive) { model.clearParking() }
                        }
                    }
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
                            .overlay(Capsule().stroke(brand.opacity(0.4), lineWidth: 1)).foregroundColor(brand)
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

    func row(icon: String, title: String, detail: String, distance: String? = nil) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).foregroundColor(.secondary).frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundColor(.primary).lineLimit(1)
                if !detail.isEmpty { Text(detail).font(.footnote).foregroundColor(.secondary).lineLimit(1) }
            }
            Spacer(minLength: 0)
            if let distance = distance {
                Text(distance).font(.subheadline).foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    /// "6 km" from here to a search result (category lists already show it in the detail).
    func distanceText(to place: SearchResult) -> String? {
        guard model.resultsTitle == nil else { return nil }
        let from = CLLocation(latitude: here.latitude, longitude: here.longitude)
        let meters = from.distance(from: CLLocation(latitude: place.lat, longitude: place.lon))
        return strings.distance(km: meters / 1000)
    }

    // MARK: Results and routes

    var resultsList: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let title = model.resultsTitle { Text(title).font(.headline) }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(model.results.enumerated()), id: \.offset) { _, place in
                        Button { go(place) } label: {
                            row(icon: "mappin.circle", title: place.name, detail: place.detail, distance: distanceText(to: place))
                        }
                    }
                }
            }
            .frame(maxHeight: 220)
        }
    }

    var routePreview: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let summary = Choices.shared.summary(options: model.routeOptions, language: language) {
                Label(summary, systemImage: "slider.horizontal.3").font(.caption).foregroundColor(.secondary)
            }
            stopsList
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
                        favouritePlace = place
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
                        .background(brand).clipShape(Capsule())
                    }
                }
            }
        }
    }

    /// Stops on the way, each removable; a tip about adding them when there are none.
    @ViewBuilder var stopsList: some View {
        if model.stops.isEmpty {
            Text(strings.tipAddStop).font(.caption).foregroundColor(.secondary)
            // Only for places from the server's own data, which a moderator can correct.
            if model.hasServer, let place = model.destination, !place.directoryId.isEmpty {
                Button(strings.reportPlaceProblem) { showPlaceProblem = true }
                    .font(.caption)
                    .foregroundColor(brand)
            }
        } else {
            ForEach(Array(model.stops.enumerated()), id: \.offset) { index, stop in
                HStack {
                    Image(systemName: "flag.fill").foregroundColor(.orange)
                    Text(strings.stop(number: Int32(index + 1), name: stop.name)).font(.subheadline).lineLimit(1)
                    Spacer()
                    Button {
                        Task { await model.removeStop(at: index, from: here, language: language) }
                    } label: { Image(systemName: "minus.circle.fill").foregroundColor(.secondary) }
                        .accessibilityLabel(strings.removeStop)
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
                .background(isSelected ? brand : Color(.systemBackground))
                .foregroundColor(isSelected ? .white : .secondary)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            Text(strings.duration(minutes: route.minutesWithTraffic)).font(.title2).bold()
            Text(strings.distance(km: route.km)).font(.footnote).foregroundColor(.secondary)
            if let via = route.via {
                Text(strings.via(road: via)).font(.caption).foregroundColor(.secondary).lineLimit(1)
            }
            if let toll = route.tollLabel {
                Text(toll).font(.caption).bold().foregroundColor(.orange).lineLimit(1)
            }
            TrafficBarView(pieces: route.trafficPieces, track: Color(.systemBackground)).padding(.top, 4)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .frame(width: 160, alignment: .leading)
        .background(isSelected ? brand.opacity(0.12) : Color(.secondarySystemBackground))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(isSelected ? brand : Color.clear, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    /// Route choices as large cards with arrival time, traffic bar, toll and Go.
    var routeList: some View {
        ScrollView {
            VStack(spacing: 10) {
                ForEach(Array(model.routes.enumerated()), id: \.offset) { index, route in
                    RouteListCard(route: route, selected: index == model.selected, strings: strings, brand: brand,
                                  onSelect: { model.selected = index },
                                  onGo: {
                                      if let place = model.destination {
                                          model.selected = index
                                          drive.start(route: route, to: place, simulated: false)
                                      }
                                  })
                }
            }
            .padding(.vertical, 4)
        }
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
