import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: CalcoraStore

    var body: some View {
        NavigationStack {
            Form {
                Section(LocalizedStringKey("Appearance")) {
                    Picker("Theme", selection: $store.settings.themeMode) { ForEach(ThemeMode.allCases) { Text($0.rawValue).tag($0) } }
                    Picker("Language", selection: Binding(get: { store.settings.language }, set: store.setLanguage)) { ForEach(AppLanguage.allCases) { Text($0.rawValue).tag($0) } }
                }
                Section(LocalizedStringKey("Calculation")) {
                    Picker(LocalizedStringKey("Default mode"), selection: Binding(get: { store.settings.defaultEvalMode }, set: { value in store.settings.defaultEvalMode = value; store.selectedMode = value })) { ForEach(EvalMode.allCases) { Text($0.rawValue).tag($0) } }
                    Picker("Angle unit", selection: $store.settings.angleUnit) { ForEach(AngleUnit.allCases) { Text($0.rawValue).tag($0) } }
                    Stepper(String(format: NSLocalizedString("Precision: %d", comment: ""), store.settings.precision), value: $store.settings.precision, in: 4...20)
                    Stepper(String(format: NSLocalizedString("History limit: %d", comment: ""), store.settings.historyLimit), value: $store.settings.historyLimit, in: 20...200, step: 4)
                }
                Section(LocalizedStringKey("Editor")) {
                    Toggle("Autocomplete", isOn: $store.settings.autocompleteEnabled)
                    Toggle("Syntax highlighting", isOn: $store.settings.syntaxHighlighting)
                    Text(LocalizedStringKey("Syntax highlighting is applied to native text fields and may be disabled for accessibility or performance."))
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section(LocalizedStringKey("Actions")) {
                    Button { store.resetSession() } label: { Label(LocalizedStringKey("Reset Giac session"), systemImage: "arrow.counterclockwise") }
                    Button { store.checkForUpdate() } label: { Label(LocalizedStringKey("Check for updates"), systemImage: "arrow.triangle.2.circlepath") }
                    if !store.updateMessage.isEmpty { Text(store.updateMessage).font(.caption).foregroundStyle(.secondary).textSelection(.enabled) }
                }
                Section(LocalizedStringKey("About")) {
                    LabeledContent(LocalizedStringKey("Native engine"), value: CalcoraEngine.shared.version())
                    LabeledContent(LocalizedStringKey("Interface"), value: "SwiftUI · iOS/iPadOS")
                    Text(LocalizedStringKey("Calcora uses the native Giac engine and is designed for touch, keyboard, Dynamic Type, and iPad multitasking."))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(LocalizedStringKey("Settings"))
        }
    }
}
