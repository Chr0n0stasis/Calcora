import SwiftUI

struct ContentView: View {
    @StateObject private var store = CalcoraStore()
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                NavigationSplitView {
                    List(AppTab.allCases, selection: $store.selectedTab) { tab in
                        Label(tab.title, systemImage: tab.symbol).tag(tab)
                    }
                    .navigationTitle("Calcora")
                    .listStyle(.sidebar)
                } detail: {
                    selectedView
                }
            } else {
                TabView(selection: $store.selectedTab) {
                    CalculatorView().tabItem { Label("Calculator", systemImage: "function") }.tag(AppTab.calculator)
                    HelpView().tabItem { Label("Help", systemImage: "book") }.tag(AppTab.help)
                    HistoryView().tabItem { Label("History", systemImage: "clock.arrow.circlepath") }.tag(AppTab.history)
                    SettingsView().tabItem { Label("Settings", systemImage: "gearshape") }.tag(AppTab.settings)
                }
            }
        }
        .environmentObject(store)
        .preferredColorScheme(store.settings.themeMode.colorScheme)
    }

    @ViewBuilder private var selectedView: some View {
        switch store.selectedTab {
        case .calculator: CalculatorView()
        case .help: HelpView()
        case .history: HistoryView()
        case .settings: SettingsView()
        }
    }
}
