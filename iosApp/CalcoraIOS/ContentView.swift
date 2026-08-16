import SwiftUI

struct ContentView: View {
    @StateObject private var store = CalcoraStore()
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                NavigationSplitView {
                    List(selection: Binding(get: { store.selectedTab }, set: { if let v = $0 { store.selectedTab = v } })) { ForEach(AppTab.allCases) { tab in Label(LocalizedStringKey(tab.title), systemImage: tab.symbol).tag(tab) } }
                    .navigationTitle("Calcora")
                    .listStyle(.sidebar)
                } detail: {
                    selectedView
                }
            } else {
                TabView(selection: $store.selectedTab) {
                    CalculatorView().tabItem { Label("Calculator", systemImage: "function") }.tag(AppTab.calculator)
                    HelpView().tabItem { Label("Help", systemImage: "book") }.tag(AppTab.help)
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
                case .settings: SettingsView()
        }
    }
}

